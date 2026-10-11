package com.aro.music.spotify

import android.content.Context
import com.aro.music.data.database.AroDatabase
import com.aro.music.data.model.Song
import com.aro.music.data.queue.QueueProvider
import com.aro.music.data.queue.SpotifyQueue
import com.aro.music.data.queue.SpotifyPlaylistQueue
import com.aro.music.data.queue.SpotifyLikedSongsQueue
import com.aro.music.spotify.models.SpotifyTrack
import com.aro.music.utils.MediaItemBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** App-facing adapter: Spotify catalog/profile, YouTube audio, existing Aro UI models. */
@Singleton
class SpotifyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SpotifySession,
    private val database: AroDatabase,
    val mapper: SpotifyYouTubeMapper,
    private val registry: com.aro.music.data.innertube.InnerTubeBrowseRegistry,
) {
    private val playlistCache by lazy { SpotifyPlaylistCache(context) }
    private val playlistLocks = Array(32) { kotlinx.coroutines.sync.Mutex() }
    val libraryPlaylistsFlow = session.isLoggedInFlow.mapLatest { connected ->
        if (!connected) return@mapLatest emptyList<com.aro.music.data.model.Playlist>()
        catalog {
            val playlists = mutableListOf<com.aro.music.data.model.Playlist>()
            playlists.add(com.aro.music.data.model.Playlist("spotify:liked", "Spotify Liked Songs", emptyList(), source = "SPOTIFY", remoteSongCount = playlistCount("spotify:liked")))
            var offset = 0
            do {
                val page = Spotify.myPlaylists(limit = 50, offset = offset).getOrThrow()
                playlists += page.items.map { playlist ->
                    com.aro.music.data.model.Playlist("spotify:${playlist.id}", playlist.name, emptyList(),
                        coverImageUri = playlist.images.firstOrNull()?.url, source = "SPOTIFY", remoteSongCount = playlist.tracks?.total)
                }
                offset += page.items.size
            } while (page.items.isNotEmpty() && offset < page.total)
            playlists.distinctBy { it.id }.chunked(4).flatMap { batch ->
                coroutineScope {
                    batch.map { playlist -> async {
                        if (playlist.remoteSongCount != null) playlist
                        else playlist.copy(remoteSongCount = playlistCount(playlist.id))
                    } }.awaitAll()
                }
            }
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    private suspend fun playlistCount(id: String): Int? = try {
        if (id == "spotify:liked") Spotify.likedSongs(1, 0).getOrThrow().total
        else Spotify.playlistTracks(id.removePrefix("spotify:"), 1, 0).getOrThrow().total
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.w(e, "Spotify playlist count unavailable")
        null
    }

    suspend fun searchSongs(query: String, limit: Int): List<Song> = safely {
        if (!session.ensureAuthenticated()) return@safely emptyList()
        resolve(Spotify.search(query, limit = limit.coerceIn(1, 50)).getOrThrow().tracks?.items.orEmpty())
    }

    suspend fun homeSongs(limit: Int): List<Song> = safely {
        if (!session.ensureAuthenticated()) return@safely emptyList()
        resolve(SpotifyProfileCache.getTopTracks(context, database, limit))
    }

    suspend fun searchAlbums(query: String): List<com.aro.music.data.model.Album> = catalog {
        Spotify.search(query, types = listOf("album")).getOrThrow().albums?.items.orEmpty().map(::toAlbum)
    }

    suspend fun searchArtists(query: String): List<com.aro.music.data.model.Artist> = catalog {
        Spotify.search(query, types = listOf("artist")).getOrThrow().artists?.items.orEmpty().map { artist ->
            val id = com.aro.music.data.innertube.InnerTubeIds.artistId("spotify:${artist.id}")
            registry.putArtist(id, "spotify:${artist.id}")
            com.aro.music.data.model.Artist(id, artist.name, 0, artist.images.firstOrNull()?.url)
        }
    }

    private fun toAlbum(album: com.aro.music.spotify.models.SpotifyAlbum): com.aro.music.data.model.Album {
        val id = com.aro.music.data.innertube.InnerTubeIds.albumId("spotify:${album.id}")
        registry.putAlbum(id, "spotify:${album.id}")
        return com.aro.music.data.model.Album(id, album.name, album.artists.joinToString(", ") { it.name },
            0, 0, album.images.firstOrNull()?.url, album.totalTracks)
    }

    suspend fun album(browseId: String): com.aro.music.data.model.Album? {
        if (!session.ensureAuthenticated()) return null
        return Spotify.album(browseId.removePrefix("spotify:")).getOrNull()?.let(::toAlbum)
    }

    suspend fun albumSongs(browseId: String): List<Song> = safely {
        if (!session.ensureAuthenticated()) return@safely emptyList()
        val album = Spotify.album(browseId.removePrefix("spotify:")).getOrThrow()
        val parent = com.aro.music.spotify.models.SpotifySimpleAlbum(id = album.id, name = album.name, images = album.images)
        resolve(album.tracks?.items.orEmpty().map { it.copy(album = parent) })
    }

    suspend fun artistSongs(browseId: String): List<Song> = safely {
        if (!session.ensureAuthenticated()) return@safely emptyList()
        resolve(Spotify.artistTopTracks(browseId.removePrefix("spotify:")).getOrThrow().tracks)
    }

    suspend fun artist(browseId: String): com.aro.music.data.model.Artist? = try {
        if (!session.ensureAuthenticated()) null else {
            val artist = Spotify.artist(browseId.removePrefix("spotify:")).getOrThrow()
            val id = com.aro.music.data.innertube.InnerTubeIds.artistId("spotify:${artist.id}")
            registry.putArtist(id, "spotify:${artist.id}")
            com.aro.music.data.model.Artist(id, artist.name, 0, artist.images.firstOrNull()?.url)
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.w(e, "Spotify artist unavailable")
        null
    }

    data class ResolvedPlaylist(val playlist: com.aro.music.data.model.Playlist, val songs: List<Song>, val totalTracks: Int = songs.size)

    suspend fun searchPlaylists(query: String): List<ResolvedPlaylist> = catalog {
        val results = Spotify.search(query, types = listOf("playlist"), limit = 3).getOrThrow().playlists?.items.orEmpty()
        results.mapNotNull { playlist -> resolvePlaylist("spotify:${playlist.id}", playlist.name, playlist.images.firstOrNull()?.url, 50) }
    }

    suspend fun resolvePlaylist(id: String, name: String? = null, artwork: String? = null,
        maxTracks: Int = Int.MAX_VALUE, onProgress: ((Int, Int) -> Unit)? = null,
        onPartial: (suspend (ResolvedPlaylist) -> Unit)? = null, forceRefresh: Boolean = false,
    ): ResolvedPlaylist? = withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!session.ensureAuthenticated()) return@withContext null
        playlistLocks[(id.hashCode() and Int.MAX_VALUE) % playlistLocks.size].withLock {
            // Preview callers still fetch their requested source slice. Only complete
            // playlist opens/imports read or write this snapshot.
            if (maxTracks == Int.MAX_VALUE && !forceRefresh) playlistCache.read(id)?.let { cached ->
                onPartial?.invoke(cached)
                onProgress?.invoke(cached.totalTracks, cached.totalTracks)
                return@withLock cached
            }
            val liked = id == "spotify:liked"
            val spotifyId = id.removePrefix("spotify:")
            val metadata = if (!liked && name == null) Spotify.playlist(spotifyId).getOrThrow() else null
            var header = com.aro.music.data.model.Playlist(id, name ?: metadata?.name ?: "Spotify Liked Songs", emptyList(),
                coverImageUri = artwork ?: metadata?.images?.firstOrNull()?.url, source = "SPOTIFY", remoteSongCount = metadata?.tracks?.total)
            onPartial?.invoke(ResolvedPlaylist(header, emptyList(), header.remoteSongCount ?: 0))
            val songs = mutableListOf<Song>()
            var offset = 0
            do {
                val limit = minOf(50, maxTracks - offset)
                val page = if (liked) {
                    val result = Spotify.likedSongs(limit, offset).getOrThrow()
                    Triple(result.items.map { it.track }, result.items.size, result.total)
                } else {
                    val result = Spotify.playlistTracks(spotifyId, limit, offset).getOrThrow()
                    Triple(result.items.mapNotNull { it.track }, result.rawCount ?: result.items.size, result.total)
                }
                header = header.copy(remoteSongCount = page.third)
                check(page.second > 0 || offset >= page.third) { "Playlist page incomplete" }
                val previous = songs.toList()
                val resolvedPage = resolve(page.first.filter { !it.isLocal }, onBatch = { batch ->
                    val available = (previous + batch).distinctBy { it.id }
                    onPartial?.invoke(ResolvedPlaylist(header.copy(songIds = available.map { it.id }), available, page.third))
                })
                songs.addAll(resolvedPage)
                offset += page.second
                onProgress?.invoke(offset, page.third)
            } while (page.second > 0 && offset < page.third && offset < maxTracks)
            val unique = songs.distinctBy { it.id }
            val result = ResolvedPlaylist(header.copy(songIds = unique.map { it.id }), unique, offset)
            if (maxTracks == Int.MAX_VALUE) playlistCache.write(result)
            result
        }
    }

    private suspend fun <T> catalog(block: suspend () -> List<T>): List<T> = try {
        if (session.ensureAuthenticated()) block() else emptyList()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.w(e, "Spotify catalog unavailable")
        emptyList()
    }

    suspend fun radioFor(song: Song): QueueProvider? {
        if (!session.ensureAuthenticated()) return null
        val known = SpotifyMetadataRegistry.get(song.id)
        val videoId = com.aro.music.data.innertube.InnerTubeIds.videoIdFromContentUri(song.contentUriString)
        val cached = if (known == null && videoId != null) database.spotifyMatchDao().getByYouTubeId(videoId) else null
        val track = known ?: cached?.let { match ->
            Spotify.search("${song.title} ${song.artist}").getOrNull()?.tracks?.items?.find { it.id == match.spotifyId }
        } ?: return null
        return AuthenticatedSpotifyQueue(session, SpotifyQueue(track, mapper, context, database, MediaItemBuilder.build(song)))
    }

    fun playlistQueue(playlistId: String, startIndex: Int = 0): QueueProvider =
        AuthenticatedSpotifyQueue(session, SpotifyPlaylistQueue(playlistId, startIndex = startIndex, mapper = mapper))

    fun likedSongsQueue(startIndex: Int = 0): QueueProvider =
        AuthenticatedSpotifyQueue(session, SpotifyLikedSongsQueue(startIndex, mapper))

    suspend fun resolve(tracks: List<SpotifyTrack>, onProgress: ((Int, Int) -> Unit)? = null, onBatch: (suspend (List<Song>) -> Unit)? = null): List<Song> {
        val resolved = mutableListOf<Song>()
        var processed = 0
        for (batch in tracks.chunked(4)) {
            coroutineScope {
                val jobs = batch.map { track -> async { mapper.mapToYouTube(track) } }
                for (job in jobs) {
                    job.await()?.let { resolved += it }
                    onBatch?.invoke(resolved.distinctBy { it.id })
                }
            }
            processed += batch.size
            onProgress?.invoke(processed, tracks.size)
        }
        return resolved.distinctBy { it.id }
    }

    private suspend fun safely(block: suspend () -> List<Song>): List<Song> = try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.w(e, "Spotify catalog unavailable")
        emptyList()
    }
}

private class AuthenticatedSpotifyQueue(
    private val session: SpotifySession,
    private val queue: QueueProvider,
) : QueueProvider by queue {
    override suspend fun getInitialStatus(): QueueProvider.Status {
        check(session.ensureAuthenticated()) { "Reconnect Spotify in Accounts" }
        return queue.getInitialStatus()
    }
    override suspend fun nextPage(): List<androidx.media3.common.MediaItem> {
        check(session.ensureAuthenticated()) { "Reconnect Spotify in Accounts" }
        return queue.nextPage()
    }
    override suspend fun getFullStatus(): QueueProvider.Status? {
        check(session.ensureAuthenticated()) { "Reconnect Spotify in Accounts" }
        return queue.getFullStatus()
    }
    override suspend fun shuffleRemainingTracks() {
        check(session.ensureAuthenticated()) { "Reconnect Spotify in Accounts" }
        queue.shuffleRemainingTracks()
    }
}
