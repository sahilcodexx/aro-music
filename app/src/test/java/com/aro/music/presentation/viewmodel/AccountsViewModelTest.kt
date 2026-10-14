package com.aro.music.presentation.viewmodel

import androidx.lifecycle.ViewModelStore
import com.aro.music.data.gdrive.GDriveRepository
import com.aro.music.data.jellyfin.JellyfinRepository
import com.aro.music.data.navidrome.NavidromeRepository
import com.aro.music.data.netease.NeteaseRepository
import com.aro.music.data.qqmusic.QqMusicRepository
import com.aro.music.data.repository.MusicRepository
import com.aro.music.data.telegram.TelegramRepository
import com.aro.music.spotify.SpotifySession
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {
    @Test fun `Spotify connection does not wait for unrelated provider collections`() = runTest {
        val store = ViewModelStore()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val telegram=mockk<TelegramRepository>(); val music=mockk<MusicRepository>()
            val drive=mockk<GDriveRepository>(); val netease=mockk<NeteaseRepository>()
            val qq=mockk<QqMusicRepository>(); val navidrome=mockk<NavidromeRepository>()
            val jellyfin=mockk<JellyfinRepository>(); val spotify=mockk<SpotifySession>()
            val connected=MutableStateFlow(false)
            every { spotify.isLoggedInFlow } returns connected
            every { telegram.authorizationState } returns MutableStateFlow(null)
            every { music.getAllTelegramChannels() } returns emptyFlow()
            every { drive.isLoggedInFlow } returns MutableStateFlow(false)
            every { drive.getFolders() } returns emptyFlow()
            every { netease.isLoggedInFlow } returns MutableStateFlow(false)
            every { netease.getPlaylists() } returns emptyFlow()
            every { qq.isLoggedInFlow } returns MutableStateFlow(false)
            every { qq.getPlaylists() } returns emptyFlow()
            every { navidrome.isLoggedInFlow } returns MutableStateFlow(false)
            every { navidrome.getPlaylists() } returns emptyFlow()
            every { jellyfin.isLoggedInFlow } returns MutableStateFlow(false)
            every { jellyfin.getPlaylists() } returns emptyFlow()
            val model=AccountsViewModel(telegram,music,drive,netease,qq,navidrome,spotify,jellyfin).also { store.put("accounts",it) }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
            runCurrent()
            connected.value=true
            runCurrent()
            assertEquals(listOf(ExternalServiceAccount.SPOTIFY),model.uiState.value.connectedAccounts.map { it.service })
            connected.value=false
            runCurrent()
            assertTrue(model.uiState.value.connectedAccounts.isEmpty())
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
}
