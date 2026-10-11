package com.aro.music.spotify

import android.content.Context
import com.aro.music.data.database.AroDatabase
import com.aro.music.data.innertube.InnerTubeBrowseRegistry
import com.aro.music.data.model.Song
import com.aro.music.spotify.models.*
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpotifyPlaylistImportTest {
    @AfterEach fun cleanup() { unmockkObject(Spotify) }
    @Test fun `missing tracks do not corrupt pagination offsets or source counts`() = runBlocking {
        mockkObject(Spotify)
        val session = mockk<SpotifySession>(relaxed = true)
        every { session.isLoggedInFlow } returns flowOf(true)
        coEvery { session.ensureAuthenticated() } returns true
        val mapper = mockk<SpotifyYouTubeMapper>()
        coEvery { Spotify.playlist("playlist") } returns Result.success(SpotifyPlaylist(id = "playlist", name = "My mix"))
        coEvery { Spotify.playlistTracks("playlist", 50, 0) } returns Result.success(SpotifyPaging(items = listOf(SpotifyPlaylistTrack(track = SpotifyTrack(id = "one"))), total = 52, rawCount = 50))
        coEvery { Spotify.playlistTracks("playlist", 50, 50) } returns Result.success(SpotifyPaging(items = listOf(SpotifyPlaylistTrack(track = SpotifyTrack(id = "two"))), total = 52, offset = 50, rawCount = 2))
        coEvery { mapper.mapToYouTube(any()) } answers { Song.emptySong().copy(id = firstArg<SpotifyTrack>().id) }
        val repository = SpotifyRepository(mockk<Context>(relaxed = true), session, mockk<AroDatabase>(), mapper, mockk<InnerTubeBrowseRegistry>())
        val result = repository.resolvePlaylist("spotify:playlist")!!
        assertEquals(52, result.totalTracks)
        assertEquals(52, result.playlist.remoteSongCount)
        assertEquals(listOf("one", "two"), result.songs.map { it.id })
        coVerify(exactly = 1) { Spotify.playlistTracks("playlist", 50, 50) }
    }
    @Test fun `library uses Spotify totals without mapping tracks`() = runBlocking {
        mockkObject(Spotify)
        val session = mockk<SpotifySession>(relaxed = true)
        every { session.isLoggedInFlow } returns flowOf(true)
        coEvery { session.ensureAuthenticated() } returns true
        coEvery { Spotify.likedSongs(1, 0) } returns Result.success(SpotifyPaging(emptyList(), total = 8))
        coEvery { Spotify.myPlaylists(50, 0) } returns Result.success(SpotifyPaging(listOf(
            SpotifyPlaylist(id = "known", name = "Known", tracks = SpotifyPlaylistTracksRef(total = 99)),
            SpotifyPlaylist(id = "lazy", name = "Lazy"),
            SpotifyPlaylist(id = "empty", name = "Empty", tracks = SpotifyPlaylistTracksRef(total = 0)),
        ), total = 3))
        coEvery { Spotify.playlistTracks("lazy", 1, 0) } returns Result.success(SpotifyPaging(emptyList(), total = 17))
        val mapper = mockk<SpotifyYouTubeMapper>()
        val repository = SpotifyRepository(mockk(relaxed = true), session, mockk(), mapper, mockk())
        val playlists = repository.libraryPlaylistsFlow.first()
        assertEquals(listOf(8, 99, 17, 0), playlists.map { it.remoteSongCount })
        assertTrue(playlists.all { it.songIds.isEmpty() })
        coVerify(exactly = 0) { mapper.mapToYouTube(any()) }
        coVerify(exactly = 0) { Spotify.playlistTracks("known", any(), any()) }
    }

    @Test fun `failed count lookup remains unknown instead of zero`() = runBlocking {
        mockkObject(Spotify)
        val session = mockk<SpotifySession>(relaxed = true)
        every { session.isLoggedInFlow } returns flowOf(true)
        coEvery { session.ensureAuthenticated() } returns true
        coEvery { Spotify.likedSongs(1, 0) } returns Result.success(SpotifyPaging(emptyList(), total = 4))
        coEvery { Spotify.myPlaylists(50, 0) } returns Result.success(SpotifyPaging(listOf(SpotifyPlaylist(id = "unavailable", name = "Saved")), total = 1))
        coEvery { Spotify.playlistTracks("unavailable", 1, 0) } returns Result.failure(IllegalStateException("Unavailable"))
        val repository = SpotifyRepository(mockk(relaxed = true), session, mockk(), mockk(), mockk())
        val playlists = repository.libraryPlaylistsFlow.first()
        assertEquals(2, playlists.size)
        assertNull(playlists.last().remoteSongCount)
    }

    @Test fun `disconnected account does not fetch playlist data`() = runBlocking {
        mockkObject(Spotify)
        val session = mockk<SpotifySession>(relaxed = true)
        every { session.isLoggedInFlow } returns flowOf(false)
        coEvery { session.ensureAuthenticated() } returns false
        val repository = SpotifyRepository(mockk(relaxed = true), session, mockk(), mockk(), mockk())
        assertNull(repository.resolvePlaylist("spotify:playlist"))
        coVerify(exactly = 0) { Spotify.playlist(any()) }
    }
}
