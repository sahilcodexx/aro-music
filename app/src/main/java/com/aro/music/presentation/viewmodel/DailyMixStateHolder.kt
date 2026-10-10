package com.aro.music.presentation.viewmodel

import com.aro.music.data.DailyMixManager
import com.aro.music.data.model.Song
import com.aro.music.data.preferences.UserPreferencesRepository
import com.aro.music.data.streaming.StreamingRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Daily Mix and Your Mix state.
 * Fetches songs directly from the streaming API — no DB dependency.
 */
@Singleton
class DailyMixStateHolder @Inject constructor(
    private val dailyMixManager: DailyMixManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val streamingRepository: StreamingRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
) {
    private val preferences = context.getSharedPreferences("daily_mix_snapshot", android.content.Context.MODE_PRIVATE)
    private val gson = com.google.gson.Gson()
    private var fetchedAt = 0L
    @androidx.annotation.Keep
    private data class Snapshot(val fetchedAt: Long, val daily: List<Song>, val your: List<Song>)

    private fun restoreSnapshot() {
        try {
            preferences.getString("snapshot_v1", null)?.let { raw ->
                val snapshot = gson.fromJson(raw, Snapshot::class.java)
                fetchedAt = snapshot.fetchedAt
                _dailyMixSongs.value = snapshot.daily.toImmutableList()
                _yourMixSongs.value = snapshot.your.toImmutableList()
            }
        } catch (e: Exception) { timber.log.Timber.w(e, "Ignoring invalid daily mix snapshot") }
    }

    private var scope: CoroutineScope? = null
    private var updateJob: Job? = null

    private val _dailyMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val dailyMixSongs: StateFlow<ImmutableList<Song>> = _dailyMixSongs.asStateFlow()

    private val _yourMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val yourMixSongs: StateFlow<ImmutableList<Song>> = _yourMixSongs.asStateFlow()

    fun initialize(coroutineScope: CoroutineScope) {
        scope = coroutineScope
    }

    fun removeFromDailyMix(songId: String) {
        _dailyMixSongs.update { it.filterNot { s -> s.id == songId }.toImmutableList() }
    }

    /**
     * Fetch trending songs from API and build daily/your mixes.
     */
    fun updateDailyMix(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>, force: Boolean = false) {
        if (updateJob?.isActive == true) return
        updateJob = scope?.launch(Dispatchers.IO) {
            if (_dailyMixSongs.value.isEmpty()) restoreSnapshot()
            if (!force && _dailyMixSongs.value.isNotEmpty() && System.currentTimeMillis() - fetchedAt in 0 until 24 * 60 * 60_000L) return@launch
            var attempt = 0
            val maxAttempts = 5
            val retryDelayMs = 5_000L
            while (attempt < maxAttempts) {
                attempt++
                val allSongs: List<Song> = try {
                    streamingRepository.getTrendingSongs(limit = 50)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }

                if (allSongs.isNotEmpty()) {
                    val favoriteIds = try { favoriteSongIdsFlow.first() } catch (_: Exception) { emptySet() }

                    val mix = dailyMixManager.generateDailyMix(allSongs, favoriteIds)
                    _dailyMixSongs.value = mix.toImmutableList()
                    userPreferencesRepository.saveDailyMixSongIds(mix.map { it.id })

                    val yourMix = dailyMixManager.generateYourMix(allSongs, favoriteIds)
                    _yourMixSongs.value = yourMix.toImmutableList()
                    userPreferencesRepository.saveYourMixSongIds(yourMix.map { it.id })
                    fetchedAt = System.currentTimeMillis()
                    preferences.edit().putString("snapshot_v1", gson.toJson(Snapshot(fetchedAt, mix, yourMix))).apply()
                    userPreferencesRepository.saveLastDailyMixUpdateTimestamp(fetchedAt)
                    return@launch
                }
                // API returned empty — retry after delay
                if (attempt < maxAttempts) delay(retryDelayMs)
            }
        }
    }

    /**
     * Compatibility hook. Restoration is owned by updateDailyMix to avoid
     * concurrent restoration overwriting a newly fetched mix.
     */
    fun loadPersistedDailyMix() {
        // updateDailyMix restores the snapshot in the same job as its freshness check.
    }

    fun forceUpdate(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        updateDailyMix(favoriteSongIdsFlow, force = true)
    }

    fun checkAndUpdateIfNeeded(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        updateDailyMix(favoriteSongIdsFlow)
    }

    fun setDailyMixSongs(songs: List<Song>) {
        _dailyMixSongs.value = songs.toImmutableList()
        scope?.launch {
            userPreferencesRepository.saveDailyMixSongIds(songs.map { it.id })
        }
    }

    suspend fun getCandidatePool(
        allSongs: List<Song>,
        favoriteIds: Set<String>,
        maxSize: Int = 100
    ): List<Song> = dailyMixManager.generateDailyMix(allSongs, favoriteIds, maxSize)

    fun onCleared() {
        updateJob?.cancel()
        scope = null
    }
}
