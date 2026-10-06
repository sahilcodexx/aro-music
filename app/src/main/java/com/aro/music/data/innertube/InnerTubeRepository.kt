package com.aro.music.data.innertube

import com.aro.music.data.model.Album
import com.aro.music.data.model.Artist
import com.aro.music.data.model.ArtistRef
import com.aro.music.data.model.Playlist
import com.aro.music.data.model.Song
import com.metrolist.innertubex.models.YouTubeClient
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
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

        /**
         * Browse id for the starter feed.
         *
         * `FEmusic_charts` is artist/album tiles only (50 renderers, 0 playable,
         * verified against the live response), and `FEmusic_explore` is mood and
         * album tiles that carry no `videoId` either — that is what made the home
         * screen show "No data to show yet": 41 rows parsed, all of them filtered
         * out for a missing playtime. `FEmusic_home` is what Meld browses
         * (`YouTube.kt`: `browse(WEB_REMIX, "FEmusic_home", …)`): its shelves are
         * `musicTwoRowItemRenderer` song tiles whose navigation endpoint is a
         * `watchEndpoint`, so every playable row arrives with a `videoId`.
         */
        private const val BROWSE_HOME = "FEmusic_home"

        /** Placeholder when a search row has no artist byline. */
        private const val UNKNOWN_ARTIST = "Unknown artist"

        /** `Playlist.source` discriminator for YouTube Music playlists. */
        const val SOURCE_INNERTUBE = "INNERTUBE"

        /**
         * Prefix that turns a `next` request into a radio mix of that track.
         * YouTube treats `RDAMVM<videoId>` as "automix from this seed".
         */
        private const val RADIO_PLAYLIST_PREFIX = "RDAMVM"
    }

    /**
     * De-duplicates concurrent identical catalogue fetches.
     *
     * The library, the home feed and the application start-up prefetch all ask for
     * trending songs within a second of each other. Without this they each issued
     * their own browse, and three simultaneous parses of a multi-megabyte response
     * were enough to exhaust the heap.
     */
    private val requestScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val requestMutex = kotlinx.coroutines.sync.Mutex()
    private val inFlight = mutableMapOf<String, Deferred<Any?>>()

    private suspend fun <T> share(key: String, block: suspend () -> T): T {
        @Suppress("UNCHECKED_CAST")
        val deferred = requestMutex.withLock {
            (inFlight[key]?.takeUnless { it.isCompleted }
                ?: requestScope.async { block() }.also { inFlight[key] = it }) as Deferred<T>
        }
        return try {
            deferred.await()
        } finally {
            requestMutex.withLock {
                if (deferred.isCompleted && inFlight[key] === deferred) inFlight.remove(key)
            }
        }
    }

    @Volatile private var homeCache: List<Song> = emptyList()
    @Volatile private var homeFetchedAt = 0L

    @Volatile private var homeRoot: kotlinx.serialization.json.JsonObject? = null
    @Volatile private var homeRootFetchedAt = 0L

    private suspend fun homeRoot(): kotlinx.serialization.json.JsonObject? {
        homeRoot?.takeIf { System.currentTimeMillis() - homeRootFetchedAt < 5 * 60_000L }?.let { return it }
        return share("home-root") {
            engine.browse(browseId = BROWSE_HOME)?.also {
                homeRoot = it
                homeRootFetchedAt = System.currentTimeMillis()
            }
        }
    }

    suspend fun getHomePage(continuation: String? = null): HomeCatalogPage = withContext(Dispatchers.IO) {
        val root = if (continuation == null) homeRoot() else engine.browse(BROWSE_HOME, continuation = continuation)
        HomeCatalogPage(mapHomeSections(root), InnerTubeSearchParser.parse(root).continuation)
    }

    suspend fun getExploreSections(): List<HomeCatalogSection> = share("explore") {
        mapHomeSections(engine.browse("FEmusic_explore"))
    }

    private fun mapHomeSections(root: kotlinx.serialization.json.JsonObject?): List<HomeCatalogSection> =
        InnerTubeSearchParser.parseHomeSections(root).map { section ->
            HomeCatalogSection(section.title, section.label, buildList {
                section.songs.mapTo(this) { HomeCatalogItem.Track(it.toSong()) }
                section.albums.mapTo(this) { album ->
                    val id = InnerTubeIds.albumId(album.browseId)
                    registry.putAlbum(id, album.browseId)
                    HomeCatalogItem.AlbumItem(album.toAlbum(id))
                }
                section.playlists.mapTo(this) { HomeCatalogItem.PlaylistItem(it) }
                section.artists.mapTo(this) { artist ->
                    val id = InnerTubeIds.artistId(artist.browseId)
                    registry.putArtist(id, artist.browseId)
                    HomeCatalogItem.ArtistItem(artist.toArtist(id))
                }
            }.distinctBy { it.id })
        }

    /** Searches YouTube Music for songs matching [query]. */
    suspend fun searchSongs(query: String, limit: Int = 20): List<Song> =
        share("songs:$query") {
            withContext(Dispatchers.IO) {
                val page = engine.search(query = query, params = PARAMS_SONGS)
                page.tracks.take(limit).map { it.toSong() }
            }
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
    suspend fun getTrendingSongs(limit: Int = 30): List<Song> {
        val ttl = if (homeCache.isEmpty()) 20_000L else 5 * 60_000L
        if (System.currentTimeMillis() - homeFetchedAt < ttl) return homeCache.take(limit)
        return share("home") {
            val root = homeRoot()
            // Playable song rows routinely carry album/artist metadata. Their
            // videoId, rather than the absence of that metadata, identifies them.
            val direct = InnerTubeSearchParser.parse(root).tracks.map { it.toSong() }
            val songs = if (direct.isNotEmpty()) direct else {
                // Anonymous home may contain only playlist/album shelves. Resolve
                // a real shelf source instead of substituting keyword searches.
                val playlists = InnerTubeSearchParser.parsePlaylists(root).take(2)
                coroutineScope {
                    playlists.map { playlist -> async { browseTracks(playlist.browseId, limit = 50) } }.map { it.await() }.flatten()
                }
            }
            homeCache = songs.distinctBy { it.id }
            homeFetchedAt = System.currentTimeMillis()
            Timber.d("$TAG: home resolved ${homeCache.size} songs")
            homeCache.take(limit)
        }
    }

    /**
 * One page of a radio feed.
 *
 * [continuation] is null once the feed is exhausted, which is how the queue knows
 * to stop asking for more.
 */
data class InnerTubeRadioPage(
    val songs: List<Song>,
    val continuation: String?,
)

/**
 * Fetches YouTube Music's radio for a seed track.
 *
 * Passing `playlistId = "RDAMVM<videoId>"` to the `next` endpoint is what asks
 * for a *radio mix* rather than an album or playlist — this is the same trick
 * Meld's `YouTubeQueue` uses, and it is what makes the queue effectively endless.
 *
 * [continuation] must be echoed back on each subsequent call to walk the feed.
 */
suspend fun radioPage(
    seedVideoId: String,
    continuation: String? = null,
): InnerTubeRadioPage = withContext(Dispatchers.IO) {
    val page = engine.next(
        videoId = seedVideoId,
        playlistId = RADIO_PLAYLIST_PREFIX + seedVideoId,
        continuation = continuation,
    )
    InnerTubeRadioPage(
        songs = page.tracks.map { it.toSong() },
        continuation = page.continuation,
    )
}    fun headersFor(audioUrl: String) = engine.headersFor(audioUrl)

    /** Resolves a playable stream. Returns null when YouTube refuses playback. */
    suspend fun resolveStream(videoId: String): InnerTubeStream? =
        withContext(Dispatchers.IO) { engine.extractStream(videoId) }

    /**
     * Live query suggestions for the search field, Meld's `YouTube
     * .searchSuggestions` port.
     *
     * Returns text completions plus recommended playable items; the repository
     * maps the items through the same [toSong] conversion as search rows so the
     * UI can navigate with them unchanged. Fails soft — an empty result is the
     * caller's signal to show only history.
     */
    suspend fun getSuggestions(query: String): InnerTubeSuggestionResult =
        withContext(Dispatchers.IO) {
            val root = engine.searchSuggestions(query)
                ?: return@withContext InnerTubeSuggestionResult(emptyList(), emptyList())
            val parsed = InnerTubeSearchParser.parseSuggestions(root)
            InnerTubeSuggestionResult(
                queries = parsed.queries,
                songs = parsed.recommendedItems.map { it.toSong() },
            )
        }

    /**
     * Lyrics for an InnerTube track, straight from YouTube Music — Meld's
     * `YouTubeLyricsProvider` chain in order: the track's line-timed lyrics
     * page, then the WEB_REMIX plain shelf, then video captions.
     *
     * This is what gives streaming songs synced lyrics without LRCLIB: the
     * timed browse goes out as ANDROID_MUSIC, which is the only client YouTube
     * serves per-line cue ranges to.
     */
    suspend fun getYouTubeLyrics(videoId: String): String? =
        withContext(Dispatchers.IO) {
            val nextRoot = engine.nextRaw(videoId = videoId)
            val endpoint = InnerTubeSearchParser.findLyricsEndpoint(nextRoot)

            if (endpoint != null) {
                engine.timedLyrics(browseId = endpoint.first, params = endpoint.second)
                    ?.let { return@withContext it }

                val plainRoot = engine.browseRaw(
                    browseId = endpoint.first,
                    params = endpoint.second,
                    clients = listOf(YouTubeClient.WEB_REMIX),
                )
                InnerTubeSearchParser.parsePlainLyrics(plainRoot)
                    ?.let { return@withContext it }
            }

            engine.transcript(videoId)
        }

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
            albumArtUriString = InnerTubeArtwork.forSong(artworkUrl, InnerTubeIds.contentUri(videoId)),
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
 * Live search suggestions mapped to app models.
 *
 * [queries] are the plain text completions for the dropdown; [songs] are the
 * recommended playable items, already converted with the same [InnerTubeTrack
 * .toSong] mapping search rows use so navigation needs no special case.
 */
data class InnerTubeSuggestionResult(
    val queries: List<String>,
    val songs: List<Song>,
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