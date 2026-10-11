package com.aro.music.data.playlist

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aro.music.data.innertube.InnerTubeRepository
import com.aro.music.data.preferences.PlaylistPreferencesRepository
import com.aro.music.data.repository.MusicRepository
import com.aro.music.spotify.SpotifyRepository
import com.aro.music.spotify.SpotifySession
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistImportLiveTest {
    private fun entry() = EntryPointAccessors.fromApplication(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext, PlaylistImportEntryPoint::class.java)

    @Test fun youtubeLinkSavesOrderedTracksAndReimportUpdatesOneCopy() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "true")
        val entry = entry()
        runBlocking {
            withTimeout(180_000) {
                val candidates = entry.youtube().searchPlaylists("Indian hip hop", 10)
                println("YouTube playlist candidates: ${candidates.map { it.browseId }}")
                val remote = requireNotNull(candidates.filter { it.browseId.startsWith("VL") && !it.browseId.startsWith("VLRDAMVM") }
                    .minByOrNull { it.songCount?.takeIf { count -> count > 0 } ?: Int.MAX_VALUE }) { "No accessible playlist returned" }
                val link = "https://music.youtube.com/playlist?list=${remote.browseId.removePrefix("VL")}"
                val parsed = PlaylistLink.parse(link)!!
                val before = entry.playlists().userPlaylistsFlow.first().find { it.id == parsed.localId }
                val libraryBefore = entry.music().getAudioFiles().first().map { it.id }.toSet()
                try {
                    val imported = entry.importer().import(link)
                    assertTrue(imported.playlist.songIds.isNotEmpty())
                    val songs = entry.music().getSongsByIds(imported.playlist.songIds).first()
                    assertEquals(imported.playlist.songIds, songs.map { it.id })
                    assertTrue(songs.all { it.contentUriString.startsWith("innertube://") && !it.albumArtUriString.isNullOrBlank() })
                    assertEquals(libraryBefore + imported.playlist.songIds, entry.music().getAudioFiles().first().map { it.id }.toSet())
                    val again = entry.importer().import(link)
                    assertEquals(imported.playlist.id, again.playlist.id)
                    assertEquals(1, entry.playlists().userPlaylistsFlow.first().count { it.id == again.playlist.id })
                    assertEquals(imported.playlist.songIds, again.playlist.songIds)
                } finally {
                    if (before == null) entry.playlists().deletePlaylist(parsed.localId) else entry.playlists().updatePlaylist(before)
                }
                assertEquals(libraryBefore, entry.music().getAudioFiles().first().map { it.id }.toSet())
            }
        }
    }

    @Test fun spotifyImportUsesAccountOrShowsConnectState() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "true")
        val entry = entry()
        runBlocking {
            withTimeout(240_000) {
                if (!entry.session().ensureAuthenticated()) {
                    try {
                        entry.importer().import("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")
                        fail("A disconnected account must show Connect Spotify")
                    } catch (e: PlaylistImportRepository.ImportException) {
                        assertEquals(PlaylistImportRepository.Failure.CONNECT_SPOTIFY, e.failure)
                    }
                    println("Spotify import verified: account connection required")
                } else {
                    val remote = com.aro.music.spotify.Spotify.search("Karma", types = listOf("playlist"), limit = 10).getOrThrow().playlists!!.items
                        .filter { it.tracks?.total in 1..60 }.minByOrNull { it.tracks!!.total }
                    requireNotNull(remote) { "No small playlist available for Spotify import verification" }
                    val link = "https://open.spotify.com/playlist/${remote.id}"
                    val id = PlaylistLink.parse(link)!!.localId
                    val before = entry.playlists().userPlaylistsFlow.first().find { it.id == id }
                    try {
                        val imported = entry.importer().import(link)
                        assertTrue(imported.playlist.songIds.isNotEmpty())
                        assertEquals(imported.playlist.songIds, entry.music().getSongsByIds(imported.playlist.songIds).first().map { it.id })
                        println("Spotify import verified: ${imported.playlist.songIds.size} saved tracks")
                    } finally {
                        if (before == null) entry.playlists().deletePlaylist(id) else entry.playlists().updatePlaylist(before)
                    }
                }
            }
        }
    }
}
