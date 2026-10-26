package com.aro.music.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aro.music.data.database.MusicDao
import com.aro.music.data.database.AlbumEntity
import com.aro.music.data.database.toEntity
import com.aro.music.data.innertube.HomeFeedCache
import com.aro.music.data.innertube.HomeCatalogItem
import com.aro.music.data.innertube.HomeCatalogSection
import com.aro.music.data.innertube.InnerTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject

data class HomeFeedState(
    val personalReady: Boolean = false,
    val sections: List<HomeCatalogSection> = emptyList(),
    val personalSections: List<HomeCatalogSection> = emptyList(),
    val pinnedSongIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val continuation: String? = null,
    val canRetry: Boolean = false,
)

@HiltViewModel
class HomeFeedViewModel @Inject constructor(
    private val repository: InnerTubeRepository,
    private val musicDao: MusicDao,
    private val musicRepository: com.aro.music.data.repository.MusicRepository,
    private val homeLayoutPreferences: com.aro.music.data.preferences.HomeLayoutPreferences,
    private val statsRepository: com.aro.music.data.stats.PlaybackStatsRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    val homeLayout = homeLayoutPreferences.layout.stateIn(viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), com.aro.music.data.preferences.HomeLayout())
    fun saveHomeLayout(layout: com.aro.music.data.preferences.HomeLayout) {
        viewModelScope.launch { homeLayoutPreferences.save(layout) }
    }
    private val _state = MutableStateFlow(HomeFeedState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private val seenTokens = mutableSetOf<String>()

    private val preferences = context.getSharedPreferences("home_speed_dial", android.content.Context.MODE_PRIVATE)
    private var personalJob: Job? = null
    private val personalizationMutex = Mutex()
    @Volatile private var activeSong: com.aro.music.data.model.Song? = null
    private var lastSeed: String? = null
    private val cache = HomeFeedCache(context)
    private var fetchedAt = 0L
    @Volatile private var cacheReady = false

    init {
        _state.update { it.copy(pinnedSongIds = preferences.getStringSet("pins", emptySet()).orEmpty().toSet()) }
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = cache.read()
            if (snapshot != null) {
                fetchedAt = snapshot.fetchedAt
                lastSeed = snapshot.seed
                _state.update { it.copy(sections = snapshot.sections.map { row -> row.toCatalog() },
                    personalSections = snapshot.recommendations.map { row -> row.toCatalog() }) }
            }
            cacheReady = true
            updatePersonalFeed()
            if (snapshot?.isFresh() != true) refresh() else updatePersonalFeed(bootstrapRecommendations = true)
        }
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.flow.combine(statsRepository.refreshFlow, musicRepository.getFavoriteSongIdsFlow()) { _, _ -> Unit }
                .collect { updatePersonalFeed() }
        }
    }

    fun onSongPlayed(song: com.aro.music.data.model.Song) {
        if (activeSong?.id == song.id) return
        activeSong = song
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.cacheCatalogSongs(listOf(song))
            updatePersonalFeed(bootstrapRecommendations = true)
        }
    }

    fun toggleSpeedDial(song: com.aro.music.data.model.Song) {
        val pins = _state.value.pinnedSongIds.toMutableSet()
        if (!pins.add(song.id)) pins.remove(song.id)
        preferences.edit().putStringSet("pins", pins).apply()
        _state.update { it.copy(pinnedSongIds = pins) }
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.cacheCatalogSongs(listOf(song))
            updatePersonalFeed()
        }
    }

    private suspend fun updatePersonalFeed(force: Boolean = false, bootstrapRecommendations: Boolean = false): Unit = personalizationMutex.withLock {
        val events = statsRepository.loadPlaybackHistory(1000)
        val history = events.map { it.songId }.distinct().take(8)
        val frequentIds = events.groupBy { it.songId }.entries
            .filter { it.value.size >= 2 && it.key !in history }
            .sortedWith(compareByDescending<Map.Entry<String, List<com.aro.music.data.stats.PlaybackStatsRepository.PlaybackHistoryEntry>>> { it.value.size }
                .thenByDescending { it.value.maxOf { event -> event.timestamp } })
            .take(50).map { it.key }
        val favorites = musicRepository.getFavoriteSongsOnce(com.aro.music.data.model.StorageFilter.ALL)
        val ids = (_state.value.pinnedSongIds + listOfNotNull(activeSong?.id) + history + frequentIds + favorites.take(100).map { it.id }).distinct()
        val rows = musicRepository.getSongsByIds(ids).first()
        val lookup = (rows + listOfNotNull(activeSong)).associateBy { it.id }
        val recent = history.mapNotNull { lookup[it] }
        val quickPicks = (_state.value.personalSections.filter { it.title == context.getString(com.aro.music.R.string.home_listening_recommendations) } + _state.value.sections)
            .flatMap { it.items }.filterIsInstance<HomeCatalogItem.Track>().map { it.song }
        val dial = homeSpeedDial(_state.value.pinnedSongIds.sorted().mapNotNull { lookup[it] }, history.toSet(),
            favorites, frequentIds.mapNotNull { lookup[it] }, quickPicks)
        val base = buildList {
            if (recent.isNotEmpty()) add(HomeCatalogSection("Recently played", null, recent.map { HomeCatalogItem.Track(it) }))
            if (dial.isNotEmpty()) add(HomeCatalogSection(context.getString(com.aro.music.R.string.home_speed_dial), context.getString(com.aro.music.R.string.home_speed_dial_quick_hint), dial.map { HomeCatalogItem.Track(it) }))
        }
        _state.update { it.copy(personalReady = cacheReady, personalSections = base + it.personalSections.filter { section -> section.title == context.getString(com.aro.music.R.string.home_listening_recommendations) }) }
        // Song changes, likes and pins only rebuild local shelves. The first ever
        // played song can seed an empty feed; subsequent songs wait for refresh.
        if (!cacheReady || (!force && !(bootstrapRecommendations && lastSeed == null))) return@withLock
        val seed = activeSong ?: recent.firstOrNull() ?: return@withLock
        val videoId = com.aro.music.data.innertube.InnerTubeIds.videoIdFromContentUri(seed.contentUriString)
            ?: return@withLock
        if (lastSeed == seed.id && personalJob?.isActive == true) return@withLock
        lastSeed = seed.id
        personalJob?.cancel()
        personalJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val related = repository.radioPage(videoId).songs.filter { it.id != seed.id }.distinctBy { it.id }.take(20)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (lastSeed == seed.id && related.isNotEmpty()) {
                    val section = HomeCatalogSection(context.getString(com.aro.music.R.string.home_listening_recommendations), context.getString(com.aro.music.R.string.home_because_listened, seed.title), related.map { HomeCatalogItem.Track(it) })
                    _state.update { it.copy(personalSections = it.personalSections.filterNot { row -> row.title == context.getString(com.aro.music.R.string.home_listening_recommendations) } + section) }
                    updatePersonalFeed()
                    persistSnapshot()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.w(e, "Listening recommendations failed")
            }
        }
    }


    fun refresh() {
        synchronized(this) {
            if (!cacheReady || job?.isActive == true) return
            seenTokens.clear()
            job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                _state.update { it.copy(isLoading = true, isRefreshing = true, canRetry = false) }
                try {
                    repository.invalidateHomeCache()
                    updatePersonalFeed(force = true)
                    coroutineScope {
                        val home = async {
                            try { repository.getHomePage() }
                            catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Timber.w(e, "Home shelves failed")
                                null
                            }
                        }
                        val explore = async {
                            try { repository.getExploreSections() }
                            catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Timber.w(e, "Explore shelves failed")
                                emptyList()
                            }
                        }
                        val page = home.await()
                        val sections = page?.sections.orEmpty() + explore.await()
                        if (sections.isNotEmpty()) {
                            merge(sections, replace = true)
                        }
                        _state.update { it.copy(continuation = page?.continuation, canRetry = sections.isEmpty()) }
                    }
                    personalJob?.join()
                    if (!_state.value.canRetry && _state.value.sections.isNotEmpty()) fetchedAt = System.currentTimeMillis()
                    persistSnapshot()
                } finally {
                    // Publish completion only after allowing the next refresh. A caller
                    // observing the idle state must not be rejected by the old job.
                    synchronized(this@HomeFeedViewModel) {
                        job = null
                        _state.update { it.copy(isLoading = false, isRefreshing = false) }
                    }
                }
            }
            // Register the job before it can execute on another thread. Otherwise
            // an immediate caller can start a duplicate refresh before assignment.
            job?.start()
        }
    }


    fun loadMore() {
        synchronized(this) {
            if (job?.isActive == true) return
            val token = _state.value.continuation ?: return
            if (token in seenTokens) return
            job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                _state.update { it.copy(isLoading = true, canRetry = false) }
                try {
                    val page = repository.getHomePage(token)
                    merge(page.sections)
                    persistSnapshot()
                    seenTokens.add(token)
                    _state.update { it.copy(continuation = page.continuation?.takeUnless(seenTokens::contains)) }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.w(e, "Home continuation failed")
                    _state.update { it.copy(canRetry = true) }
                } finally {
                    synchronized(this@HomeFeedViewModel) {
                        job = null
                        _state.update { it.copy(isLoading = false) }
                    }
                }
            }
            job?.start()
        }
    }


    private fun persistSnapshot() {
        val current = _state.value
        if (current.sections.isEmpty() && current.personalSections.isEmpty()) return
        cache.write(HomeFeedCache.Snapshot(fetchedAt, current.sections.map(HomeFeedCache::encode),
            current.personalSections.filter { it.title == context.getString(com.aro.music.R.string.home_listening_recommendations) }.map(HomeFeedCache::encode), lastSeed))
    }

    private suspend fun merge(sections: List<HomeCatalogSection>, replace: Boolean = false) {
        val items = sections.flatMap { it.items }
        // Detail screens observe Room while their track fetch runs. Cache tile
        // headers first so navigation never starts with a missing album/artist.
        val albums = items.filterIsInstance<HomeCatalogItem.AlbumItem>().map { it.album }
        val artists = items.filterIsInstance<HomeCatalogItem.ArtistItem>().map { it.artist }
        if (albums.isNotEmpty()) musicDao.insertAlbumsIgnoreConflicts(albums.map {
            AlbumEntity(it.id, it.title, it.artist, 0L, it.albumArtUriString, it.songCount, it.dateAdded, it.year, it.albumArtist)
        })
        if (artists.isNotEmpty()) musicDao.insertArtistsIgnoreConflicts(artists.map { it.toEntity() })

        _state.update { current ->
            val merged = (if (replace) emptyList() else current.sections).associateByTo(linkedMapOf()) { it.title }
            sections.forEach { incoming ->
                val old = merged[incoming.title]
                merged[incoming.title] = incoming.copy(items = (old?.items.orEmpty() + incoming.items).distinctBy { it.id })
            }
            current.copy(sections = merged.values.toList())
        }
        updatePersonalFeed()
    }
}
