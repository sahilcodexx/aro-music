package com.aro.music.data.innertube

import com.aro.music.data.model.Album
import com.aro.music.data.model.Artist
import com.aro.music.data.model.ArtistRef
import com.aro.music.data.model.Playlist
import com.aro.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps InnerTube (YouTube Music) results onto the app's [Song]/[Album]/[Artist]
 * models and resolves stream URLs.
 *
 * Two conventions matter here and both are easy to get wrong:
 *
 *  * **Ids must be numeric.** Every id path in the app — Room primary keys,
 *    favourite rows, `getSongsByIds`, sort queries — parses with
 *    `toLongOrNull()`. A prefixed id like `yt_…` silently drops the song on
 *    every one of those paths, so ids are folded to negative Longs by
 *    [InnerTubeIds] and rendered as plain numeric strings.
 *  * **Stream URLs are never stored.** YouTube stream URLs are signed and
 *    expire within hours, so `Song.path` is left empty and the player resolves
 *    a fresh URL from the `innertube://<videoId>` content URI immediately
 *    before playback. Search results, by contrast, are stable and safe to
 *    cache by video id.
 */
@Singleton
class InnerTubeRepository @Inject constructor(
    private val engine: InnerTubeEngine,
    private val registry: InnerTubeBrowseRegistry,
) {
    companion object {
        private const val TAG = "InnerTubeRepo"

        /**
         * YouTube Music search tab filters.
         *
         * Without these, search returns the default mixed tab: mostly music
         * videos and user uploads with no album or artist metadata, which is
         * what makes a naive InnerTube integration feel wrong. Each filter
         * pins the response to one entity type.
         */
        const val PARAMS_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        const val PARAMS_ALBUMS = "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"
        const val PARAMS_ARTISTS = "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"
        const val PARAMS_PLAYLISTS = "EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D"

        /** Charts browse id and its region-scoped params. */
        private const val BROWSE_CHARTS = "FEmusic_charts"
        private const val CHARTS_PARAMS = "ggMGCgQIgAQ%3D"

        /** Placeholder when a search row has no artist byline. */
        private const val UNKNOWN_ARTIST = "Unknown artist"

        /** `Playlist.source` discriminator for YouTube Music playlists. */
        const val SOURCE_INNERTUBE = "INNERTUBE"
    }

    /** Searches YouTube Music for songs matching [query]. */
    suspend fun searchSongs(query: String, limit: Int = 20): List<Song> =
        withContext(Dispatchers.IO) {
            val page = engine.search(query = query, params = PARAMS_SONGS)
            page.tracks.take(limit).map { it.toSong() }
        }

    /** Second and later pages of a song search, for infinite scroll. */
    suspend fun searchSongsContinuation(continuation: String, limit: Int = 20): List<Song> =
        withContext(Dispatchers.IO) {
            val page = engine.search(continuation = continuation)
            page.tracks.take(limit).map { it.toSong() }
        }

    /** The continuation token for the next page of the current song search. */
    suspend fun searchAlbums(query: String, limit: Int = 30): List<Album> =
        withContext(Dispatchers.IO) {
            val root = engine.searchRaw(query = query, params = PARAMS_ALBUMS)
            InnerTubeSearchParser.parseAlbums(root).take(limit).map { album ->
                // Capture the browseId now: navigation will only carry the
                // synthetic id, and it cannot be hashed back.
                val appId = InnerTubeIds.albumId(album.browseId)
                registry.putAlbum(appId, album.browseId)
                album.toAlbum(appId)
            }
        }

    suspend fun searchArtists(query: String, limit: Int = 30): List<Artist> =
        withContext(Dispatchers.IO) {
            val root = engine.searchRaw(query = query, params = PARAMS_ARTISTS)
            InnerTubeSearchParser.parseArtists(root).take(limit).map { artist ->
                val appId = InnerTubeIds.artistId(artist.browseId)
                registry.putArtist(appId, artist.browseId)
                artist.toArtist(appId)
            }
        }

    /**
     * Search playlists.
     *
     * Track lists are *not* fetched here — that is one request per playlist and
     * a search page returns dozens. Callers that need the tracks ask for them by
     * id via [getPlaylistTracks].
     */
    suspend fun searchPlaylists(query: String, limit: Int = 20): List<InnerTubePlaylist> =
        withContext(Dispatchers.IO) {
            val root = engine.searchRaw(query = query, params = PARAMS_PLAYLISTS)
            InnerTubeSearchParser.parsePlaylists(root).take(limit)
        }

    /**
     * Tracks of an album, from its `MPRE…` browseId.
     *
     * The album browse response embeds the full tracklist, so a single request
     * covers both the tracks and (via [getAlbum]) the metadata.
     */
    suspend fun getAlbumTracks(browseId: String, limit: Int = 300): List<Song> =
        withContext(Dispatchers.IO) {
            browseTracks(browseId = browseId, limit = limit)
        }

    /**
     * A track's own metadata for an album browseId, used to populate the albums
     * table the first time an album is opened.
     */
    suspend fun getAlbum(browseId: String): Album? = withContext(Dispatchers.IO) {
        val root = engine.browse(browseId = browseId) ?: return@withContext null
        val album = InnerTubeSearchParser.parsePageHeader(root)
        val appId = InnerTubeIds.albumId(browseId)
        registry.putAlbum(appId, browseId)
        album?.toAlbum(appId)
    }

    /**
     * Top songs for an artist channel id (`UC…`).
     *
     * The artist browse response embeds a "Songs" shelf, so one request is
     * enough for the page the UI actually shows.
     */
    suspend fun getArtistSongs(browseId: String, limit: Int = 100): List<Song> =
        withContext(Dispatchers.IO) {
            browseTracks(browseId = browseId, limit = limit)
        }

    /** Tracks of a playlist, from its browseId (`VLPL…`). */
    suspend fun getPlaylistTracks(browseId: String, limit: Int = 300): List<Song> =
        withContext(Dispatchers.IO) {
            browseTracks(browseId = browseId, limit = limit)
        }

    private suspend fun browseTracks(browseId: String, limit: Int): List<Song> {
        val root = engine.browse(browseId = browseId) ?: return emptyList()
        return InnerTubeSearchParser.parse(root).tracks.take(limit).map { it.toSong() }
    }

    /**
     * Fetches the radio/queue continuation for a track.
     *
     * This is YouTube Music's own related-tracks feed, so it replaces local
     * "similar songs" heuristics with real recommendations. Returns an empty
     * list when the seed has no radio (region locked, or removed).
     */
    suspend fun getRadioFor(videoId: String, limit: Int = 50): List<Song> =
        withContext(Dispatchers.IO) {
            val page = engine.next(videoId = videoId)
            page.tracks.take(limit).map { it.toSong() }
        }

    /**
     * Starter songs for the library, from the YouTube Music charts feed.
     *
     * This is the first backend in the app to serve a genuine trending feed
     * rather than a keyword search, which matters because the previous
     * implementation rotated through 15 hard-coded queries and shuffled them —
     * so the "library" was different every day and never matched real popularity.
     * Returns an empty list when the charts feed is unavailable, which lets
     * [com.aro.music.data.streaming.StreamingRepository] fall back.
     */
    suspend fun getTrendingSongs(limit: Int = 30): List<Song> =
        withContext(Dispatchers.IO) {
            val root = engine.browse(browseId = BROWSE_CHARTS, params = CHARTS_PARAMS)
            if (root == null) {
                Timber.w("$TAG: charts browse unavailable")
                return@withContext emptyList()
            }
            val page = InnerTubeSearchParser.parse(root)
            val tracks = page.tracks.take(limit).map { it.toSong() }
            Timber.d("$TAG: charts → ${tracks.size} tracks")
            tracks
        }

    /** Resolves a playable stream. Returns null when YouTube refuses playback. */
    suspend fun resolveStream(videoId: String): InnerTubeStream? =
        withContext(Dispatchers.IO) { engine.extractStream(videoId) }

    // ── mapping ─────────────────────────────────────────────────────────────

    private fun InnerTubeTrack.toSong(): Song {
        val albumIdValue = albumId?.let { InnerTubeIds.albumId(it) } ?: 0L
        val artistIdValue = artistId?.let { InnerTubeIds.artistId(it) } ?: 0L
        // Search rows can carry an empty byline; `displayArtist` reads the
        // `artists` list, so the fallback has to be applied to both fields or
        // the UI shows a blank artist.
        val artistName = artist.ifBlank { UNKNOWN_ARTIST }
        return Song(
            // Numeric on purpose: see the class comment.
            id = InnerTubeIds.songId(videoId).toString(),
            title = title,
            artist = artistName,
            artistId = artistIdValue,
            artists = listOf(ArtistRef(id = artistIdValue, name = artistName, isPrimary = true)),
            album = album.orEmpty(),
            albumId = albumIdValue,
            albumArtist = null,
            // Empty: the player resolves a signed URL from the content URI.
            path = "",
            contentUriString = InnerTubeIds.contentUri(videoId),
            albumArtUriString = artworkUrl,
            duration = (durationSeconds ?: 0).toLong() * 1000L,
            genre = null,
            lyrics = null,
            isFavorite = false,
            trackNumber = 0,
            discNumber = null,
            year = 0,
            dateAdded = System.currentTimeMillis(),
            dateModified = 0L,
            mimeType = "audio/mpeg",
            bitrate = null,
            sampleRate = null,
            telegramFileId = null,
            telegramChatId = null,
            neteaseId = null,
            gdriveFileId = null,
            qqMusicMid = null,
            navidromeId = null,
            jellyfinId = null,
        )
    }

    private fun InnerTubeAlbum.toAlbum(appId: Long): Album = Album(
        id = appId,
        title = title,
        artist = artist.orEmpty(),
        year = year ?: 0,
        dateAdded = System.currentTimeMillis(),
        albumArtUriString = artworkUrl,
        // Unknown until the album page is opened; the library treats 0 as
        // "not yet counted" rather than as an empty album.
        songCount = songCount ?: 0,
        albumArtist = artist,
    )

    private fun InnerTubeArtist.toArtist(appId: Long): Artist = Artist(
        id = appId,
        name = title,
        songCount = 0,
        imageUrl = artworkUrl,
    )

    /**
     * Converts a remote playlist into the app's [Playlist] model.
     *
     * [Playlist.songIds] is left empty here on purpose. The search UI resolves
     * a playlist row by observing those ids immediately, so a row with no ids
     * renders as an empty playlist rather than a broken one; callers that want
     * a playable row fill them from [getPlaylistTracks] first. `source` is set
     * to `INNERTUBE` so the row can be told apart from a user playlist.
     */
    private fun InnerTubePlaylist.toPlaylist(songIds: List<String> = emptyList()): Playlist =
        Playlist(
            id = browseId,
            name = title,
            songIds = songIds,
            createdAt = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis(),
            isAiGenerated = false,
            isQueueGenerated = false,
            coverImageUri = artworkUrl,
            source = SOURCE_INNERTUBE,
        )

    /**
 * A resolved remote playlist: the model for the UI plus the tracks behind it.
 *
 * The tracks are returned separately because [Playlist] carries only
 * `songIds`, and the caller needs the full [Song] objects to write them to Room
 * — that is what makes `getSongsByIds` able to resolve the row later.
 */
data class ResolvedRemotePlaylist(
    val playlist: Playlist,
    val songs: List<Song>,
)

/**
 * Builds a playable playlist by resolving its tracks.
 *
 * Returns the playlist with [Playlist.songIds] populated and the tracks
 * alongside, or a playlist with no ids when the browse returned nothing
 * playable.
 */
suspend fun resolveRemotePlaylist(
    browseId: String,
    title: String,
    artworkUrl: String?,
    limit: Int = 300,
): ResolvedRemotePlaylist = withContext(Dispatchers.IO) {
    val songs = getPlaylistTracks(browseId = browseId, limit = limit)
    if (songs.isNotEmpty()) {
        Timber.d("$TAG: playlist $browseId resolved ${songs.size} tracks")
    } else {
        Timber.w("$TAG: playlist $browseId returned no playable tracks")
    }
    val playlist = InnerTubePlaylist(browseId = browseId, title = title, artworkUrl = artworkUrl)
        .toPlaylist(songs.map { it.id })
    ResolvedRemotePlaylist(playlist = playlist, songs = songs)
}

/**
 * Fetches a playlist by its browseId, taking the name and cover from the page
 * header rather than requiring the caller to already know them.
 *
 * This is what the playlist detail screen needs, where all that survives
 * navigation is the id. One browse covers both header and tracklist.
 */
suspend fun getRemotePlaylist(browseId: String, limit: Int = 300): ResolvedRemotePlaylist? =
    withContext(Dispatchers.IO) {
        val root = engine.browse(browseId = browseId) ?: return@withContext null
        val header = InnerTubeSearchParser.parsePageHeader(root)
        val songs = InnerTubeSearchParser.parse(root).tracks.take(limit).map { it.toSong() }
        val playlist = InnerTubePlaylist(
            browseId = browseId,
            title = header?.title ?: "Playlist",
            author = header?.artist,
            artworkUrl = header?.artworkUrl,
        ).toPlaylist(songs.map { it.id })
        Timber.d("$TAG: playlist $browseId → '${playlist.name}' with ${songs.size} tracks")
        ResolvedRemotePlaylist(playlist = playlist, songs = songs)
    }

/** The browseId captured for [albumId], or null when it was never seen. */
fun browseIdForAlbum(albumId: Long): String? = registry.albumBrowseId(albumId)

/** The browseId captured for [artistId], or null when it was never seen. */
fun browseIdForArtist(artistId: Long): String? = registry.artistBrowseId(artistId)
}