package com.aro.music.presentation.viewmodel

import com.aro.music.data.model.SearchFilterType
import com.aro.music.data.model.SearchHistoryItem
import com.aro.music.data.model.SearchResultItem
import com.aro.music.data.repository.MusicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.FlowPreview

/**
 * Manages search state and operations.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - Search query execution
 * - Search filter management
 * - Search history CRUD operations
 */
@Singleton
class SearchStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
) {
    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    // Search State
    private val _searchResults = MutableStateFlow<ImmutableList<SearchResultItem>>(persistentListOf())
    val searchResults = _searchResults.asStateFlow()

    private val _selectedSearchFilter = MutableStateFlow(SearchFilterType.ALL)
    val selectedSearchFilter = _selectedSearchFilter.asStateFlow()

    private val _searchHistory = MutableStateFlow<ImmutableList<SearchHistoryItem>>(persistentListOf())
    val searchHistory = _searchHistory.asStateFlow()

    /**
     * Latest query text. Typed input lands here and is debounced by
     * [observeSearchRequests]; the filter is combined with it so switching tabs
     * re-runs the search that is already on screen.
     */
    private val _activeQuery = MutableStateFlow("")

    private val latestSearchRequestId = AtomicLong(0L)

    private var scope: CoroutineScope? = null
    private var searchJob: Job? = null

    /**
     * Initialize with ViewModel scope.
     */
    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        observeSearchRequests()
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchRequests() {
        searchJob?.cancel()
        searchJob = scope?.launch {
            // Debouncing the query rather than the request means a filter change
            // re-runs the current query immediately, without re-entering the
            // 300ms debounce window.
            combine(
                _activeQuery.debounce(SEARCH_DEBOUNCE_MS),
                _selectedSearchFilter,
            ) { query, filter -> query to filter }
                .collectLatest { (query, filter) ->
                    if (query.isBlank()) {
                        if (_searchResults.value.isNotEmpty()) {
                            _searchResults.value = persistentListOf()
                        }
                        return@collectLatest
                    }

                    val requestId = latestSearchRequestId.incrementAndGet()
                    try {
                        // Routed through MusicRepository so the selected filter is
                        // honoured and favourite state is merged in. The previous
                        // implementation called the streaming API directly, which
                        // made the ALL/SONGS/ALBUMS/ARTISTS/PLAYLISTS chips render
                        // but never change the results.
                        val results = withContext(Dispatchers.IO) {
                            musicRepository.searchAll(query = query, filterType = filter)
                        }.first()

                        // A newer keystroke arrived while this one was in flight.
                        if (requestId != latestSearchRequestId.get()) return@collectLatest
                        _searchResults.value = results.toImmutableList()
                    } catch (_: CancellationException) {
                        // Superseded by a newer query; ignore.
                    } catch (e: Exception) {
                        if (requestId == latestSearchRequestId.get()) {
                            Timber.e(e, "Error performing search for query: $query")
                            _searchResults.value = persistentListOf()
                        }
                    }
                }
        }
    }

    fun updateSearchFilter(filterType: SearchFilterType) {
        if (_selectedSearchFilter.value == filterType) return
        _selectedSearchFilter.value = filterType
    }

    fun loadSearchHistory(limit: Int = 15) {
        scope?.launch {
            try {
                val history = withContext(Dispatchers.IO) {
                    musicRepository.getRecentSearchHistory(limit)
                }
                _searchHistory.value = history.toImmutableList()
            } catch (e: Exception) {
                Timber.e(e, "Error loading search history")
            }
        }
    }

    fun onSearchQuerySubmitted(query: String) {
        scope?.launch {
            if (query.isNotBlank()) {
                try {
                    withContext(Dispatchers.IO) {
                        musicRepository.addSearchHistoryItem(query)
                    }
                    loadSearchHistory()
                } catch (e: Exception) {
                    Timber.e(e, "Error adding search history item")
                }
            }
        }
    }

    fun performSearch(query: String) {
        val normalizedQuery = query.trim()

        if (normalizedQuery.isBlank() && _searchResults.value.isNotEmpty()) {
            _searchResults.value = persistentListOf()
        }

        _activeQuery.value = normalizedQuery
    }

    fun deleteSearchHistoryItem(query: String) {
        scope?.launch {
            try {
                withContext(Dispatchers.IO) {
                    musicRepository.deleteSearchHistoryItemByQuery(query)
                }
                loadSearchHistory()
            } catch (e: Exception) {
                Timber.e(e, "Error deleting search history item")
            }
        }
    }

    fun clearSearchHistory() {
        scope?.launch {
            try {
                withContext(Dispatchers.IO) {
                    musicRepository.clearSearchHistory()
                }
                _searchHistory.value = persistentListOf()
            } catch (e: Exception) {
                Timber.e(e, "Error clearing search history")
            }
        }
    }

    fun onCleared() {
        searchJob?.cancel()
        scope = null
    }
}
