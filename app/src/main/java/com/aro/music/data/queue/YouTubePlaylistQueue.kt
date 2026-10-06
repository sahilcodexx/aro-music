package com.aro.music.data.queue

import androidx.media3.common.MediaItem
import com.aro.music.data.innertube.InnerTubeRepository
import com.aro.music.data.model.Song
import com.aro.music.utils.MediaItemBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Queue for YouTube Music playlists.
 * Adapted from Meld's YouTubePlaylistQueue to work with aro-music's existing API.
 *
 * Note: aro-music's InnerTubeRepository doesn't support playlist continuation pagination
 * yet, so this queue returns the full playlist at once. Future enhancement can add
 * continuation support to enable true pagination.
 *
 * @param repository InnerTubeRepository for fetching playlist data
 * @param playlistId YouTube Music playlist browse ID (VLPL...)
 * @param playlistTitle Optional playlist title for display
 * @param initialSongs Pre-loaded songs (if available)
 * @param startIndex Index to start playback from
 * @param preloadItem Item to hand to player immediately before queue loads
 */
class YouTubePlaylistQueue(
    private val repository: InnerTubeRepository,
    private val playlistId: String,
    private val playlistTitle: String? = null,
    private val initialSongs: List<Song> = emptyList(),
    private val startIndex: Int = 0,
    override val preloadItem: MediaItem? = null,
) : QueueProvider {

    private var loaded = false

    override suspend fun getInitialStatus(): QueueProvider.Status = withContext(Dispatchers.IO) {
        if (initialSongs.isNotEmpty()) {
            loaded = true
            QueueProvider.Status(
                title = playlistTitle,
                items = initialSongs.map { MediaItemBuilder.build(it) },
                mediaItemIndex = startIndex,
            )
        } else {
            val songs = repository.getPlaylistTracks(playlistId, limit = 500)
            loaded = true
            QueueProvider.Status(
                title = playlistTitle,
                items = songs.map { MediaItemBuilder.build(it) },
                mediaItemIndex = startIndex,
            )
        }
    }

    // No pagination support yet - returns empty list
    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<MediaItem> = emptyList()
}
