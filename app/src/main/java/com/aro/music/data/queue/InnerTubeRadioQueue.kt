package com.aro.music.data.queue

import androidx.media3.common.MediaItem
import com.aro.music.data.innertube.InnerTubeRepository
import com.aro.music.data.model.Song
import com.aro.music.utils.MediaItemBuilder
import timber.log.Timber

/**
 * An endless "related songs" queue seeded by one track.
 *
 * Ported from Meld's `YouTubeQueue` with enhanced retry logic.
 * The important detail is on the repository side: it requests `RDAMVM<videoId>`,
 * which is what asks YouTube for a *radio mix* of that seed, and every continuation
 * returns more of the same. That is what turns a finished playlist into a queue
 * that never runs out, instead of stopping dead at the end of the search results.
 *
 * Enhanced with retry logic from Meld's YouTubeQueue to handle transient failures.
 *
 * @param seedSong the track the user tapped; always leads the queue
 * @param preloadItem handed to the player before the radio loads, so playback
 *   starts immediately rather than after a network round trip
 */
class InnerTubeRadioQueue(
    private val repository: InnerTubeRepository,
    private val seedSong: Song,
    private val seedVideoId: String,
    override val preloadItem: MediaItem?,
    private val title: String? = null,
) : QueueProvider {

    private var continuation: String? = null
    private var exhausted = false
    private var retryCount = 0
    private val maxRetries = 3

    override suspend fun getInitialStatus(): QueueProvider.Status {
        var lastException: Throwable? = null

        for (attempt in 0..maxRetries) {
            try {
                val page = repository.radioPage(seedVideoId = seedVideoId)
                continuation = page.continuation
                exhausted = page.continuation == null
                retryCount = 0

                // The user's own pick leads the queue; the radio fills in behind it.
                val radioItems = page.songs
                    .filter { it.id != seedSong.id }
                    .map { MediaItemBuilder.build(it) }

                val items = buildList {
                    add(MediaItemBuilder.build(seedSong))
                    addAll(radioItems)
                }
                return QueueProvider.Status(title = title, items = items, mediaItemIndex = 0)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastException = e
                retryCount++
                Timber.w(e, "InnerTubeRadioQueue: getInitialStatus attempt $attempt failed")
                if (retryCount >= maxRetries) {
                    Timber.e("InnerTubeRadioQueue: Max retries reached for initial load")
                    exhausted = true
                    break
                }
            }
        }

        // Return seed song only if all retries fail
        Timber.e("InnerTubeRadioQueue: Failed to load radio, returning seed song only")
        return QueueProvider.Status(
            title = title,
            items = listOf(MediaItemBuilder.build(seedSong)),
            mediaItemIndex = 0
        )
    }

    /**
     * A radio keeps producing, so this stays true until a continuation comes back
     * empty — there is no fixed end to page through.
     */
    override fun hasNextPage(): Boolean = !exhausted && continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        val token = continuation
        if (token == null || exhausted) return emptyList()

        var lastException: Throwable? = null

        for (attempt in 0..maxRetries) {
            try {
                val page = repository.radioPage(seedVideoId = seedVideoId, continuation = token)
                if (page.songs.isEmpty()) {
                    exhausted = true
                    continuation = null
                    return emptyList()
                }
                continuation = page.continuation
                if (page.continuation == null) exhausted = true
                retryCount = 0
                return page.songs.map { MediaItemBuilder.build(it) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastException = e
                retryCount++
                Timber.w(e, "InnerTubeRadioQueue: nextPage attempt $attempt failed")
                if (retryCount >= maxRetries) {
                    Timber.e("InnerTubeRadioQueue: Max retries reached for next page")
                    continuation = null
                    exhausted = true
                    break
                }
            }
        }

        Timber.e("InnerTubeRadioQueue: Failed to load next page")
        return emptyList()
    }

    companion object {
        /** Radio seeded by [song], with it preloaded for instant playback. */
        fun radio(
            repository: InnerTubeRepository,
            song: Song,
            videoId: String,
            title: String? = null,
        ): InnerTubeRadioQueue = InnerTubeRadioQueue(
            repository = repository,
            seedSong = song,
            seedVideoId = videoId,
            preloadItem = MediaItemBuilder.build(song),
            title = title,
        )
    }
}
