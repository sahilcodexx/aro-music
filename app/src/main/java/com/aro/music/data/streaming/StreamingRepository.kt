package com.aro.music.data.streaming

import com.aro.music.data.innertube.InnerTubeRepository
import com.aro.music.data.model.Album
import com.aro.music.data.model.Artist
import com.aro.music.data.model.Song
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catalog access, backed solely by InnerTube (YouTube Music).
 *
 * The JioSaavn and iTunes backends were removed deliberately. They were kept
 * first as a fallback and that turned out to be actively harmful: when InnerTube
 * failed, they silently answered instead, so the app looked healthy while
 * serving a completely different catalogue. JioSaavn only has Indian film and
 * karaoke recordings, so a query for, say, a rap track returned loosely-related
 * film songs with the real track missing entirely — which reads as "search is
 * broken" rather than "search is unavailable".
 *
 * Failing visibly is better than answering from the wrong catalogue. If InnerTube
 * is unreachable the caller gets an empty list and the UI shows its empty state.
 */
@Singleton
class StreamingRepository @Inject constructor(
    private val innerTube: InnerTubeRepository,
    private val spotify: com.aro.music.spotify.SpotifyRepository,
) {
    suspend fun spotifyAlbum(browseId: String) = spotify.album(browseId)
    suspend fun spotifyAlbumSongs(browseId: String) = spotify.albumSongs(browseId)
    suspend fun spotifyArtistSongs(browseId: String) = spotify.artistSongs(browseId)
    suspend fun spotifyArtist(browseId: String) = spotify.artist(browseId)
    suspend fun spotifySearchPlaylists(query: String) = spotify.searchPlaylists(query)
    suspend fun spotifyPlaylist(id: String) = spotify.resolvePlaylist(id)

    companion object {
        private const val TAG = "StreamingRepo"
    }

    suspend fun searchSongs(query: String, limit: Int = 20): List<Song> {
        if (query.isBlank()) return emptyList()
        return try {
            val songs = spotify.searchSongs(query, limit).ifEmpty { innerTube.searchSongs(query = query, limit = limit) }
            Timber.d("$TAG: InnerTube → ${songs.size} songs for '$query'")
            songs
        } catch (e: Exception) {
            Timber.w(e, "$TAG: InnerTube searchSongs failed for '$query'")
            emptyList()
        }
    }

    suspend fun searchAlbums(query: String): List<Album> {
        if (query.isBlank()) return emptyList()
        return try {
            val albums = spotify.searchAlbums(query).ifEmpty { innerTube.searchAlbums(query = query) }
            Timber.d("$TAG: InnerTube → ${albums.size} albums for '$query'")
            albums
        } catch (e: Exception) {
            Timber.w(e, "$TAG: InnerTube searchAlbums failed for '$query'")
            emptyList()
        }
    }

    suspend fun searchArtists(query: String): List<Artist> {
        if (query.isBlank()) return emptyList()
        return try {
            val artists = spotify.searchArtists(query).ifEmpty { innerTube.searchArtists(query = query) }
            Timber.d("$TAG: InnerTube → ${artists.size} artists for '$query'")
            artists
        } catch (e: Exception) {
            Timber.w(e, "$TAG: InnerTube searchArtists failed for '$query'")
            emptyList()
        }
    }

    /**
     * Songs for a genre tag.
     *
     * Empty by design: InnerTube has no genre facet, and approximating one with
     * a keyword search returned loosely related tracks rather than the genre.
     */
    suspend fun searchSongsForGenre(genreTag: String, limit: Int = 50): List<Song> {
        Timber.d("$TAG: genre browsing unavailable without the removed keyword backends")
        return emptyList()
    }

    /** YouTube Music's own related-tracks feed for a track. */
    suspend fun getRadioFor(videoId: String, limit: Int = 50): List<Song> = try {
        innerTube.getRadioFor(videoId = videoId, limit = limit)
    } catch (e: Exception) {
        Timber.w(e, "$TAG: getRadioFor failed for $videoId")
        emptyList()
    }

    /**
     * Live query suggestions for the search field.
     *
     * Meld wires `music/get_search_suggestions` straight into its search box;
     * this is the same feed, failing soft to empty so the dropdown just shows
     * history when YouTube does not answer.
     */
    suspend fun getSuggestions(query: String): Pair<List<String>, List<Song>> =
        try {
            val result = innerTube.getSuggestions(query)
            result.queries to result.songs
        } catch (e: Exception) {
            Timber.w(e, "$TAG: getSuggestions failed for '$query'")
            emptyList<String>() to emptyList()
        }

    /**
     * Lyrics straight from YouTube Music for an InnerTube track.
     *
     * Returns null when the track has no lyrics page and no captions, which is
     * the caller's signal to keep using the LRCLIB pipeline.
     */
    suspend fun getYouTubeLyrics(videoId: String): String? = try {
        innerTube.getYouTubeLyrics(videoId)
    } catch (e: Exception) {
        Timber.w(e, "$TAG: getYouTubeLyrics failed for $videoId")
        null
    }

    /** Starter songs for the library, from the YouTube Music charts feed. */
    suspend fun getTrendingSongs(limit: Int = 30): List<Song> = try {
        kotlinx.coroutines.withTimeoutOrNull(3_500L) { spotify.homeSongs(limit) }.orEmpty().ifEmpty { innerTube.getTrendingSongs(limit = limit) }
    } catch (e: Exception) {
        Timber.w(e, "$TAG: InnerTube trending failed")
        emptyList()
    }
}
