package com.aro.music.spotify

import android.util.LruCache
import androidx.media3.common.MediaItem
import com.aro.music.data.database.AroDatabase
import com.aro.music.data.database.SpotifyMatchEntity
import com.aro.music.data.innertube.InnerTubeIds
import com.aro.music.data.innertube.InnerTubeRepository
import com.aro.music.data.model.Song
import com.aro.music.spotify.models.SpotifyTrack
import com.aro.music.utils.MediaItemBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Meld's fuzzy matching, adapted to Aro's numeric song IDs and lazy stream resolution. */
@Singleton
class SpotifyYouTubeMapper @Inject constructor(
    private val database: AroDatabase,
    private val youtube: InnerTubeRepository,
    private val registry: com.aro.music.data.innertube.InnerTubeBrowseRegistry,
) {
    private val memoryCache = LruCache<String, Song>(500)
    private val locks = Array(32) { Mutex() }

    suspend fun mapToYouTube(track: SpotifyTrack): Song? = withContext(Dispatchers.IO) {
        locks[(track.id.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            memoryCache[track.id]?.let { return@withLock it }
            val dao = database.spotifyMatchDao()
            dao.getSpotifyMatch(track.id)?.let { cached ->
                return@withLock remember(track, buildSong(cached.youtubeId, track))
            }
            val precomputed = SpotifyMapper.precompute(track.name, track.artists.firstOrNull()?.name.orEmpty(), track.durationMs)
            val candidates = youtube.searchSongs(SpotifyMapper.buildSearchQuery(track), 20)
            val scored = candidates.map { song ->
                song to SpotifyMapper.matchScorePrecomputed(precomputed, song.title, song.displayArtist,
                    (song.duration / 1000).toInt().takeIf { it > 0 })
            }
            val best = scored.maxByOrNull { it.second - SpotifyMatchRanking.variantPenalty(track.name, it.first.title) }?.takeIf { it.second >= 0.35 } ?: return@withLock null
            val videoId = best.first.contentUriString.removePrefix("innertube://")
            dao.upsertSpotifyMatch(SpotifyMatchEntity(track.id, videoId, best.first.title, best.first.artist, best.second))
            // Re-read so a concurrent manual override always wins.
            val persisted = dao.getSpotifyMatch(track.id) ?: return@withLock null
            remember(track, buildSong(persisted.youtubeId, track))
        }
    }

    private fun remember(track: SpotifyTrack, song: Song): Song {
        memoryCache.put(track.id, song)
        SpotifyMetadataRegistry.put(song.id, track)
        return song
    }

    private fun buildSong(videoId: String, track: SpotifyTrack): Song {
        val albumId = track.album?.id?.takeIf { it.isNotBlank() }?.let {
            InnerTubeIds.albumId("spotify:$it").also { id -> registry.putAlbum(id, "spotify:$it") }
        } ?: -1L
        val artists = track.artists.mapIndexed { index, artist ->
            val key = artist.id?.takeIf { it.isNotBlank() } ?: artist.name
            val id = InnerTubeIds.artistId("spotify:$key")
            if (!artist.id.isNullOrBlank()) registry.putArtist(id, "spotify:${artist.id}")
            com.aro.music.data.model.ArtistRef(id, artist.name, index == 0)
        }
        return Song.emptySong().copy(
            id = InnerTubeIds.songId(videoId).toString(), title = track.name,
            artist = track.artists.firstOrNull()?.name.orEmpty(), artists = artists,
            artistId = artists.firstOrNull()?.id ?: -1L, albumId = albumId,
            album = track.album?.name.orEmpty(), duration = track.durationMs.toLong(),
            trackNumber = track.trackNumber ?: 0,
            albumArtUriString = SpotifyMapper.getTrackThumbnail(track),
            path = "", contentUriString = "innertube://$videoId", mimeType = "audio/mp4",
        )
    }

    suspend fun resolveToMediaItem(track: SpotifyTrack): MediaItem? = mapToYouTube(track)?.let(MediaItemBuilder::build)

    suspend fun overrideMatch(spotifyId: String, youtubeId: String, title: String, artist: String) {
        locks[(spotifyId.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            database.spotifyMatchDao().upsertSpotifyMatch(SpotifyMatchEntity(spotifyId, youtubeId, title, artist, 1.0, isManualOverride = true))
            memoryCache.remove(spotifyId)
        }
    }
}

object SpotifyMetadataRegistry {
    private val tracks = LruCache<String, SpotifyTrack>(1000)
    fun put(songId: String, track: SpotifyTrack) { tracks.put(songId, track) }
    fun get(songId: String): SpotifyTrack? = tracks[songId]
    fun clear() { tracks.evictAll() }
}
