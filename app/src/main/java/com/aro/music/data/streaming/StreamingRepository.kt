package com.aro.music.data.streaming

import com.aro.music.data.innertube.InnerTubeRepository
import com.aro.music.data.itunes.ItunesRepository
import com.aro.music.data.jiosaavn.JioSaavnRepository
import com.aro.music.data.model.Album
import com.aro.music.data.model.Artist
import com.aro.music.data.model.Song
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fans a catalog request out to the streaming backends and returns the first
 * usable answer.
 *
 * InnerTube (YouTube Music) is primary for every entity type because it is the
 * only backend that returns real album, artist and playlist relationships —
 * JioSaavn flattens everything to a title/artist string, and iTunes only ever
 * returns 30-second previews. JioSaavn and iTunes are kept as fallbacks so that
 * a blocked, rate-limited or empty InnerTube response degrades to the previous
 * behaviour instead of to an empty screen.
 */
@Singleton
class StreamingRepository @Inject constructor(
    private val innerTube: InnerTubeRepository,
    private val jioSaavn: JioSaavnRepository,
    private val iTunes: ItunesRepository,
) {
    companion object {
        private const val TAG = "StreamingRepo"
    }

    /**
     * Search songs. InnerTube first — JioSaavn, then iTunes, only if InnerTube
     * returns nothing.
     */
    suspend fun searchSongs(query: String, limit: Int = 20): List<Song> {
        val innerTubeResults = try {
            innerTube.searchSongs(query = query, limit = limit)
        } catch (e: Exception) {
            Timber.w(e, "$TAG: InnerTube searchSongs failed")
            emptyList()
        }
        if (innerTubeResults.isNotEmpty()) {
            Timber.d("$TAG: InnerTube → ${innerTubeResults.size} songs for '$query'")
            return innerTubeResults
        }
        Timber.d("$TAG: InnerTube 0 results — falling back for '$query'")

        val jioResults = try {
            jioSaavn.searchSongs(query = query, limit = limit)
        } catch (e: Exception) {
            Timber.w(e, "$TAG: JioSaavn searchSongs failed")
            emptyList()
        }
        if (jioResults.isNotEmpty()) return jioResults

        return try {
            iTunes.searchSongs(query = query, limit = limit)
        } catch (e: Exception) {
            Timber.w(e, "$TAG: iTunes searchSongs also failed")
            emptyList()
        }
    }

    /** Search albums — InnerTube first, JioSaavn as fallback. */
    suspend fun searchAlbums(query: String): List<Album> {
        val innerTubeResults = try {
            innerTube.searchAlbums(query = query)
        } catch (e: Exception) {
            Timber.w(e, "$TAG: InnerTube searchAlbums failed")
            emptyList()
        }
        if (innerTubeResults.isNotEmpty()) {
            Timber.d("$TAG: InnerTube → ${innerTubeResults.size} albums for '$query'")
            return innerTubeResults
        }
        return try {
            jioSaavn.searchAlbums(query = query)
        } catch (e: Exception) {
            Timber.w(e, "$TAG: searchAlbums failed")
            emptyList()
        }
    }

    /** Search artists — InnerTube first, JioSaavn as fallback. */
    suspend fun searchArtists(query: String): List<Artist> {
        val innerTubeResults = try {
            innerTube.searchArtists(query = query)
        } catch (e: Exception) {
            Timber.w(e, "$TAG: InnerTube searchArtists failed")
            emptyList()
        }
        if (innerTubeResults.isNotEmpty()) {
            Timber.d("$TAG: InnerTube → ${innerTubeResults.size} artists for '$query'")
            return innerTubeResults
        }
        return try {
            jioSaavn.searchArtists(query = query)
        } catch (e: Exception) {
            Timber.w(e, "$TAG: searchArtists failed")
            emptyList()
        }
    }

    /**
     * Fetch the songs for a genre tag.
     *
     * Left on JioSaavn: InnerTube has no genre facet, so its search would be a
     * keyword search dressed up as a genre browse and would return loosely
     * related tracks instead.
     */
    suspend fun searchSongsForGenre(genreTag: String, limit: Int = 50): List<Song> = try {
        jioSaavn.searchSongsForGenre(genreTag = genreTag, limit = limit)
    } catch (e: Exception) {
        Timber.w(e, "$TAG: searchSongsForGenre failed for '$genreTag'")
        emptyList()
    }

    /**
     * Real "similar songs" / autoplay feed for a track, from YouTube Music's own
     * radio. Returns an empty list when the seed has no radio so callers can
     * fall back to their local heuristics.
     */
    suspend fun getRadioFor(videoId: String, limit: Int = 50): List<Song> = try {
        innerTube.getRadioFor(videoId = videoId, limit = limit)
    } catch (e: Exception) {
        Timber.w(e, "$TAG: getRadioFor failed for $videoId")
        emptyList()
    }

    /**
     * Fetch a starter set of songs for the library.
     *
     * InnerTube's charts browse is the closest thing to a real trending feed,
     * which is what replaces the previous "rotate through 15 hard-coded keyword
     * searches" behaviour. JioSaavn + iTunes remain as the fallback.
     */
    suspend fun getTrendingSongs(limit: Int = 30): List<Song> = coroutineScope {
        val innerTubeDeferred = async {
            try {
                innerTube.getTrendingSongs(limit = limit)
            } catch (e: Exception) {
                Timber.w(e, "$TAG: InnerTube trending failed")
                emptyList()
            }
        }
        val jioDeferred = async {
            try {
                jioSaavn.getTrendingSongs(limit = limit)
            } catch (e: Exception) {
                Timber.w(e, "$TAG: JioSaavn trending failed")
                emptyList()
            }
        }
        val itunesDeferred = async {
            try {
                iTunes.searchSongs(query = "top hits 2024", limit = limit / 2)
            } catch (e: Exception) {
                Timber.w(e, "$TAG: iTunes trending failed")
                emptyList()
            }
        }

        val innerTubeSongs = innerTubeDeferred.await()
        if (innerTubeSongs.isNotEmpty()) {
            Timber.d("$TAG: getTrendingSongs → ${innerTubeSongs.size} songs via InnerTube charts")
            return@coroutineScope innerTubeSongs
        }

        val jioSongs = jioDeferred.await()
        val itunesSongs = itunesDeferred.await()
        val merged = if (jioSongs.isNotEmpty()) {
            val jioIds = jioSongs.map { it.id }.toSet()
            val extra = itunesSongs.filter { it.id !in jioIds }
            (jioSongs + extra).take(limit)
        } else {
            Timber.w("$TAG: InnerTube and JioSaavn both empty — using iTunes fallback")
            itunesSongs.take(limit)
        }
        Timber.d("$TAG: getTrendingSongs → ${merged.size} songs (fallback path)")
        merged
    }
}