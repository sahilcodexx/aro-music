package com.aro.music.presentation.viewmodel

import android.content.Context
import android.content.SharedPreferences
import com.aro.music.data.DailyMixManager
import com.aro.music.data.model.Song
import com.aro.music.data.preferences.UserPreferencesRepository
import com.aro.music.data.streaming.StreamingRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DailyMixStateHolderTest {
    private val context = mockk<Context>(relaxed = true)
    private val preferences = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val streaming = mockk<StreamingRepository>()
    private val manager = mockk<DailyMixManager>()
    private val saved = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val favorites = MutableStateFlow(emptySet<String>())

    @BeforeEach fun setup() {
        every { context.getSharedPreferences(any(), any()) } returns preferences
        every { preferences.getString(any(), any()) } answers { saved[firstArg()] ?: secondArg() }
        every { preferences.edit() } returns editor
        every { editor.putString(any(), any()) } answers { saved[firstArg()] = secondArg(); editor }
        coEvery { manager.generateDailyMix(any(), any(), any()) } answers { firstArg() }
        coEvery { manager.generateYourMix(any(), any(), any()) } answers { firstArg() }
        coEvery { streaming.getTrendingSongs(any()) } returns listOf(song("original"))
    }

    @AfterEach fun cleanup() { scope.cancel() }
    private fun song(id: String) = Song.emptySong().copy(id = id, title = id)
    private fun holder() = DailyMixStateHolder(manager, mockk<UserPreferencesRepository>(relaxed = true), streaming, context).also { it.initialize(scope) }
    private suspend fun savedMix(id: String) = withTimeout(10_000) {
        while (saved["snapshot_v1"]?.contains(id) != true) delay(10)
    }

    @Test fun `fresh daily mix is restored without a network request on reopen`() = runBlocking {
        val original = holder()
        original.updateDailyMix(favorites)
        savedMix("original")
        original.onCleared()
        val restored = holder()
        restored.updateDailyMix(favorites)
        withTimeout(10_000) { restored.yourMixSongs.first { it.isNotEmpty() } }
        assertEquals(listOf("original"), restored.dailyMixSongs.value.map { it.id })
        coVerify(exactly = 1) { streaming.getTrendingSongs(50) }
    }

    @Test fun `expired daily mix renews once and saves the replacement`() = runBlocking {
        val original = holder()
        original.updateDailyMix(favorites)
        savedMix("original")
        original.onCleared()
        val old = System.currentTimeMillis() - 25 * 60 * 60_000L
        saved["snapshot_v1"] = saved["snapshot_v1"]!!.replace(Regex("\"fetchedAt\":\\d+"), "\"fetchedAt\":$old")
        coEvery { streaming.getTrendingSongs(50) } returns listOf(song("renewed"))
        val restored = holder()
        restored.updateDailyMix(favorites)
        savedMix("renewed")
        assertEquals(listOf("renewed"), restored.dailyMixSongs.value.map { it.id })
        coVerify(exactly = 2) { streaming.getTrendingSongs(50) }
    }

    @Test fun `duplicate update calls share one request and preserve visible songs`() = runBlocking {
        val holder = holder()
        holder.updateDailyMix(favorites)
        savedMix("original")
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<List<Song>>()
        coEvery { streaming.getTrendingSongs(50) } coAnswers { started.complete(Unit); response.await() }
        holder.forceUpdate(favorites)
        withTimeout(10_000) { started.await() }
        holder.forceUpdate(favorites)
        holder.checkAndUpdateIfNeeded(favorites)
        assertEquals(listOf("original"), holder.dailyMixSongs.value.map { it.id })
        coVerify(exactly = 2) { streaming.getTrendingSongs(50) }
        response.complete(listOf(song("refreshed")))
        savedMix("refreshed")
        assertEquals(listOf("refreshed"), holder.dailyMixSongs.value.map { it.id })
    }
}
