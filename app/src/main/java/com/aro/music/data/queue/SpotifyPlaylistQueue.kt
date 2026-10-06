/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aro.music.data.queue

import androidx.media3.common.MediaItem

import com.aro.music.spotify.SpotifyYouTubeMapper
import com.aro.music.spotify.Spotify
import com.aro.music.spotify.models.SpotifyTrack

/**
 * Queue implementation that loads tracks from a Spotify playlist. All the
 * pagination / fast-start / resolution logic lives in [SpotifyPagedQueue];
 * this class only supplies the playlist fetch and the optional pre-provided list.
 */
class SpotifyPlaylistQueue(
    private val playlistId: String,
    private val initialTracks: List<SpotifyTrack> = emptyList(),
    startIndex: Int = 0,
    mapper: SpotifyYouTubeMapper,
    preloadItem: MediaItem? = null,
) : SpotifyPagedQueue(startIndex, mapper, preloadItem) {

    override val logTag: String = "SpotifyPlaylistQueue"

    override val providedTracks: List<SpotifyTrack>? = initialTracks.takeIf { it.isNotEmpty() }

    override suspend fun fetchPage(offset: Int, limit: Int): PageResult {
        val result = Spotify.playlistTracks(playlistId, limit = limit, offset = offset).getOrThrow()
        return PageResult(
            tracks = result.items.mapNotNull { it.track?.takeIf { t -> !t.isLocal } },
            total = result.total,
            rawCount = result.items.size,
        )
    }
}
