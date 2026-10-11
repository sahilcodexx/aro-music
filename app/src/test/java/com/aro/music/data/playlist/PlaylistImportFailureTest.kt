package com.aro.music.data.playlist

import com.aro.music.data.database.AroDatabase
import com.aro.music.data.innertube.InnerTubeRepository
import com.aro.music.data.innertube.InnerTubeRepository.ResolvedRemotePlaylist
import com.aro.music.data.model.Playlist
import com.aro.music.data.model.Song
import com.aro.music.data.preferences.PlaylistPreferencesRepository
import com.aro.music.data.repository.MusicRepository
import com.aro.music.spotify.SpotifyRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlaylistImportFailureTest {
    private val youtube = mockk<InnerTubeRepository>()
    private val spotify = mockk<SpotifyRepository>()
    private val music = mockk<MusicRepository>(relaxed = true)
    private val playlists = mockk<PlaylistPreferencesRepository>(relaxed = true)
    private val importer = PlaylistImportRepository(youtube, spotify, music, playlists, mockk<AroDatabase>())
    private fun failure(link: String) = assertThrows(PlaylistImportRepository.ImportException::class.java) { runBlocking { importer.import(link) } }.failure
    @Test fun `invalid link and disconnected spotify never save a playlist`() {
        assertEquals(PlaylistImportRepository.Failure.INVALID_LINK, failure("not a playlist"))
        coEvery { spotify.resolvePlaylist(any(), any(), any(), any(), any(), forceRefresh = true) } returns null
        assertEquals(PlaylistImportRepository.Failure.CONNECT_SPOTIFY, failure("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        verify(exactly = 0) { playlists.userPlaylistsFlow }
    }
    @Test fun `empty remote playlist is not saved as an empty library entry`() {
        coEvery { youtube.getRemotePlaylist(any(), any(), any()) } returns ResolvedRemotePlaylist(Playlist("remote", "Empty", emptyList()), emptyList())
        assertEquals(PlaylistImportRepository.Failure.EMPTY, failure("https://youtube.com/playlist?list=PLabcdefghijklmnopqrstuv"))
        verify(exactly = 0) { playlists.userPlaylistsFlow }
    }
    @Test fun `missing cached metadata prevents a broken saved playlist`() {
        val song = Song.emptySong().copy(id = "-1")
        coEvery { youtube.getRemotePlaylist(any(), any(), any()) } returns ResolvedRemotePlaylist(Playlist("remote", "Tracks", listOf(song.id)), listOf(song))
        every { music.getSongsByIds(any()) } returns flowOf(emptyList())
        assertEquals(PlaylistImportRepository.Failure.SAVE_FAILED, failure("https://youtube.com/playlist?list=PLabcdefghijklmnopqrstuv"))
        verify(exactly = 0) { playlists.userPlaylistsFlow }
    }
    @Test fun `canceling an in flight load does not save a partial playlist`() = kotlinx.coroutines.runBlocking {
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { youtube.getRemotePlaylist(any(), any(), any()) } coAnswers {
            started.complete(Unit)
            kotlinx.coroutines.awaitCancellation()
        }
        val task = launch { importer.import("https://youtube.com/playlist?list=PLabcdefghijklmnopqrstuv") }
        kotlinx.coroutines.withTimeout(10_000) { started.await() }
        task.cancel()
        task.join()
        verify(exactly = 0) { playlists.userPlaylistsFlow }
    }

}
