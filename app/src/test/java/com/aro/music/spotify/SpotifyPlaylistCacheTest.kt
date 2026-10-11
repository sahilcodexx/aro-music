package com.aro.music.spotify

import android.content.Context
import android.content.SharedPreferences
import com.aro.music.data.model.Song
import com.aro.music.spotify.models.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpotifyPlaylistCacheTest {
    @AfterEach fun cleanup() { unmockkObject(Spotify) }
    private fun context(): Context {
        val values = mutableMapOf<String, String>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } answers { values[firstArg()] = secondArg(); editor }
        val preferences = mockk<SharedPreferences>(relaxed = true)
        every { preferences.getString(any(), any()) } answers { values[firstArg()] }
        every { preferences.edit() } returns editor
        return mockk<Context>().also { every { it.getSharedPreferences(any(), any()) } returns preferences }
    }
    private fun repository(context: Context, customMapper: SpotifyYouTubeMapper? = null): SpotifyRepository {
        val session = mockk<SpotifySession>(relaxed = true)
        every { session.isLoggedInFlow } returns flowOf(true)
        coEvery { session.ensureAuthenticated() } returns true
        val mapper = customMapper ?: mockk<SpotifyYouTubeMapper>()
        if (customMapper == null) coEvery { mapper.mapToYouTube(any()) } answers { Song.emptySong().copy(id = firstArg<SpotifyTrack>().id, duration = 123000, contentUriString = "innertube://video") }
        return SpotifyRepository(context, session, mockk(), mapper, mockk())
    }
    private fun source() {
        mockkObject(Spotify)
        coEvery { Spotify.playlist("mix") } returns Result.success(SpotifyPlaylist(id = "mix", name = "Mix", tracks = SpotifyPlaylistTracksRef(5)))
        coEvery { Spotify.playlistTracks("mix", 50, 0) } returns Result.success(SpotifyPaging((1..5).map { SpotifyPlaylistTrack(track = SpotifyTrack(id = "$it")) }, total = 5))
        coEvery { Spotify.playlistTracks("mix", 3, 0) } returns Result.success(SpotifyPaging((1..3).map { SpotifyPlaylistTrack(track = SpotifyTrack(id = "$it")) }, total = 5))
    }
    @Test fun `reopen and new repository restore full snapshot without Spotify fetch or mapping`() = runBlocking {
        source()
        val context = context()
        val repo = repository(context)
        val partials = mutableListOf<Int>()
        val first = repo.resolvePlaylist("spotify:mix", onPartial = { partials += it.songs.size })!!
        assertEquals(listOf(0, 1, 2, 3, 4, 5), partials)
        assertEquals(first, repo.resolvePlaylist("spotify:mix"))
        val cachedMapper = mockk<SpotifyYouTubeMapper>()
        val restored = repository(context, cachedMapper).resolvePlaylist("spotify:mix")!!
        coVerify(exactly = 0) { cachedMapper.mapToYouTube(any()) }
        assertEquals(first, restored)
        assertEquals(123000L, restored.songs.first().duration)
        coVerify(exactly = 1) { Spotify.playlistTracks("mix", 50, 0) }
        coVerify(exactly = 1) { Spotify.playlist("mix") }
    }
    @Test fun `preview never prevents a later full load and forced refresh bypasses cache`() = runBlocking {
        source()
        val repo = repository(context())
        assertEquals(3, repo.resolvePlaylist("spotify:mix", maxTracks = 3)!!.songs.size)
        assertEquals(5, repo.resolvePlaylist("spotify:mix")!!.songs.size)
        assertEquals(5, repo.resolvePlaylist("spotify:mix", forceRefresh = true)!!.songs.size)
        coVerify(exactly = 2) { Spotify.playlistTracks("mix", 50, 0) }
    }
    @Test fun `concurrent full loads share one resolution`() = runBlocking {
        source()
        val repo = repository(context())
        val results = awaitAll(async { repo.resolvePlaylist("spotify:mix") }, async { repo.resolvePlaylist("spotify:mix") })
        assertEquals(results.first(), results.last())
        coVerify(exactly = 1) { Spotify.playlistTracks("mix", 50, 0) }
    }
    @Test fun `first playable track appears before a slower match completes`() = runBlocking {
        source()
        val gate = CompletableDeferred<Unit>()
        val firstReady = CompletableDeferred<Unit>()
        val mapper = mockk<SpotifyYouTubeMapper>()
        coEvery { mapper.mapToYouTube(any()) } coAnswers {
            val track = firstArg<SpotifyTrack>()
            if (track.id == "2") gate.await()
            Song.emptySong().copy(id = track.id)
        }
        val repo = repository(context(), mapper)
        val load = async { repo.resolvePlaylist("spotify:mix", onPartial = {
            if (it.songs.map { song -> song.id } == listOf("1")) firstReady.complete(Unit)
        }) }
        try {
            withTimeout(5000) { firstReady.await() }
            assertFalse(load.isCompleted)
        } finally {
            gate.complete(Unit)
        }
        assertEquals(listOf("1", "2", "3", "4", "5"), load.await()!!.songs.map { it.id })
    }
    @Test fun `cache freshness rejects old and future timestamps`() {
        val snapshot = SpotifyPlaylistCache.Snapshot(1000, com.aro.music.data.model.Playlist("spotify:mix", "Mix", emptyList()), emptyList(), 0)
        assertTrue(snapshot.isFresh(1001))
        assertFalse(snapshot.isFresh(999))
        assertFalse(snapshot.isFresh(1000 + SpotifyPlaylistCache.MAX_AGE_MS))
    }
}
