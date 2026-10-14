package com.aro.music.data.repository

// import kotlinx.coroutines.withContext // May not be needed for Flow transformations

// import kotlinx.coroutines.sync.withLock // May not be needed if directoryScanMutex logic changes

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log

import com.aro.music.data.innertube.InnerTubeIds
import com.aro.music.data.model.Song
import com.aro.music.data.repository.ArtistImageRepository
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri
import com.aro.music.data.database.FavoritesDao
import com.aro.music.data.database.MusicDao
import com.aro.music.data.database.SearchHistoryDao
import com.aro.music.data.database.SearchHistoryEntity
import com.aro.music.data.database.TelegramChannelEntity
import com.aro.music.data.database.TelegramDao
import com.aro.music.data.database.toAlbum
import com.aro.music.data.database.toArtist
import com.aro.music.data.database.toEntity
import com.aro.music.data.database.toSearchHistoryItem
import com.aro.music.data.database.toSong
import com.aro.music.data.database.toEntityWithoutPaths
import com.aro.music.data.database.toTelegramEntity
import com.aro.music.data.database.toTelegramEntityWithThread
import com.aro.music.data.database.TelegramTopicEntity
import com.aro.music.data.model.Album
import com.aro.music.data.model.Artist
import com.aro.music.data.model.Genre
import com.aro.music.data.model.Lyrics
import com.aro.music.data.model.LyricsSourcePreference
import com.aro.music.data.model.MusicFolder
import com.aro.music.data.model.Playlist
import com.aro.music.data.model.SearchFilterType
import com.aro.music.data.model.SearchHistoryItem
import com.aro.music.data.model.SearchResultItem
import com.aro.music.data.model.SortOption
import com.aro.music.data.model.FolderSource
import com.aro.music.data.model.StorageFilter
import com.aro.music.data.preferences.PlaylistPreferencesRepository
import com.aro.music.data.preferences.UserPreferencesRepository
import com.aro.music.ui.theme.GenreThemeUtils
import com.aro.music.utils.DirectoryFilterUtils
import com.aro.music.utils.LogUtils
import com.aro.music.utils.StorageType
import com.aro.music.utils.StorageUtils
import com.aro.music.utils.toHexString
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.paging.filter
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val musicDao: MusicDao,
    private val lyricsRepository: LyricsRepository,
    private val telegramDao: TelegramDao,
    private val telegramCacheManagerProvider: Lazy<com.aro.music.data.telegram.TelegramCacheManager>,
    private val telegramRepositoryProvider: Lazy<com.aro.music.data.telegram.TelegramRepository>,
    private val songRepository: SongRepository,
    private val favoritesDao: FavoritesDao,
    private val artistImageRepository: ArtistImageRepository,
    private val folderTreeBuilder: FolderTreeBuilder,
    private val streamingRepository: com.aro.music.data.streaming.StreamingRepository,
    private val innerTubeRepository: com.aro.music.data.innertube.InnerTubeRepository
) : MusicRepository {

    companion object {
        /** Maximum number of search results to load at once to avoid memory issues with large libraries. */
        private const val SEARCH_RESULTS_LIMIT = 100
        private const val TRENDING_LIBRARY_LIMIT = 50

        /**
         * How many songs to pull and persist for the home mix preview. Fetching
         * exactly `limit` would leave the preview with no variety on rotation.
         */
        private const val HOME_PREVIEW_FETCH_LIMIT = 50

        /**
         * How many remote playlists a search resolves to a real track list.
         *
         * Each one costs a browse request, so this is deliberately small: a
         * search page returns dozens of playlists and a user scrolls a few.
         */
        private const val REMOTE_PLAYLIST_RESOLVE_LIMIT = 4
        private const val UNKNOWN_GENRE_NAME = "Unknown"
        private const val UNKNOWN_GENRE_ID = "unknown"
    }

    private val directoryScanMutex = Mutex()
    private val genreCacheMutex = Mutex()
    private val genreCache = linkedMapOf<String, Pair<Long, List<Song>>>()
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val defaultLibraryPagingConfig = PagingConfig(
        pageSize = 50,
        enablePlaceholders = true,
        maxSize = 250
    )
    // Tracks the active prefetch job so a new flow emission cancels the previous one.
    @Volatile private var prefetchJob: Job? = null
    @Volatile private var currentSongArtistPrefetchJob: Job? = null
    @Volatile private var currentSongArtistPrefetchSongId: Long? = null
    @Volatile private var telegramDownloadSyncObserverStarted = false
    private val telegramCacheManager: com.aro.music.data.telegram.TelegramCacheManager
        get() = telegramCacheManagerProvider.get()
    override val telegramRepository: com.aro.music.data.telegram.TelegramRepository
        get() = telegramRepositoryProvider.get()

    private fun normalizePath(path: String): String =
        runCatching { File(path).canonicalPath }.getOrElse { File(path).absolutePath }

    /** Cached directory filter — recomputed only when allowed/blocked dirs preferences change. */
    data class CachedDirFilter(val allowedParentDirs: List<String> = emptyList(), val applyFilter: Boolean = false)

    private val cachedDirFilter: StateFlow<CachedDirFilter> = combine(
        userPreferencesRepository.allowedDirectoriesFlow,
        userPreferencesRepository.blockedDirectoriesFlow
    ) { allowed, blocked ->
        val (dirs, apply) = DirectoryFilterUtils.computeAllowedParentDirs(
            allowedDirs = allowed,
            blockedDirs = blocked,
            getAllParentDirs = { musicDao.getDistinctParentDirectories() },
            normalizePath = ::normalizePath
        )
        CachedDirFilter(dirs, apply)
    }.stateIn(repositoryScope, SharingStarted.Eagerly, CachedDirFilter())

    private fun ensureTelegramDownloadSyncObserverStarted() {
        if (telegramDownloadSyncObserverStarted) return
        telegramDownloadSyncObserverStarted = true

        repositoryScope.launch {
            telegramRepository.songFileUpdated.collect {
                androidx.work.WorkManager.getInstance(context).enqueue(
                    com.aro.music.data.worker.SyncWorker.incrementalSyncWork()
                )
            }
        }
    }

    private fun List<Artist>.missingImageCandidates(): List<Pair<Long, String>> =
        asSequence()
            .filter { it.effectiveImageUrl.isNullOrBlank() && it.name.isNotBlank() }
            .map { it.id to it.name }
            .distinctBy { (_, name) -> name.trim().lowercase() }
            .toList()

    /** Saved library membership comes from Room, independently of discovery caches. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAudioFiles(): Flow<List<Song>> = combine(
        userPreferencesRepository.allowedDirectoriesFlow,
        userPreferencesRepository.blockedDirectoriesFlow
    ) { allowed, blocked -> computeAllowedDirs(allowed, blocked) }
        .flatMapLatest { (dirs, filter) -> musicDao.getLibrarySongs(dirs, filter) }
        .map { songs -> songs.map { it.toSong() }.withFavoriteState() }
        .flowOn(Dispatchers.IO)

    override fun getPaginatedSongs(
        sortOption: SortOption,
        storageFilter: com.aro.music.data.model.StorageFilter,
    ): Flow<PagingData<Song>> = songRepository.getPaginatedSongs(sortOption, storageFilter)

    /**
     * Stamps the persisted favourite flag onto freshly fetched streaming rows.
     *
     * Search and library results come straight from the network with
     * `isFavorite = false`, so without this a favourited track shows up
     * un-favourited the next time it is fetched. Ids are stable per backend
     * (see `InnerTubeIds`), so the favourites table is a reliable join key.
     */
    private suspend fun List<Song>.withFavoriteState(): List<Song> {
        if (isEmpty()) return this
        val favoriteIds = try {
            favoritesDao.getFavoriteSongIdsOnce().map(Long::toString).toSet()
        } catch (e: Exception) {
            Log.w("MusicRepository", "Could not read favourite ids: ${e.message}")
            return this
        }
        if (favoriteIds.isEmpty()) return this
        return map { song -> if (song.id in favoriteIds) song.copy(isFavorite = true) else song }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedAlbums(
        sortOption: SortOption,
        storageFilter: StorageFilter,
        minTracks: Int
    ): Flow<PagingData<Album>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    Pager(
                        config = defaultLibraryPagingConfig,
                        pagingSourceFactory = {
                            musicDao.getAlbumsPaginated(
                                allowedParentDirs = allowedParentDirs,
                                applyDirectoryFilter = applyDirectoryFilter,
                                filterMode = storageFilter.toFilterMode(),
                                sortOrder = sortOption.storageKey,
                                minTracks = minTracks
                            )
                        }
                    ).flow
                )
            }.flatMapLatest { it }
        }.map { pagingData ->
            pagingData.map { entity -> entity.toAlbum() }
        }.flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedArtists(
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): Flow<PagingData<Artist>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    Pager(
                        config = defaultLibraryPagingConfig,
                        pagingSourceFactory = {
                            musicDao.getArtistsPaginated(
                                allowedParentDirs = allowedParentDirs,
                                applyDirectoryFilter = applyDirectoryFilter,
                                filterMode = storageFilter.toFilterMode(),
                                sortOrder = sortOption.storageKey
                            )
                        }
                    ).flow
                )
            }.flatMapLatest { it }
        }.map { pagingData ->
            pagingData.map { entity -> entity.toArtist() }
        }.flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedFavoriteSongs(sortOption: SortOption, storageFilter: StorageFilter): Flow<PagingData<Song>> {
        return songRepository.getPaginatedFavoriteSongs(sortOption, storageFilter)
    }

    override suspend fun getFavoriteSongsOnce(storageFilter: StorageFilter): List<Song> {
        return songRepository.getFavoriteSongsOnce(storageFilter)
    }

    override suspend fun getFavoriteSongsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): List<Song> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getFavoriteSongsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode(),
            limit = limit,
            offset = offset
        ).map { it.toSong() }
    }

    override fun getFavoriteSongCountFlow(storageFilter: StorageFilter): Flow<Int> {
        return songRepository.getFavoriteSongCountFlow(storageFilter)
    }

    override fun getSongCountFlow(): Flow<Int> {
        return musicDao.getLibrarySongCount().distinctUntilChanged()
    }

    override fun getCloudSongCountFlow(): Flow<Int> {
        return musicDao.getCloudSongCount().distinctUntilChanged()
    }

    override suspend fun getRandomSongs(limit: Int): List<Song> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getRandomSongs(limit, filter.allowedParentDirs, filter.applyFilter).map { it.toSong() }
    }

    override suspend fun getSongsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): List<Song> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getSongsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode(),
            limit = limit,
            offset = offset
        ).map { it.toSong() }
    }

    override suspend fun getAlbumsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter,
        minTracks: Int
    ): List<Album> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getAlbumsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode(),
            minTracks = minTracks,
            limit = limit,
            offset = offset
        ).map { it.toAlbum() }
    }

    override suspend fun getArtistsPage(
        limit: Int,
        offset: Int,
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): List<Artist> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getArtistsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode(),
            limit = limit,
            offset = offset
        ).map { it.toArtist() }
    }

    override suspend fun getFirstPlayableSong(): Song? = withContext(Dispatchers.IO) {
        val allowedDirs = userPreferencesRepository.allowedDirectoriesFlow.first()
        val blockedDirs = userPreferencesRepository.blockedDirectoriesFlow.first()
        val (allowedParentDirs, applyDirectoryFilter) =
            computeAllowedDirs(allowedDirs, blockedDirs)
        musicDao.getFirstPlayableSong(
            allowedParentDirs = allowedParentDirs,
            applyDirectoryFilter = applyDirectoryFilter
        )?.toSong()
    }

    override suspend fun saveTelegramSongs(songs: List<Song>) {
        val entities = songs.mapNotNull { it.toTelegramEntity() }
        if (entities.isNotEmpty()) {
            ensureTelegramDownloadSyncObserverStarted()
            telegramDao.insertSongs(entities)
            telegramRepository.warmUpArtworkForSongs(entities)
            // Trigger sync to update main DB
            androidx.work.WorkManager.getInstance(context).enqueue(
                com.aro.music.data.worker.SyncWorker.incrementalSyncWork()
            )
        }
    }

    override suspend fun replaceTelegramSongsForChannel(chatId: Long, songs: List<Song>) {
        val entities = songs.mapNotNull { it.toTelegramEntity() }.filter { it.chatId == chatId }
        ensureTelegramDownloadSyncObserverStarted()
        telegramDao.deleteSongsByChatId(chatId)
        if (entities.isNotEmpty()) {
            telegramDao.insertSongs(entities)
            telegramRepository.warmUpArtworkForSongs(entities)
        }
        // Sync into the unified `songs` table is triggered later by saveTelegramChannel().
        // We deliberately do NOT enqueue here: the SyncWorker gates Telegram processing on
        // an existing channel row (telegramDao.getAllChannels()), and on first add this
        // function runs BEFORE the channel entity is persisted. Triggering here would race
        // and skip the sync, leaving songs invisible until the next app launch.
    }

    /**
     * Compute allowed parent directories by subtracting blocked dirs from all known dirs.
     * Returns Pair(allowedDirs, applyFilter) for use with Room DAO filtered queries.
     */
    private suspend fun computeAllowedDirs(
        allowedDirs: Set<String>,
        blockedDirs: Set<String>
    ): Pair<List<String>, Boolean> {
        return DirectoryFilterUtils.computeAllowedParentDirs(
            allowedDirs = allowedDirs,
            blockedDirs = blockedDirs,
            getAllParentDirs = { musicDao.getDistinctParentDirectories() },
            normalizePath = ::normalizePath
        )
    }

    /** Synchronous version using the cached StateFlow — avoids duplicate preference reads. */
    private fun computeAllowedDirsSync(): Pair<List<String>, Boolean> {
        val cached = cachedDirFilter.value
        return cached.allowedParentDirs to cached.applyFilter
    }

    private fun StorageFilter.toFilterMode(): Int = when (this) {
        StorageFilter.ALL -> 0
        StorageFilter.OFFLINE -> 1
        StorageFilter.ONLINE -> 2
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAlbums(storageFilter: StorageFilter, minTracks: Int): Flow<List<Album>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            val (allowedParentDirs, applyFilter) = computeAllowedDirs(allowedDirs, blockedDirs)
            musicDao.getAlbums(allowedParentDirs, applyFilter, storageFilter.toFilterMode(), minTracks)
                .map { entities -> entities.map { it.toAlbum() } }
                .distinctUntilChanged()
        }.conflate().flowOn(Dispatchers.IO)
    }

    /**
     * Album metadata for the detail screen.
     *
     * An InnerTube album has no Room row until it is opened, so the header is
     * fetched from the album browse response and stored. This has to happen
     * independently of [getSongsForAlbum] because the detail screen combines
     * the two and renders an error whenever the album is missing — without this,
     * opening an album would flash "album not found" until the track fetch
     * happened to write the album row as a side effect.
     */
    override fun getAlbumById(id: Long): Flow<Album?> {
        val fromRoom = musicDao.getAlbumById(id).map { it?.toAlbum() }
        if (!InnerTubeIds.isAlbumId(id)) {
            return fromRoom.flowOn(Dispatchers.IO)
        }
        return flow {
            val cached = fromRoom.first()
            if (cached != null) {
                emitAll(fromRoom)
                return@flow
            }
            val browseId = innerTubeRepository.browseIdForAlbum(id)
            if (browseId == null) {
                Log.w("MusicRepository", "No browseId registered for album $id")
                emitAll(fromRoom)
                return@flow
            }
            try {
                val album = if (browseId.startsWith("spotify:")) {
                    streamingRepository.spotifyAlbum(browseId)
                } else {
                    innerTubeRepository.getAlbum(browseId)
                }
                if (album != null) {
                    persistRemoteAlbum(album)
                } else {
                    Log.w("MusicRepository", "Album fetch returned null for $id")
                }
            } catch (e: Exception) {
                Log.w("MusicRepository", "Album header fetch failed for $id: ${e.message}")
            }
            emitAll(fromRoom)
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Stores an album header, plus the artist row it points at.
     *
     * `Album` carries no artist id of its own, so the artist is keyed by the
     * album's own synthetic id space via [InnerTubeIds] to keep it distinct from
     * a local artist of the same name.
     */
    private suspend fun persistRemoteAlbum(album: Album) {
        try {
            val artistId = InnerTubeIds.artistId("album:${album.id}")
            musicDao.insertArtists(
                listOf(
                    com.aro.music.data.database.ArtistEntity(
                        id = artistId,
                        name = album.artist.ifBlank { album.albumArtist.orEmpty() },
                        trackCount = album.songCount,
                        imageUrl = null,
                        customImageUri = null,
                    )
                )
            )
            musicDao.insertAlbums(listOf(album.toEntity(artistId)))
        } catch (e: Exception) {
            Log.w("MusicRepository", "Could not persist album ${album.id}: ${e.message}")
        }
    }

    /**
     * Stores a remote artist (Spotify or InnerTube) to Room.
     */
    private suspend fun persistRemoteArtist(artist: Artist) {
        val cached = musicDao.getArtistById(artist.id).first()
        musicDao.insertArtists(listOf(
            com.aro.music.data.database.ArtistEntity(
                id = artist.id, name = artist.name,
                trackCount = maxOf(artist.songCount, cached?.trackCount ?: 0),
                imageUrl = artist.imageUrl ?: cached?.imageUrl,
                customImageUri = cached?.customImageUri,
            )
        ))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getArtists(storageFilter: StorageFilter): Flow<List<Artist>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            val (allowedParentDirs, applyFilter) = computeAllowedDirs(allowedDirs, blockedDirs)
            musicDao.getArtistsWithSongCountsFiltered(
                allowedParentDirs = allowedParentDirs,
                applyDirectoryFilter = applyFilter,
                filterMode = storageFilter.toFilterMode()
            )
                .distinctUntilChanged()
                .map { entities ->
                    val artists = entities.map { it.toArtist() }
                    // Trigger prefetch for missing images (non-blocking)
                    val missingImages = artists.missingImageCandidates()
                    if (missingImages.isNotEmpty()) {
                        // Cancel any in-flight prefetch before starting a new one — the flow
                        // can emit multiple times during sync, and concurrent launches would
                        // create N × artist-count coroutines simultaneously.
                        prefetchJob?.cancel()
                        prefetchJob = repositoryScope.launch {
                            artistImageRepository.prefetchArtistImages(missingImages)
                        }
                    }
                    artists
                }
        }.conflate().flowOn(Dispatchers.IO)
    }

    /**
     * Tracks of an album.
     *
     * A YouTube Music album has no local rows until it is opened, so an empty
     * Room result for an InnerTube id triggers a network fetch; the fetched
     * tracks are then written to Room, which means the album also becomes a real
     * row for the library tabs and for favourites. Local albums never enter this
     * branch because their ids are positive MediaStore ids.
     */
    override fun getSongsForAlbum(albumId: Long): Flow<List<Song>> {
        val fromRoom = musicDao.getSongsByAlbumId(albumId).map { entities ->
            entities.map { it.toSong() }.sortedBy { it.trackNumber }
        }
        if (!InnerTubeIds.isAlbumId(albumId)) {
            return fromRoom.flowOn(Dispatchers.IO)
        }
        return flow {
            val cached = fromRoom.first()
            if (cached.isNotEmpty()) {
                emitAll(fromRoom)
                return@flow
            }
            val fetched = fetchInnerTubeAlbumTracks(albumId)
            // Re-read from Room rather than emitting [fetched] directly, so the
            // favourite flag and any normalisation Room applies are consistent
            // with every other album in the app.
            emitAll(fromRoom)
            if (fetched.isEmpty()) {
                Log.w("MusicRepository", "InnerTube album $albumId returned no tracks")
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Fetches an InnerTube album's tracks and persists them with their album and
     * artist parent rows.
     */
    private suspend fun fetchInnerTubeAlbumTracks(albumId: Long): List<Song> {
        val browseId = innerTubeRepository.browseIdForAlbum(albumId) ?: run {
            Log.w("MusicRepository", "No browseId registered for album $albumId")
            return emptyList()
        }
        return try {
            val header = musicDao.getAlbumById(albumId).first()?.toAlbum()
            val fetched = if (browseId.startsWith("spotify:")) streamingRepository.spotifyAlbumSongs(browseId) else innerTubeRepository.getAlbumTracks(browseId)
            val tracks = fetched.mapIndexed { index, song ->
                song.copy(albumId = albumId, album = header?.title ?: song.album,
                    trackNumber = song.trackNumber.takeIf { it > 0 } ?: index + 1)
            }
            if (tracks.isNotEmpty()) {
                persistRemoteTracks(tracks)
            }
            tracks
        } catch (e: Exception) {
            Log.w("MusicRepository", "Album track fetch failed for $albumId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Writes network tracks to Room together with the album and artist rows
     * their foreign keys require.
     *
     * Grouped by album rather than assuming a single one, because a playlist
     * spans many albums while an album fetch does not. Rows are derived from the
     * tracks themselves, so they are self-consistent without needing the album
     * browse response. `insertAlbums`/`insertArtists` merge on conflict, so this
     * is safe to re-run.
     */
    override suspend fun cacheCatalogSongs(songs: List<Song>) = persistRemoteTracks(songs)

    private suspend fun persistRemoteTracks(tracks: List<Song>) {
        if (tracks.isEmpty()) return
        try {
            val artistRefs = tracks.flatMap { song ->
                song.artists + com.aro.music.data.model.ArtistRef(song.artistId, song.artist.ifBlank { "Unknown artist" }, true)
            }.distinctBy { it.id }
            // The database requires parents even for catalog rows without album
            // or artist metadata (their IDs are 0). Keep existing artist headers:
            // a track's combined byline must never rename the artist page.
            musicDao.insertArtistsIgnoreConflicts(artistRefs.map { artist ->
                com.aro.music.data.database.ArtistEntity(artist.id, artist.name, 0, null)
            })
            val byAlbum = tracks.groupBy { it.albumId }
            musicDao.insertAlbumsIgnoreConflicts(byAlbum.map { (albumId, albumTracks) ->
                val sample = albumTracks.first()
                com.aro.music.data.database.AlbumEntity(
                    id = albumId, title = sample.album.ifBlank { "Unknown album" },
                    artistName = sample.displayArtist, artistId = sample.artistId,
                    albumArtUriString = sample.albumArtUriString, songCount = albumTracks.size,
                    dateAdded = sample.dateAdded, year = sample.year, albumArtist = sample.albumArtist,
                )
            })
            musicDao.insertSongs(tracks.map { it.toEntityWithoutPaths() })
            val refs = tracks.flatMap { song ->
                val artists = (song.artists + com.aro.music.data.model.ArtistRef(song.artistId, song.artist, true)).distinctBy { it.id }
                artists.map { artist -> com.aro.music.data.database.SongArtistCrossRef(song.id.toLong(), artist.id, artist.id == song.artistId) }
            }
            musicDao.insertSongArtistCrossRefs(refs)

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("MusicRepository", "Could not persist remote tracks: ${e.message}")
        }
    }

    override fun getArtistById(artistId: Long): Flow<Artist?> {
        val fromRoom = musicDao.getArtistById(artistId).map { it?.toArtist() }
        if (!InnerTubeIds.isArtistId(artistId)) {
            return fromRoom.flowOn(Dispatchers.IO)
        }
        return flow {
            val cached = fromRoom.first()
            if (cached != null) {
                emitAll(fromRoom)
                return@flow
            }
            val browseId = innerTubeRepository.browseIdForArtist(artistId)
            if (browseId == null) {
                Log.w("MusicRepository", "No browseId registered for artist $artistId")
                emitAll(fromRoom)
                return@flow
            }
            try {
                val artist = if (browseId.startsWith("spotify:")) {
                    streamingRepository.spotifyArtist(browseId)
                } else {
                    innerTubeRepository.getArtist(browseId)
                }
                if (artist != null) {
                    persistRemoteArtist(artist)
                } else {
                    Log.w("MusicRepository", "Artist fetch returned null for $artistId")
                }
            } catch (e: Exception) {
                Log.w("MusicRepository", "Artist fetch failed for $artistId: ${e.message}")
            }
            emitAll(fromRoom)
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getArtistIdByName(name: String): Long? = withContext(Dispatchers.IO) {
        musicDao.getArtistIdByName(name)
    }

    override fun getArtistsForSong(songId: Long): Flow<List<Artist>> {
        return musicDao.getArtistsForSong(songId)
            .map { entities -> entities.map { it.toArtist() } }
            .distinctUntilChanged()
            .onEach { artists ->
                val missingImages = artists.missingImageCandidates()
                if (missingImages.isNotEmpty()) {
                    val isNewSong = currentSongArtistPrefetchSongId != songId
                    if (isNewSong) {
                        currentSongArtistPrefetchJob?.cancel()
                        currentSongArtistPrefetchSongId = songId
                    } else if (currentSongArtistPrefetchJob?.isActive == true) {
                        // Room re-emits as artist rows are updated; keep the current song batch
                        // alive so one successful image write does not cancel the remaining fetches.
                        return@onEach
                    }

                    currentSongArtistPrefetchJob = repositoryScope.launch {
                        artistImageRepository.prefetchArtistImages(missingImages)
                    }
                }
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Songs of an artist.
     *
     * Mirrors [getSongsForAlbum]: an InnerTube artist id with no local rows is
     * filled from the network on first open, then behaves like any other artist.
     */
    override fun getSongsForArtist(artistId: Long): Flow<List<Song>> {
        val fromRoom = musicDao.getSongsForArtist(artistId).map { entities ->
            entities.map { it.toSong() }
        }
        if (!InnerTubeIds.isArtistId(artistId)) {
            return fromRoom.flowOn(Dispatchers.IO)
        }
        return flow {
            val cached = fromRoom.first()
            if (cached.isNotEmpty()) {
                emitAll(fromRoom)
                return@flow
            }
            val browseId = innerTubeRepository.browseIdForArtist(artistId)
            if (browseId == null) {
                Log.w("MusicRepository", "No browseId registered for artist $artistId")
                emitAll(fromRoom)
                return@flow
            }
            try {
                val header = musicDao.getArtistById(artistId).first()
                val fetched = if (browseId.startsWith("spotify:")) streamingRepository.spotifyArtistSongs(browseId) else innerTubeRepository.getArtistSongs(browseId)
                val tracks = fetched.map { song ->
                    if (song.artists.any { it.id == artistId }) song else song.copy(artists = song.artists +
                        com.aro.music.data.model.ArtistRef(artistId, header?.name ?: song.artist, song.artistId == artistId))
                }
                if (tracks.isNotEmpty()) {
                    persistRemoteTracks(tracks)
                } else {
                    Log.w("MusicRepository", "InnerTube artist $artistId returned no songs")
                }
            } catch (e: Exception) {
                Log.w("MusicRepository", "Artist song fetch failed for $artistId: ${e.message}")
            }
            emitAll(fromRoom)
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getAllUniqueAudioDirectories(): Set<String> = withContext(Dispatchers.IO) {
        // Local MediaStore scanning disabled — streaming-only mode.
        LogUtils.d(this, "getAllUniqueAudioDirectories: local scan disabled, returning empty")
        emptySet()
    }

    override fun getAllUniqueAlbumArtUris(): Flow<List<Uri>> {
        return musicDao.getAllUniqueAlbumArtUrisFromSongs().map { uriStrings ->
            uriStrings.mapNotNull { it.toUri() }
        }.flowOn(Dispatchers.IO)
    }

    // --- Métodos de Búsqueda ---

    override fun searchSongs(query: String, titleOnly: Boolean): Flow<List<Song>> {
        if (query.isBlank()) return flowOf(emptyList())
        // Bypass DB entirely — fetch directly from the streaming backends.
        return flow {
            val songs = streamingRepository.searchSongs(query = query, limit = SEARCH_RESULTS_LIMIT)
            emit(songs.withFavoriteState())
        }.flowOn(Dispatchers.IO)
    }


    override suspend fun getSearchSuggestions(query: String): Pair<List<String>, List<Song>> {
        if (query.isBlank()) return emptyList<String>() to emptyList()
        return streamingRepository.getSuggestions(query)
    }

    override fun searchAlbums(query: String, minTracks: Int): Flow<List<Album>> {
        if (query.isBlank()) return flowOf(emptyList())
        return flow {
            val albums = streamingRepository.searchAlbums(query = query)
            musicDao.insertAlbumsIgnoreConflicts(albums.map { it.toEntity(0L) })
            emit(albums)
        }.flowOn(Dispatchers.IO)
    }

    override fun searchArtists(query: String): Flow<List<Artist>> {
        if (query.isBlank()) return flowOf(emptyList())
        return flow {
            val artists = streamingRepository.searchArtists(query = query)
            artists.forEach { persistRemoteArtist(it) }
            emit(artists)
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Playlists matching [query] — the user's own, plus YouTube Music's.
     *
     * The search screen resolves a playlist row by observing `songIds`
     * immediately, so remote rows have to carry a real track list rather than
     * being handed over empty (which the UI would render as an empty playlist).
     * That costs one browse request per playlist, so it is bounded by
     * [REMOTE_PLAYLIST_RESOLVE_LIMIT] and the requests run concurrently — doing
     * them in sequence added seconds to every search.
     *
     * User playlists are listed first and win name ties, because they are the
     * ones the user can actually edit.
     */
    override suspend fun searchPlaylists(query: String): List<Playlist> {
        if (query.isBlank()) return emptyList()

        val local = playlistPreferencesRepository.userPlaylistsFlow.first()
            .filter { playlist -> playlist.name.contains(query, ignoreCase = true) }

        val remote = try {
            withContext(Dispatchers.IO) {
                val candidates = innerTubeRepository
                    .searchPlaylists(query = query)
                    .take(REMOTE_PLAYLIST_RESOLVE_LIMIT)

                coroutineScope {
                    candidates.map { candidate ->
                        async {
                            runCatching {
                                innerTubeRepository.resolveRemotePlaylist(
                                    browseId = candidate.browseId,
                                    title = candidate.title,
                                    artworkUrl = candidate.artworkUrl,
                                )
                            }.getOrNull()
                        }
                    }.awaitAll()
                }
                    .filterNotNull()
                    .filter { it.songs.isNotEmpty() }
                    .onEach {
                        // The search screen resolves the row through
                        // `getSongsByIds`, which reads Room — so the tracks have
                        // to be stored, not just carried on the model.
                        persistRemoteTracks(it.songs)
                    }
                    .map { it.playlist }
            }
        } catch (e: Exception) {
            Log.w("MusicRepository", "Remote playlist search failed for '$query': ${e.message}")
            emptyList()
        }

        val spotifyPlaylists = streamingRepository.spotifySearchPlaylists(query).onEach { persistRemoteTracks(it.songs) }.map { it.playlist }
        val allRemote = spotifyPlaylists + remote
        if (allRemote.isEmpty()) return local
        val localNames = local.map { it.name.lowercase() }.toSet()
        return local + allRemote.filterNot { it.name.lowercase() in localNames }
    }

    override fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>> {
        if (query.isBlank()) return flowOf(emptyList())
        val playlistsFlow = flow { emit(searchPlaylists(query)) }

        return combine(
            userPreferencesRepository.minTracksPerAlbumFlow
        ) { (minTracks) ->
            minTracks
        }.flatMapLatest { minTracks ->
            when (filterType) {
                SearchFilterType.ALL -> {
                    combine(
                        searchSongs(query),
                        searchAlbums(query, minTracks),
                        searchArtists(query),
                        playlistsFlow
                    ) { songs, albums, artists, playlists ->
                        mutableListOf<SearchResultItem>().apply {
                            songs.forEach { add(SearchResultItem.SongItem(it)) }
                            albums.forEach { add(SearchResultItem.AlbumItem(it)) }
                            artists.forEach { add(SearchResultItem.ArtistItem(it)) }
                            playlists.forEach { add(SearchResultItem.PlaylistItem(it)) }
                        }
                    }
                }
                SearchFilterType.SONGS -> searchSongs(query, titleOnly = true).map { songs -> songs.map { SearchResultItem.SongItem(it) } }
                SearchFilterType.ALBUMS -> searchAlbums(query, minTracks).map { albums -> albums.map { SearchResultItem.AlbumItem(it) } }
                SearchFilterType.ARTISTS -> searchArtists(query).map { artists -> artists.map { SearchResultItem.ArtistItem(it) } }
                SearchFilterType.PLAYLISTS -> playlistsFlow.map { playlists -> playlists.map { SearchResultItem.PlaylistItem(it) } }
            }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun addSearchHistoryItem(query: String) {
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
            searchHistoryDao.insert(SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis()))
        }
    }

    override suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem> {
        return withContext(Dispatchers.IO) {
            searchHistoryDao.getRecentSearches(limit).map { it.toSearchHistoryItem() }
        }
    }

    override suspend fun deleteSearchHistoryItemByQuery(query: String) {
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
        }
    }

    override suspend fun clearSearchHistory() {
        withContext(Dispatchers.IO) {
            searchHistoryDao.clearAll()
        }
    }

    override fun getMusicByGenre(genreId: String): Flow<List<Song>> {
        return userPreferencesRepository.mockGenresEnabledFlow.flatMapLatest { mockEnabled ->
            flow {
                val genreName = if (mockEnabled) "Mock" else genreId
                // Use the same playable catalog as search and home. The legacy
                // genre service hid provider failures behind an empty list.
                val songs = if (!genreName.equals("unknown", ignoreCase = true) && !mockEnabled) {
                    val key = genreName.trim().lowercase(java.util.Locale.ROOT)
                    val cached = genreCacheMutex.withLock {
                        genreCache[key]?.takeIf { System.currentTimeMillis() - it.first in 0 until 6 * 60 * 60_000L }?.second
                    }
                    cached ?: innerTubeRepository.searchSongs("${genreName.trim()} songs", limit = 50).also { fetched ->
                        if (fetched.isNotEmpty()) genreCacheMutex.withLock {
                            genreCache[key] = System.currentTimeMillis() to fetched
                            if (genreCache.size > 24) genreCache.remove(genreCache.keys.first())
                        }
                    }
                } else {
                    emptyList()
                }
                emit(songs)
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getSongsByIds(songIds: List<String>): Flow<List<Song>> {
        if (songIds.isEmpty()) return flowOf(emptyList())
        val longIds = songIds.mapNotNull { it.toLongOrNull() }
        if (longIds.isEmpty()) return flowOf(emptyList())
        val ids = longIds.distinct()
        val rows = if (ids.size <= 500) musicDao.getSongsByIds(ids, emptyList(), false)
        else combine(ids.chunked(500).map { chunk -> musicDao.getSongsByIds(chunk, emptyList(), false) }) { batches -> batches.flatMap { it } }
        return rows.map { entities ->
            val songMap = entities.associate { it.id.toString() to it.toSong() }
            // Preserve source order, including across database query batches.
            songIds.mapNotNull { songMap[it] }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getSongByPath(path: String): Song? {
        return withContext(Dispatchers.IO) {
            musicDao.getSongByPath(path)?.toSong()
        }
    }

    override suspend fun invalidateCachesDependentOnAllowedDirectories() {
        Log.i("MusicRepo", "invalidateCachesDependentOnAllowedDirectories called. Reactive flows will update automatically.")
    }

    // Implementación de las nuevas funciones suspend para carga única
    override suspend fun getAllSongsOnce(): List<Song> = withContext(Dispatchers.IO) {
        val allowedDirs = userPreferencesRepository.allowedDirectoriesFlow.first()
        val blockedDirs = userPreferencesRepository.blockedDirectoriesFlow.first()
        val (allowedParentDirs, applyDirectoryFilter) =
            computeAllowedDirs(allowedDirs, blockedDirs)
        musicDao.getLibrarySongs(
            allowedParentDirs = allowedParentDirs,
            applyDirectoryFilter = applyDirectoryFilter
        ).first().map { it.toSong() }.withFavoriteState()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getDistinctAlbumArtSongs(): Flow<List<Song>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    musicDao.getDistinctAlbumArtSongs(
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter
                    )
                )
            }.flatMapLatest { it }
        }.map { entities ->
            entities.map { it.toSong() }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getHomeMixPreviewSongs(limit: Int): Flow<List<Song>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            kotlinx.coroutines.flow.channelFlow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)

                launch {
                    musicDao.getHomeMixPreviewSongs(
                        limit = limit, allowedParentDirs = allowedParentDirs, applyDirectoryFilter = applyDirectoryFilter
                    ).collect { entities -> send(entities.map { it.toSong() }) }
                }
                // The home mix is served from Room, so the fetched tracks have to
                // be written there before it can show anything. This call used to
                // discard its result and read the table straight away, which left
                // the feed permanently blank even though the fetch succeeded.
                // Room flows are reactive, so persisting here is enough — the
                // query below re-emits on its own.
                val fetched = try {
                    streamingRepository.getTrendingSongs(limit = HOME_PREVIEW_FETCH_LIMIT)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w("MusicRepository", "Home preview fetch failed: ${e.message}")
                    emptyList()
                }
                if (fetched.isNotEmpty()) {
                    persistRemoteTracks(fetched)
                }

            }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    override suspend fun getAllAlbumsOnce(storageFilter: StorageFilter, minTracks: Int): List<Album> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getAlbumsPage(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = SortOption.AlbumTitleAZ.storageKey,
            filterMode = storageFilter.toFilterMode(),
            minTracks = minTracks,
            limit = Int.MAX_VALUE,
            offset = 0
        ).map { it.toAlbum() }
    }

    override suspend fun getAllArtistsOnce(): List<Artist> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getArtistsWithSongCountsFiltered(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            filterMode = StorageFilter.ALL.toFilterMode()
        ).first().map { it.toArtist() }
    }

    override suspend fun setFavoriteStatus(songId: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        val id = songId.toLongOrNull() ?: return@withContext
        if (isFavorite) {
            favoritesDao.setFavorite(
                com.aro.music.data.database.FavoritesEntity(
                    songId = id,
                    isFavorite = true
                )
            )
        } else {
            favoritesDao.removeFavorite(id)
        }
    }

    override suspend fun setFavoriteStatusWithSong(song: com.aro.music.data.model.Song, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        val id = song.id.toLongOrNull() ?: return@withContext
        if (isFavorite) {
            // Persist parents and artist relations before saving a search result.
            persistRemoteTracks(listOf(song))
            favoritesDao.setFavorite(
                com.aro.music.data.database.FavoritesEntity(
                    songId = id,
                    isFavorite = true
                )
            )
        } else {
            favoritesDao.removeFavorite(id)
        }
    }

    /**
     * A single playlist by id, from either store.
     *
     * The playlist detail screen used to look only in the user's playlists, so
     * opening a YouTube Music playlist from search landed on "not found". Remote
     * playlists are fetched here and their tracks written to Room, which is what
     * `getSongsByIds` then reads to render the detail screen.
     */
    override suspend fun getPlaylistById(playlistId: String): Playlist? {
        playlistPreferencesRepository.userPlaylistsFlow.first()
            .find { it.id == playlistId }
            ?.let { return it }

        if (playlistId.startsWith("spotify:")) {
            val resolved = streamingRepository.spotifyPlaylist(playlistId) ?: return null
            persistRemoteTracks(resolved.songs)
            return resolved.playlist
        }
        if (!isRemotePlaylistId(playlistId)) return null

        return try {
            val resolved = innerTubeRepository.getRemotePlaylist(playlistId) ?: run {
                Log.w("MusicRepository", "Remote playlist $playlistId not found")
                return null
            }
            persistRemoteTracks(resolved.songs)
            resolved.playlist
        } catch (e: Exception) {
            Log.w("MusicRepository", "Remote playlist fetch failed for $playlistId: ${e.message}")
            null
        }
    }

    /**
     * True for a YouTube Music playlist browseId.
     *
     * `VL` is the browse prefix for playlists (`VLPL…` for user playlists,
     * `VLOL…` for auto-mixes), and generated local playlist ids are UUID-like,
     * so this cannot misidentify one of the user's own playlists.
     */
    private fun isRemotePlaylistId(playlistId: String): Boolean =
        playlistId.startsWith("VL", ignoreCase = true)

    override suspend fun getFavoriteSongIdsOnce(): Set<String> = withContext(Dispatchers.IO) {
        favoritesDao.getFavoriteSongIdsOnce()
            .map { it.toString() }
            .toSet()
    }

    override fun getFavoriteSongIdsFlow(): Flow<Set<String>> {
        return favoritesDao.getFavoriteSongIds()
            .map { ids -> ids.asSequence().map(Long::toString).toSet() }
            .distinctUntilChanged()
    }

    override suspend fun toggleFavoriteStatus(songId: String): Boolean = withContext(Dispatchers.IO) {
        val id = songId.toLongOrNull() ?: return@withContext false
        val isFav = favoritesDao.isFavorite(id) ?: false
        val newFav = !isFav
        setFavoriteStatus(songId, newFav)
        return@withContext newFav
    }

    override fun getSong(songId: String): Flow<Song?> {
        val longId = songId.toLongOrNull()
        return if (longId != null) {
            musicDao.getSongById(longId).map { it?.toSong() }.flowOn(Dispatchers.IO)
        } else {
            combine(
                telegramDao.getSongsByIds(listOf(songId)),
                telegramDao.getAllChannels()
            ) { songs, channels ->
                val channelMap = channels.associateBy { it.chatId }
                songs.firstOrNull()?.let {
                    it.toSong(channelTitle = channelMap[it.chatId]?.title)
                }
            }.flowOn(Dispatchers.IO)
        }
    }

    // Hardcoded fallback genres shown even when DB is empty (streaming-only mode).
    // These match the languages JioSaavn songs use in the genre/language field.
    private val FALLBACK_GENRES = listOf(
        "Hindi", "English", "Punjabi", "Tamil", "Telugu",
        "Bengali", "Marathi", "Gujarati", "Kannada", "Malayalam",
        "Pop", "Bollywood", "Devotional", "Lofi"
    )

    override fun getGenres(): Flow<List<Genre>> {
        // Emit fallback genres immediately so the UI never shows "No genres available"
        // while waiting for DB/prefs to initialize (streaming-only mode has empty DB).
        val fallbackImmediately: List<Genre> = FALLBACK_GENRES.map { buildGenre(it) }
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)

                emit(
                    combine(
                        musicDao.getUniqueGenres(
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        ),
                        musicDao.hasUnknownGenre(
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        )
                    ) { genreNames, hasUnknown ->
                        val knownGenres = genreNames
                            .asSequence()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .map { buildGenre(it) }
                            .distinctBy { it.id }
                            .toList()
                        // Merge DB genres with fallback genres; DB genres take priority
                        val dbGenreIds = knownGenres.map { it.id }.toSet()
                        val fallbackGenres = FALLBACK_GENRES
                            .map { buildGenre(it) }
                            .filter { it.id !in dbGenreIds }
                        val allGenres = (knownGenres + fallbackGenres)
                            .distinctBy { it.id }
                            .sortedBy { it.name.lowercase() }
                        val unknownAlreadyPresent = allGenres.any { it.id == UNKNOWN_GENRE_ID }
                        if (hasUnknown && !unknownAlreadyPresent) {
                            allGenres + buildGenre(UNKNOWN_GENRE_NAME)
                        } else {
                            allGenres
                        }
                    }
                )
            }.flatMapLatest { it }
        }.onStart { emit(fallbackImmediately) }
        .conflate().flowOn(Dispatchers.IO)
    }

    private fun buildGenre(genreName: String): Genre {
        val id = if (genreName.equals(UNKNOWN_GENRE_NAME, ignoreCase = true)) {
            UNKNOWN_GENRE_ID
        } else {
            genreName
                .lowercase()
                .replace(" ", "_")
                .replace("/", "_")
        }
        val lightThemeColor = GenreThemeUtils.getGenreThemeColor(id, isDark = false)
        val darkThemeColor = GenreThemeUtils.getGenreThemeColor(id, isDark = true)
        return Genre(
            id = id,
            name = genreName,
            lightColorHex = lightThemeColor.container.toHexString(),
            onLightColorHex = lightThemeColor.onContainer.toHexString(),
            darkColorHex = darkThemeColor.container.toHexString(),
            onDarkColorHex = darkThemeColor.onContainer.toHexString()
        )
    }

    override suspend fun getLyrics(
        song: Song,
        sourcePreference: LyricsSourcePreference,
        forceRefresh: Boolean
    ): Lyrics? {
        return lyricsRepository.getLyrics(song, sourcePreference, forceRefresh)
    }

    override suspend fun getStoredLyrics(song: Song): Pair<Lyrics, String>? {
        return lyricsRepository.getStoredLyrics(song)
    }

    /**
     * Obtiene la letra de una canción desde la API de LRCLIB, la persiste en la base de datos
     * y la devuelve como un objeto Lyrics parseado.
     *
     * @param song La canción para la cual se buscará la letra.
     * @return Un objeto Result que contiene el objeto Lyrics si se encontró, o un error.
     */
    override suspend fun getLyricsFromRemote(song: Song): Result<Pair<Lyrics, String>> {
        return lyricsRepository.fetchFromRemote(song)
    }

    override suspend fun searchRemoteLyrics(song: Song): Result<Pair<String, List<LyricsSearchResult>>> {
        return lyricsRepository.searchRemote(song)
    }

    override suspend fun searchRemoteLyricsByQuery(title: String, artist: String?): Result<Pair<String, List<LyricsSearchResult>>> {
        return lyricsRepository.searchRemoteByQuery(title, artist)
    }

    override suspend fun updateLyrics(songId: Long, lyrics: String) {
        lyricsRepository.updateLyrics(songId, lyrics)
    }

    override suspend fun resetLyrics(songId: Long) {
        lyricsRepository.resetLyrics(songId)
    }

    override suspend fun resetAllLyrics() {
        lyricsRepository.resetAllLyrics()
    }

    override fun getMusicFolders(storageFilter: StorageFilter): Flow<List<MusicFolder>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow,
            userPreferencesRepository.isFolderFilterActiveFlow,
            userPreferencesRepository.foldersSourceFlow
        ) { allowedDirs, blockedDirs, isFolderFilterActive, folderSource ->
            FolderFlowConfig(
                allowedDirs = allowedDirs,
                blockedDirs = blockedDirs,
                isFolderFilterActive = isFolderFilterActive,
                folderSource = folderSource
            )
        }.flatMapLatest { config ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) = computeAllowedDirs(
                    allowedDirs = config.allowedDirs,
                    blockedDirs = config.blockedDirs
                )
                emit(
                    musicDao.getFolderSongs(
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter,
                        filterMode = storageFilter.toFilterMode()
                    ).map { folderSongs ->
                        folderTreeBuilder.buildFolderTree(
                            folderSongs = folderSongs,
                            allowedDirs = config.allowedDirs,
                            blockedDirs = config.blockedDirs,
                            isFolderFilterActive = config.isFolderFilterActive,
                            folderSource = config.folderSource,
                            context = context
                        )
                    }
                )
            }.flatMapLatest { it }
        }.conflate().flowOn(Dispatchers.IO)
    }

    private data class FolderFlowConfig(
        val allowedDirs: Set<String>,
        val blockedDirs: Set<String>,
        val isFolderFilterActive: Boolean,
        val folderSource: FolderSource
    )

    override suspend fun deleteById(id: Long) {
        musicDao.deleteById(id)
    }

    override suspend fun clearTelegramData() {
        // Delete all Telegram playlists from app playlists
        val allChannels = telegramDao.getAllChannels().first()
        allChannels.forEach { channel ->
            telegramRepository.deleteAppPlaylistForTelegramChannel(channel.chatId)
            telegramRepository.deleteAllTopicPlaylistsForChannel(channel.chatId)
        }

        musicDao.clearAllTelegramSongs()
        telegramDao.clearAll()
        // Clear all Telegram caches (TDLib files, embedded art, memory)
        telegramRepository.clearMemoryCache()
        telegramCacheManager.clearAllCache()
    }

    override suspend fun saveTelegramChannel(channel: TelegramChannelEntity) {
        telegramDao.insertChannel(channel)

        // Create or update the corresponding app playlist.
        // Forum channels use per-topic playlists (managed by replaceTelegramSongsForTopic),
        // so we skip the flat channel-level playlist when topics exist.
        try {
            val topics = withContext(Dispatchers.IO) {
                telegramDao.getTopicsByChannelOnce(channel.chatId)
            }
            if (topics.isEmpty()) {
                // Flat (non-forum) channel: create/update a single channel playlist
                val channelSongs = withContext(Dispatchers.IO) {
                    telegramDao.getSongsByChatId(channel.chatId)
                }
                telegramRepository.updateAppPlaylistForTelegramChannel(
                    channel.chatId,
                    channel.title,
                    channelSongs
                )
            }
            // Forum channels: topic playlists are managed by replaceTelegramSongsForTopic
        } catch (e: Exception) {
            Log.e("MusicRepo", "Failed to update app playlist for Telegram channel ${channel.chatId}", e)
        }

        // Trigger the unified-library sync now that the channel row exists. SyncWorker's
        // Telegram phase is gated on telegramDao.getAllChannels() being non-empty, so this
        // is the earliest moment the sync can succeed. KEEP (not REPLACE) ensures we never
        // cancel a heavier full/rebuild that might be running under the same unique name.
        requestTelegramUnifiedSync()
    }

    override fun getAllTelegramChannels(): Flow<List<TelegramChannelEntity>> {
        return telegramDao.getAllChannels()
    }

    override suspend fun deleteTelegramChannel(chatId: Long) {
        musicDao.clearTelegramSongsForChat(chatId)
        telegramDao.deleteSongsByChatId(chatId)
        telegramDao.deleteTopicsByChannel(chatId)     // NEW: remove topics
        telegramDao.deleteChannel(chatId)
        telegramRepository.deleteAppPlaylistForTelegramChannel(chatId)
        telegramRepository.deleteAllTopicPlaylistsForChannel(chatId)  // NEW: remove topic playlists
    }

    override suspend fun saveTelegramTopics(chatId: Long, topics: List<TelegramTopicEntity>) {
        telegramDao.insertTopics(topics)
    }

    override suspend fun replaceTopicsForChannel(
        chatId: Long,
        freshTopics: List<TelegramTopicEntity>
    ) {
        val existingTopics = telegramDao.getTopicsByChannelOnce(chatId)
        val freshThreadIds = freshTopics.map { it.threadId }.toSet()

        // Delete topics that are no longer returned by Telegram
        val removedTopics = existingTopics.filter { it.threadId !in freshThreadIds }
        for (removed in removedTopics) {
            // Delete their songs from the telegram_songs table
            telegramDao.deleteSongsByTopicId(chatId, removed.threadId)
            // Delete their songs from the main music DB
            musicDao.clearTelegramSongsForTopic(chatId, removed.threadId)
            // Delete their playlist from the app playlist store
            telegramRepository.deleteAppPlaylistForTopic(chatId, removed.threadId)
            // Delete the topic row itself
            telegramDao.deleteTopic(removed.id)
        }

        // Insert/update the fresh topic list
        if (freshTopics.isNotEmpty()) {
            telegramDao.insertTopics(freshTopics)
        }
    }

    override suspend fun getTopicsForChannel(chatId: Long): List<TelegramTopicEntity> {
        return telegramDao.getTopicsByChannelOnce(chatId)
    }
    override fun getAllTelegramTopics(): Flow<List<TelegramTopicEntity>> {
        return telegramDao.getAllTopics()
    }

    override suspend fun replaceTelegramSongsForTopic(
        chatId: Long,
        threadId: Long,
        topicName: String,
        songs: List<Song>
    ) {
        // Stamp each song entity with the threadId before inserting
        val entities = songs.mapNotNull { it.toTelegramEntityWithThread(threadId) }
            .filter { it.chatId == chatId }

        ensureTelegramDownloadSyncObserverStarted()
        telegramDao.deleteSongsByTopicId(chatId, threadId)
        if (entities.isNotEmpty()) {
            telegramDao.insertSongs(entities)
            telegramRepository.warmUpArtworkForSongs(entities)
        }

        // Create/update the per-topic app playlist
        telegramRepository.updateAppPlaylistForTopic(chatId, threadId, topicName, entities)

        // Best-effort sync trigger: if no worker is in flight yet, KEEP enqueues a fresh
        // incremental run that will catch the rows being committed during the topic
        // loop. This is the safety net for forum flows that fail mid-loop and never
        // reach the final saveTelegramChannel() call (the dashboard's syncForumChannel
        // path in particular — its only sync trigger is the end-of-flow channel save).
        // Subsequent topic insertions are no-ops here because the existing worker is
        // running; the calling VM's finally block ensures a follow-up sync runs
        // afterwards to pick up rows added past the worker's Telegram phase.
        requestTelegramUnifiedSync()
    }

    override suspend fun getSongIdsSorted(
        sortOption: SortOption,
        storageFilter: com.aro.music.data.model.StorageFilter
    ): List<Long> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getSongIdsSorted(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode()
        )
    }

    override suspend fun getFavoriteSongIdsSorted(
        sortOption: SortOption,
        storageFilter: com.aro.music.data.model.StorageFilter
    ): List<Long> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getFavoriteSongIdsSorted(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode()
        )
    }

    override suspend fun getSongIdByContentUri(contentUri: String): Long? =
        withContext(Dispatchers.IO) {
            musicDao.getSongIdByContentUri(contentUri)
        }

    override fun requestTelegramUnifiedSync() {
        // KEEP — never disturb a full/rebuild sync that shares this unique work name.
        // If no worker is queued or running, this enqueues a fresh incremental run.
        // If one is already in flight, we let it complete; its Telegram phase reads
        // telegram_songs at run time and will pick up rows committed before then.
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            com.aro.music.data.worker.SyncWorker.WORK_NAME,
            androidx.work.ExistingWorkPolicy.KEEP,
            com.aro.music.data.worker.SyncWorker.incrementalSyncWork()
        )
    }
}
