package com.aro.music.presentation.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import com.aro.music.R
import com.aro.music.data.database.MusicDao
import com.aro.music.data.innertube.*
import com.aro.music.data.model.Song
import com.aro.music.data.repository.MusicRepository
import com.aro.music.data.stats.PlaybackStatsRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedViewModelTest {
    private val context = mockk<Context>(relaxed = true)
    private val preferences = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val catalog = mockk<InnerTubeRepository>(relaxed = true)
    private val music = mockk<MusicRepository>(relaxed = true)
    private val stats = mockk<PlaybackStatsRepository>(relaxed = true)
    private val store = ViewModelStore()
    private val saved = java.util.concurrent.ConcurrentHashMap<String, String>()

    @BeforeEach fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { context.getSharedPreferences(any(), any()) } returns preferences
        every { preferences.getStringSet(any(), any()) } returns emptySet()
        every { preferences.getString(any(), any()) } answers { saved[firstArg()] ?: secondArg() }
        every { preferences.edit() } returns editor
        every { editor.putString(any(), any()) } answers { saved[firstArg()] = secondArg(); editor }
        every { editor.putStringSet(any(), any()) } returns editor
        every { context.getString(R.string.home_speed_dial) } returns "Speed dial"
        every { context.getString(R.string.home_speed_dial_hint) } returns "Pin a song"
        every { context.getString(R.string.home_listening_recommendations) } returns "Recommended for you"
        every { context.getString(R.string.home_because_listened, any()) } answers { "Because you listened to ${secondArg<Array<out Any>>().first()}" }
        coEvery { catalog.getHomePage(any()) } returns HomeCatalogPage(listOf(HomeCatalogSection("Explore", null, listOf(HomeCatalogItem.Track(song("catalog"))))), null)
        coEvery { catalog.getExploreSections() } returns emptyList()
        coEvery { stats.loadPlaybackHistory(any()) } returns emptyList()
        every { stats.refreshFlow } returns MutableStateFlow(0L)
        every { music.getFavoriteSongIdsFlow() } returns MutableStateFlow(emptySet())
        coEvery { music.getFavoriteSongsOnce(any()) } returns emptyList()
        every { music.getSongsByIds(any()) } returns flowOf(emptyList())
    }

    @AfterEach fun cleanup() { store.clear(); Dispatchers.resetMain() }

    private fun song(key: String): Song = Song.emptySong().copy(id = key, title = key,
        contentUriString = "innertube://$key", artists = emptyList())

    private suspend fun ready(model: HomeFeedViewModel) {
        withTimeout(10_000) { model.state.first { it.sections.isNotEmpty() && !it.isLoading } }
    }

    private fun model() = HomeFeedViewModel(catalog, mockk<MusicDao>(relaxed = true), music, stats, context).also {
        store.put("home", it)
    }

    @Test fun `playing a song changes recommendations and speed dial without adding library membership`() = runBlocking {
        val seed = song("seed")
        val related = song("related")
        coEvery { catalog.radioPage("seed", null) } returns InnerTubeRepository.InnerTubeRadioPage(listOf(seed, related, related), null)
        val model = model()
        ready(model)
        model.onSongPlayed(seed)
        val state = withTimeout(10_000) { model.state.first { it.personalSections.any { row -> row.title == "Recommended for you" } } }
        assertEquals(listOf("related"), state.personalSections.last().items.map { (it as HomeCatalogItem.Track).song.id })
        assertEquals("seed", state.personalSections.first().items.first().let { (it as HomeCatalogItem.Track).song.id })
        coVerify { music.cacheCatalogSongs(listOf(seed)) }
        coVerify(exactly = 0) { music.setFavoriteStatusWithSong(any(), any()) }
        model.toggleSpeedDial(seed)
        assertTrue("seed" in model.state.value.pinnedSongIds)
        verify { editor.putStringSet("pins", setOf("seed")) }
        model.toggleSpeedDial(seed)
        assertFalse("seed" in model.state.value.pinnedSongIds)
    }

    @Test fun `song changes and pins keep recommendations stable until manual refresh`() = runBlocking {
        coEvery { catalog.radioPage("old", null) } returns InnerTubeRepository.InnerTubeRadioPage(listOf(song("old-related")), null)
        coEvery { catalog.radioPage("new", null) } returns InnerTubeRepository.InnerTubeRadioPage(listOf(song("new-related")), null)
        val model = model()
        ready(model)
        model.onSongPlayed(song("old"))
        withTimeout(10_000) { model.state.first { it.personalSections.any { row -> row.label == "Because you listened to old" } } }
        model.onSongPlayed(song("new"))
        model.toggleSpeedDial(song("new"))
        withTimeout(10_000) { model.state.first { it.personalSections.firstOrNull()?.items?.firstOrNull()?.id == "song:new" } }
        assertEquals("Because you listened to old", model.state.value.personalSections.last().label)
        coVerify(exactly = 1) { catalog.radioPage("old", null) }
        coVerify(exactly = 0) { catalog.radioPage("new", null) }
        coVerify(exactly = 1) { catalog.getHomePage(null) }
        model.refresh()
        withTimeout(10_000) { model.state.first { !it.isRefreshing && it.personalSections.lastOrNull()?.label == "Because you listened to new" } }
        coVerify(exactly = 1) { catalog.radioPage("new", null) }
        coVerify(exactly = 2) { catalog.getHomePage(null) }
    }

    @Test fun `fresh disk snapshot survives a new view model without network fetches`() = runBlocking {
        coEvery { catalog.radioPage("seed", null) } returns InnerTubeRepository.InnerTubeRadioPage(listOf(song("related")), null)
        val original = model()
        ready(original)
        original.onSongPlayed(song("seed"))
        withTimeout(10_000) {
            while (saved["snapshot_v1"]?.contains("related") != true) delay(10)
        }
        store.clear()
        val restored = model()
        ready(restored)
        restored.onSongPlayed(song("another"))
        withTimeout(10_000) { restored.state.first { it.personalSections.firstOrNull()?.items?.firstOrNull()?.id == "song:another" } }
        assertEquals("Because you listened to seed", restored.state.value.personalSections.last().label)
        coVerify(exactly = 1) { catalog.getHomePage(null) }
        coVerify(exactly = 1) { catalog.getExploreSections() }
        coVerify(exactly = 0) { catalog.radioPage("another", null) }
    }

    @Test fun `expired snapshot stays visible while a single refresh is running`() = runBlocking {
        val snapshot = HomeFeedCache.Snapshot(System.currentTimeMillis() - HomeFeedCache.MAX_AGE_MS - 1,
            listOf(HomeFeedCache.encode(HomeCatalogSection("Cached", null, listOf(HomeCatalogItem.Track(song("cached")))))), emptyList(), null)
        saved["snapshot_v1"] = com.google.gson.Gson().toJson(snapshot)
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<HomeCatalogPage>()
        coEvery { catalog.getHomePage(null) } coAnswers { started.complete(Unit); response.await() }
        val model = model()
        withTimeout(10_000) { started.await() }
        assertEquals("Cached", model.state.value.sections.first().title)
        model.refresh()
        model.refresh()
        coVerify(exactly = 1) { catalog.getHomePage(null) }
        response.complete(HomeCatalogPage(listOf(HomeCatalogSection("New", null, listOf(HomeCatalogItem.Track(song("new"))))), null))
        withTimeout(10_000) { model.state.first { !it.isRefreshing && it.sections.firstOrNull()?.title == "New" } }
        assertTrue(saved["snapshot_v1"]!!.contains("New"))
    }

    @Test fun `failed manual refresh preserves the existing feed`() = runBlocking {
        val model = model()
        ready(model)
        val previous = model.state.value.sections
        coEvery { catalog.getHomePage(null) } throws java.io.IOException("offline")
        coEvery { catalog.getExploreSections() } throws java.io.IOException("offline")
        model.refresh()
        withTimeout(10_000) { model.state.first { it.canRetry && !it.isRefreshing } }
        assertEquals(previous, model.state.value.sections)
    }
}
