package com.aro.music.spotify

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aro.music.data.playlist.PlaylistImportEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpotifyLibraryCountsLiveTest {
    @Test fun libraryCountsMatchSpotifyAndPlaylistTracksResolve() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "true")
        val entry = EntryPointAccessors.fromApplication(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext, PlaylistImportEntryPoint::class.java)
        runBlocking {
            withTimeout(180_000) {
                assertTrue("Connect Spotify before this live check", entry.session().ensureAuthenticated())
                val playlists = entry.spotify().libraryPlaylistsFlow.first()
                assertTrue(playlists.isNotEmpty())
                assertTrue("Connected library counts must be available", playlists.all { it.remoteSongCount != null })
                val liked = playlists.single { it.id == "spotify:liked" }
                assertEquals(Spotify.likedSongs(1, 0).getOrThrow().total, liked.remoteSongCount)
                val sample = playlists.filter { it.id != "spotify:liked" && (it.remoteSongCount ?: 0) > 0 }.minByOrNull { it.remoteSongCount!! }
                requireNotNull(sample) { "No nonempty playlist available" }
                val count = Spotify.playlistTracks(sample.id.removePrefix("spotify:"), 1, 0).getOrThrow().total
                assertEquals(count, sample.remoteSongCount)
                val resolved = entry.spotify().resolvePlaylist(sample.id, maxTracks = 3)!!
                assertEquals(count, resolved.playlist.remoteSongCount)
                assertTrue("Opening a nonempty playlist must resolve playable tracks", resolved.songs.isNotEmpty())
                assertTrue(resolved.songs.all { it.contentUriString.startsWith("innertube://") })
                println("Spotify library counts verified: ${playlists.size} entries; playlist preview resolves playable tracks")
            }
        }
    }
}
