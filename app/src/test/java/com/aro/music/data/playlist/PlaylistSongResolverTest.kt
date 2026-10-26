package com.aro.music.data.playlist

import com.aro.music.data.model.*
import com.aro.music.data.preferences.UserPreferencesRepository
import com.aro.music.data.repository.MusicRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException

class PlaylistSongResolverTest {
    private val music = mockk<MusicRepository>()
    private val prefs = mockk<UserPreferencesRepository>()
    private val saved = linkedMapOf<String, Song>()
    private val songs = (1..53).map { n -> Song.emptySong().copy(id = "-$n", title = "Track $n", artist = "Artist",
        contentUriString = "innertube://v${n.toString().padStart(10, '0')}") }
    private fun resolver(): PlaylistSongResolver {
        every { music.getSongsByIds(any()) } answers { flowOf(firstArg<List<String>>().mapNotNull(saved::get)) }
        coEvery { prefs.getPlaybackQueueSnapshotOnce() } returns null
        coEvery { music.cacheCatalogSongs(any()) } coAnswers { firstArg<List<Song>>().forEach { saved[it.id] = it } }
        return PlaylistSongResolver(music, prefs)
    }
    @Test fun `all 53 queue tracks persist without playing beyond the first five`() = runTest {
        val resolver = resolver()
        songs.take(5).forEach { saved[it.id] = it.copy(isFavorite = true) }
        val resolved = resolver.resolve(songs.map { it.id }, songs, requireComplete = true)
        assertEquals(songs.map { it.id }, resolved.map { it.id })
        assertEquals(53, saved.size)
        assertTrue(resolved.take(5).all { it.isFavorite })
        coVerify { music.cacheCatalogSongs(match { it.size == 48 && it.none { s -> s.id in songs.take(5).map { old -> old.id } } }) }
    }
    @Test fun `existing broken playlist recovers missing metadata from saved queue`() = runTest {
        val resolver = resolver()
        songs.take(5).forEach { saved[it.id] = it }
        coEvery { prefs.getPlaybackQueueSnapshotOnce() } returns PlaybackQueueSnapshot(songs.map {
            PlaybackQueueItemSnapshot(it.id, it.contentUriString, it.title, it.artist)
        })
        assertEquals(songs.map { it.id }, resolver.resolve(songs.map { it.id }).map { it.id })
    }
    @Test fun `only selected songs are cached and local rows retain paths`() = runTest {
        val resolver = resolver()
        val local = Song.emptySong().copy(id = "123", contentUriString = "content://media/123", path = "/music/track.mp3")
        saved[local.id] = local
        val selected = listOf(local.id, songs[8].id, songs[2].id)
        assertEquals(selected, resolver.resolve(selected, songs + local, requireComplete = true).map { it.id })
        assertEquals(local, saved[local.id])
        coVerify { music.cacheCatalogSongs(match { it.map { s -> s.id }.toSet() == setOf(songs[8].id, songs[2].id) }) }
    }
    @Test fun `failed metadata persistence prevents misleading complete playlist`() = runTest {
        val resolver = resolver()
        coEvery { music.cacheCatalogSongs(any()) } just Runs
        var failed = false
        try { resolver.resolve(songs.map { it.id }, songs, requireComplete = true) } catch (_: IOException) { failed = true }
        assertTrue(failed)
        assertTrue(saved.isEmpty())
    }
}
