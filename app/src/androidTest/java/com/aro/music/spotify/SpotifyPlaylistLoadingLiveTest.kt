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
class SpotifyPlaylistLoadingLiveTest {
    @Test fun fullPlaylistReopensFromDiskAndFirstLoadShowsProgressiveTracks() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val entry = EntryPointAccessors.fromApplication(context, PlaylistImportEntryPoint::class.java)
        runBlocking {
            withTimeout(180000) {
                assertTrue(entry.session().ensureAuthenticated())
                val playlists = entry.spotify().libraryPlaylistsFlow.first()
                val sample = requireNotNull(playlists.filter { it.id != "spotify:liked" && it.remoteSongCount in 20..100 }.minByOrNull { it.remoteSongCount!! })
                val batches = mutableListOf<Int>()
                val started = System.nanoTime()
                var firstBatchMs: Long? = null
                val first = entry.spotify().resolvePlaylist(sample.id, forceRefresh = true, onPartial = {
                    batches += it.songs.size
                    if (it.songs.isNotEmpty() && firstBatchMs == null) firstBatchMs = (System.nanoTime() - started) / 1000000
                })!!
                val fullMs = (System.nanoTime() - started) / 1000000
                assertTrue(first.songs.size > 4)
                assertEquals(0, batches.first())
                assertTrue(batches.any { it in 1 until first.songs.size })
                assertEquals(first, SpotifyPlaylistCache(context).read(sample.id))
                val cachedStart = System.nanoTime()
                val again = entry.spotify().resolvePlaylist(sample.id)!!
                val cachedMs = (System.nanoTime() - cachedStart) / 1000000
                assertEquals(first, again)
                assertTrue("Cached reopen should avoid network and mapping ($cachedMs ms)", cachedMs < 1500)
                println("Spotify playlist loading verified: ${first.songs.size} tracks; first batch ${firstBatchMs}ms; full ${fullMs}ms; reopen ${cachedMs}ms")
            }
        }
    }
}
