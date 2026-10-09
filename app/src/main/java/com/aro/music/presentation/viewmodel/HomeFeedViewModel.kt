package com.aro.music.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aro.music.data.database.MusicDao
import com.aro.music.data.database.AlbumEntity
import com.aro.music.data.database.toEntity
import com.aro.music.data.innertube.HomeCatalogItem
import com.aro.music.data.innertube.HomeCatalogSection
import com.aro.music.data.innertube.InnerTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject

data class HomeFeedState(
    val sections: List<HomeCatalogSection> = emptyList(),
    val personalSections: List<HomeCatalogSection> = emptyList(),
    val pinnedSongIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val continuation: String? = null,
    val canRetry: Boolean = false,
)

@HiltViewModel
class HomeFeedViewModel @Inject constructor(
    private val repository: InnerTubeRepository,
    private val musicDao: MusicDao,
    private val musicRepository: com.aro.music.data.repository.MusicRepository,
    private val statsRepository: com.aro.music.data.stats.PlaybackStatsRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeFeedState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private val seenTokens = mutableSetOf<String>()

    private val preferences = context.getSharedPreferences("home_speed_dial", android.content.Context.MODE_PRIVATE)
    private var personalJob: Job? = null
    private val personalizationMutex = Mutex()
    @Volatile private var activeSong: com.aro.music.data.model.Song? = null
    private var lastSeed: String? = null

    init {
        _state.update { it.copy(pinnedSongIds = preferences.getStringSet("pins", emptySet()).orEmpty().toSet()) }
        refresh()
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
            updatePersonalFeed()
        }
    }

    fun toggleSpeedDial(song: com.aro.music.data.model.Song) {
        val pins = _state.value.pinnedSongIds.toMutableSet()
        if (!pins.add(song.id)) pins.remove(song.id)
        preferences.edit().putStringSet("pins", pins).apply()
        _state.update { it.copy(pinnedSongIds = pins) }
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.cacheCatalogSongs(listOf(song))
            updatePersonalFeed(force = true)
        }
    }

    private suspend fun updatePersonalFeed(force: Boolean = false) = personalizationMutex.withLock {
        val history = statsRepository.loadPlaybackHistory(100).map { it.songId }.distinct().take(20)
        val favorites = musicRepository.getFavoriteSongsOnce(com.aro.music.data.model.StorageFilter.ALL)
        val ids = (_state.value.pinnedSongIds + listOfNotNull(activeSong?.id) + history + favorites.map { it.id }).distinct()
        val rows = musicRepository.getSongsByIds(ids).first()
        val lookup = (rows + listOfNotNull(activeSong)).associateBy { it.id }
        val recent = history.mapNotNull { lookup[it] }
        val dial = (_state.value.pinnedSongIds.mapNotNull { lookup[it] } + listOfNotNull(activeSong) + recent + favorites)
            .distinctBy { it.id }.take(12)
        val base = buildList {
            if (dial.isNotEmpty()) add(HomeCatalogSection(context.getString(com.aro.music.R.string.home_speed_dial), context.getString(com.aro.music.R.string.home_speed_dial_hint), dial.map { HomeCatalogItem.Track(it) }))
            if (recent.isNotEmpty()) add(HomeCatalogSection("Recently played", null, recent.map { HomeCatalogItem.Track(it) }))
        }
        _state.update { it.copy(personalSections = base + it.personalSections.filter { section -> section.title == context.getString(com.aro.music.R.string.home_listening_recommendations) }) }
        val seed = activeSong ?: recent.firstOrNull() ?: return@withLock
        if (lastSeed == seed.id && !force) return@withLock
        lastSeed = seed.id
        _state.update { it.copy(personalSections = base) }
        personalJob?.cancel()
        personalJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val videoId = com.aro.music.data.innertube.InnerTubeIds.videoIdFromContentUri(seed.contentUriString)
                    ?: return@launch
                val related = repository.radioPage(videoId).songs.filter { it.id != seed.id }.distinctBy { it.id }.take(20)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (lastSeed == seed.id && related.isNotEmpty()) {
                    val section = HomeCatalogSection(context.getString(com.aro.music.R.string.home_listening_recommendations), context.getString(com.aro.music.R.string.home_because_listened, seed.title), related.map { HomeCatalogItem.Track(it) })
                    _state.update { it.copy(personalSections = it.personalSections.filterNot { row -> row.title == context.getString(com.aro.music.R.string.home_listening_recommendations) } + section) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastSeed = null
                Timber.w(e, "Listening recommendations failed")
            }
        }
    }


    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { updatePersonalFeed() }
        if (job?.isActive == true) return
        seenTokens.clear()
        job = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, canRetry = false) }
            try {
                coroutineScope {
                    launch {
                        try {
                            val page = repository.getHomePage()
                            merge(page.sections)
                            _state.update { it.copy(continuation = page.continuation, canRetry = page.sections.isEmpty()) }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Timber.w(e, "Home shelves failed")
                            _state.update { it.copy(canRetry = true) }
                        }
                    }
                    launch {
                        try { merge(repository.getExploreSections()) }
                        catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Timber.w(e, "Explore shelves failed")
                        }
                    }
                }
            } finally { _state.update { it.copy(isLoading = false) } }
        }
    }

    fun loadMore() {
        if (job?.isActive == true) return
        val token = _state.value.continuation ?: return
        if (token in seenTokens) return
        job = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, canRetry = false) }
            try {
                val page = repository.getHomePage(token)
                merge(page.sections)
                seenTokens.add(token)
                _state.update { it.copy(continuation = page.continuation?.takeUnless(seenTokens::contains)) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.w(e, "Home continuation failed")
                _state.update { it.copy(canRetry = true) }
            } finally { _state.update { it.copy(isLoading = false) } }
        }
    }

    private suspend fun merge(sections: List<HomeCatalogSection>) {
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
            val merged = current.sections.associateByTo(linkedMapOf()) { it.title }
            sections.forEach { incoming ->
                val old = merged[incoming.title]
                merged[incoming.title] = incoming.copy(items = (old?.items.orEmpty() + incoming.items).distinctBy { it.id })
            }
            current.copy(sections = merged.values.toList())
        }
    }
}
