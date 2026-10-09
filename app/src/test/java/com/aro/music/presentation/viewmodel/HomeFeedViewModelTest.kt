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

    @BeforeEach fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { context.getSharedPreferences(any(), any()) } returns preferences
        every { preferences.getStringSet(any(), any()) } returns emptySet()
        every { preferences.edit() } returns editor
        every { editor.putStringSet(any(), any()) } returns editor
        every { context.getString(R.string.home_speed_dial) } returns "Speed dial"
        every { context.getString(R.string.home_speed_dial_hint) } returns "Pin a song"
        every { context.getString(R.string.home_listening_recommendations) } returns "Recommended for you"
        every { context.getString(R.string.home_because_listened, any()) } answers { "Because you listened to ${secondArg<Array<out Any>>().first()}" }
        coEvery { catalog.getHomePage(any()) } returns HomeCatalogPage(emptyList(), null)
        coEvery { catalog.getExploreSections() } returns emptyList()
        coEvery { stats.loadPlaybackHistory(any()) } returns emptyList()
        every { stats.refreshFlow } returns MutableStateFlow(0L)
        every { music.getFavoriteSongIdsFlow() } returns MutableStateFlow(emptySet())
        coEvery { music.getFavoriteSongsOnce(any()) } returns emptyList()
        every { music.getSongsByIds(any()) } returns flowOf(emptyList())
    }

    @AfterEach fun cleanup() { store.clear(); Dispatchers.resetMain() }

    private fun song(key: String): Song = mockk<Song>(relaxed = true).also {
        every { it.id } returns key
        every { it.title } returns key
        every { it.contentUriString } returns "innertube://$key"
    }

    private fun model() = HomeFeedViewModel(catalog, mockk<MusicDao>(relaxed = true), music, stats, context).also {
        store.put("home", it)
    }

    @Test fun `playing a song changes recommendations and speed dial without adding library membership`() = runBlocking {
        val seed = song("seed")
        val related = song("related")
        coEvery { catalog.radioPage("seed", null) } returns InnerTubeRepository.InnerTubeRadioPage(listOf(seed, related, related), null)
        val model = model()
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

    @Test fun `late responses from an older song cannot replace the latest recommendations`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        coEvery { catalog.radioPage("old", null) } coAnswers { started.complete(Unit); awaitCancellation() }
        coEvery { catalog.radioPage("new", null) } returns InnerTubeRepository.InnerTubeRadioPage(listOf(song("new-related")), null)
        val model = model()
        model.onSongPlayed(song("old"))
        withTimeout(10_000) { started.await() }
        model.onSongPlayed(song("new"))
        val state = withTimeout(10_000) { model.state.first { it.personalSections.any { row -> row.items.any { item -> item.id == "song:new-related" } } } }
        assertEquals("Because you listened to new", state.personalSections.last().label)
        assertFalse(state.personalSections.last().items.any { it.id == "song:old" })
    }
}
