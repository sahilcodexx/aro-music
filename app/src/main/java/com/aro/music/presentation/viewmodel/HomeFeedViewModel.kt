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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class HomeFeedState(
    val sections: List<HomeCatalogSection> = emptyList(),
    val isLoading: Boolean = false,
    val continuation: String? = null,
    val canRetry: Boolean = false,
)

@HiltViewModel
class HomeFeedViewModel @Inject constructor(
    private val repository: InnerTubeRepository,
    private val musicDao: MusicDao,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeFeedState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private val seenTokens = mutableSetOf<String>()

    init { refresh() }

    fun refresh() {
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
