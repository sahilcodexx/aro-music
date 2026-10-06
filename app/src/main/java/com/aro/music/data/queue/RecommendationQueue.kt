package com.aro.music.data.queue

import androidx.media3.common.MediaItem
import com.aro.music.data.DailyMixManager
import com.aro.music.data.model.Song
import com.aro.music.utils.MediaItemBuilder
import timber.log.Timber

/**
 * Queue that generates personalized recommendations based on user's listening history.
 * Uses aro-music's existing DailyMixManager which already has sophisticated recommendation logic.
 *
 * Inspired by Meld's SpotifyRecommendationEngine but adapted for local library.
 * The DailyMixManager includes:
 * - Artist affinity scoring
 * - Genre affinity scoring
 * - Recency scoring
 * - Novelty scoring
 * - Diversity constraints (max tracks per artist)
 *
 * @param seedSong The song the user tapped (leads the queue)
 * @param allSongs All available songs in the library
 * @param favoriteSongIds Set of favorite song IDs
 * @param dailyMixManager The DailyMixManager for recommendation generation
 * @param preloadItem Item to hand to player immediately
 */
class RecommendationQueue(
    private val seedSong: Song,
    private val allSongs: List<Song>,
    private val favoriteSongIds: Set<String>,
    private val dailyMixManager: DailyMixManager,
    override val preloadItem: MediaItem? = null,
) : QueueProvider {

    companion object {
        private const val TAG = "RecommendationQueue"
        private const val RECOMMENDATION_LIMIT = 50

        /**
         * Creates a recommendation queue with the seed song preloaded.
         */
        fun recommendation(
            seedSong: Song,
            allSongs: List<Song>,
            favoriteSongIds: Set<String>,
            dailyMixManager: DailyMixManager,
        ): RecommendationQueue = RecommendationQueue(
            seedSong = seedSong,
            allSongs = allSongs,
            favoriteSongIds = favoriteSongIds,
            dailyMixManager = dailyMixManager,
            preloadItem = MediaItemBuilder.build(seedSong),
        )
    }

    private var recommendationsGenerated = false
    private var recommendedSongs: List<Song> = emptyList()

    override suspend fun getInitialStatus(): QueueProvider.Status {
        // Return seed song immediately for instant playback
        Timber.d("$TAG: Returning seed song immediately, recommendations deferred")
        return QueueProvider.Status(
            title = "Based on ${seedSong.title}",
            items = listOf(MediaItemBuilder.build(seedSong)),
            mediaItemIndex = 0,
        )
    }

    override fun hasNextPage(): Boolean = !recommendationsGenerated || recommendedSongs.isNotEmpty()

    override suspend fun nextPage(): List<MediaItem> {
        if (recommendationsGenerated) {
            return emptyList()
        }

        recommendationsGenerated = true

        try {
            // Generate recommendations using DailyMixManager
            // This uses engagement data, artist affinity, genre affinity, etc.
            val recommendations = dailyMixManager.generateYourMix(
                allSongs = allSongs,
                favoriteSongIds = favoriteSongIds,
                limit = RECOMMENDATION_LIMIT
            )

            // Filter out the seed song from recommendations
            recommendedSongs = recommendations.filter { it.id != seedSong.id }

            Timber.d("$TAG: Generated ${recommendedSongs.size} recommendations for '${seedSong.title}'")

            return recommendedSongs.map { MediaItemBuilder.build(it) }
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "$TAG: Failed to generate recommendations")
            return emptyList()
        }
    }
}
