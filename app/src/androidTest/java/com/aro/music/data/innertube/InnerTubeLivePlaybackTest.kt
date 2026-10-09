package com.aro.music.data.innertube

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Opt-in network smoke test: -e live true. Uses muted playback and retains user data. */
@RunWith(AndroidJUnit4::class)
class InnerTubeLivePlaybackTest {
    @Test fun homeShelvesAndHighResolutionArtwork() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "true")
        val engine = InnerTubeEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        val repository = InnerTubeRepository(engine, InnerTubeBrowseRegistry(InstrumentationRegistry.getInstrumentation().targetContext))
        try {
            runBlocking {
                withTimeout(120_000) {
                    val home = repository.getHomePage()
                    assertTrue("Home must retain catalog shelves", home.sections.isNotEmpty())
                    val explore = repository.getExploreSections()
                    val album = explore.flatMap { it.items }.filterIsInstance<HomeCatalogItem.AlbumItem>().first().album
                    val albumBrowseId = requireNotNull(repository.browseIdForAlbum(album.id))
                    assertTrue("Album tile must open a real track list", repository.getAlbumTracks(albumBrowseId).isNotEmpty())
                    val playlist = home.sections.flatMap { it.items }.filterIsInstance<HomeCatalogItem.PlaylistItem>().firstOrNull()?.playlist
                    if (playlist != null) {
                        assertTrue("Playlist tile must open a real track list", repository.getPlaylistTracks(playlist.browseId).isNotEmpty())
                    }
                    if (home.continuation != null) {
                        assertTrue("Home pagination must return more shelves", repository.getHomePage(home.continuation).sections.isNotEmpty())
                    }
                    val radio = repository.radioPage("DX15AR5-E4A")
                    assertTrue("Radio covers must not be discarded", radio.songs.take(10).all { !it.albumArtUriString.isNullOrBlank() })
                    val url = requireNotNull(radio.songs.first().albumArtUriString)
                    val connection = java.net.URL(url).openConnection().apply { connectTimeout = 15_000; readTimeout = 15_000 }
                    val dimensions = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    connection.getInputStream().use { android.graphics.BitmapFactory.decodeStream(it, null, dimensions) }
                    assertTrue("Player artwork must load at high resolution, got ${dimensions.outWidth}x${dimensions.outHeight}", dimensions.outWidth >= 1000 && dimensions.outHeight >= 1000)
                }
            }
        } finally { engine.shutdown() }
    }

    @Test fun homeRadioAndBhagwaanAudio() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val engine = InnerTubeEngine(context)
        val repository = InnerTubeRepository(engine, InnerTubeBrowseRegistry(InstrumentationRegistry.getInstrumentation().targetContext))
        val seed = "DX15AR5-E4A"
        try {
            val stream = runBlocking {
                withTimeout(90_000) {
                    val home = repository.getTrendingSongs(20)
                    assertTrue("Home must resolve actual playable shelf tracks", home.isNotEmpty())
                    val radio = repository.radioPage(seed)
                    assertEquals("Bhagwaan", radio.songs.first().title)
                    assertTrue("Radio should include related rap songs", radio.songs.any { it.title == "WDTBU" || it.title == "Karta Kya Hai" })
                    requireNotNull(repository.resolveStream(seed)) { "Seed stream did not resolve" }
                }
            }
            val result = AtomicReference<Throwable?>()
            val decoded = CountDownLatch(1)
            lateinit var player: ExoPlayer
            val handler = Handler(Looper.getMainLooper())
            instrumentation.runOnMainSync {
                val ordinary = DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory().setDefaultRequestProperties(stream.headers))
                val factory = DataSource.Factory { InnerTubeTransportDataSource(ordinary.createDataSource(), InnerTubeSabrDataSource(engine)) }
                player = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build()
                player.volume = 0f
                player.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) { result.set(error); decoded.countDown() }
                })
                player.setMediaItem(MediaItem.fromUri(stream.audioUrl))
                player.prepare()
                player.play()
                handler.post(object : Runnable {
                    override fun run() {
                        if (player.currentPosition >= 1500L) decoded.countDown()
                        else if (decoded.count > 0) handler.postDelayed(this, 200)
                    }
                })
            }
            try {
                assertTrue("Audio must decode and advance beyond 0:00", decoded.await(60, TimeUnit.SECONDS))
                result.get()?.let { throw AssertionError("Real playback failed", it) }
            } finally {
                instrumentation.runOnMainSync { handler.removeCallbacksAndMessages(null); player.release() }
            }
        } finally {
            engine.shutdown()
        }
    }
}
