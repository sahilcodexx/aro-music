package com.aro.music.playback

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Account-independent checks for the native cipher runtime and actual audio decoding. */
@RunWith(AndroidJUnit4::class)
class NativePlaybackCompatibilityTest {
    @Test fun cipherNativeRuntimeLoadsAndEvaluates() = runBlocking {
        withContext(Dispatchers.Default) {
            val js = QuickJs.create(Dispatchers.Default)
            try {
                js.maxStackSize = 256L * 1024L
                assertEquals("3", js.evaluate<String>("String(1 + 2)"))
            } finally { js.close() }
        }
    }

    @Test fun localAudioDecodesAndAdvancesWithoutNetwork() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val sampleRate = 44100
        val samplesSize = sampleRate * 2 * 2 // Two seconds of silent mono PCM16.
        val wav = ByteBuffer.allocate(44 + samplesSize).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + samplesSize)
            .put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samplesSize).array()
        val file = File.createTempFile("aro-playback-check", ".wav", context.cacheDir)
        file.writeBytes(wav)
        val advanced = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val handler = Handler(Looper.getMainLooper())
        var player: ExoPlayer? = null
        try {
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(context).build()
                player!!.volume = 0f
                player!!.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        failure.set(error)
                        advanced.countDown()
                    }
                })
                player!!.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                player!!.prepare()
                player!!.play()
                handler.post(object : Runnable {
                    override fun run() {
                        if (player!!.currentPosition >= 500) advanced.countDown()
                        else if (advanced.count > 0) handler.postDelayed(this, 100)
                    }
                })
            }
            assertTrue("Audio must decode and advance beyond 0:00", advanced.await(30, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Audio decoder failed", it) }
        } finally {
            instrumentation.runOnMainSync {
                handler.removeCallbacksAndMessages(null)
                player?.release()
            }
            file.delete()
        }
    }
}
