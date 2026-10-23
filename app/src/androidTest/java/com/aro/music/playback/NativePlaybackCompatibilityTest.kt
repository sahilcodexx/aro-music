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
        assertDecodesAndAdvances(file)
    }

    @Test fun fragmentedDownloadFinalizesWithoutChangingAudioSamples() = runBlocking {
        assertDownloadRemux("download-fragments.m4a", "m4a", false)
    }

    @Test fun opusDownloadUsesOggWithoutChangingAudioSamples() = runBlocking {
        assertDownloadRemux("download-opus.webm", "opus", true)
    }

    private suspend fun assertDownloadRemux(asset: String, extension: String, opus: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("aro-fragments", ".part", context.cacheDir)
        val output = File.createTempFile("aro-finalized", ".$extension", context.cacheDir)
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { input ->
                source.outputStream().use { input.copyTo(it) }
            }
            java.io.FileOutputStream(output).use {
                com.aro.music.data.download.remuxAudio(source, it.fd, opus = opus)
            }
            org.junit.Assert.assertArrayEquals(compressedAudioDigest(source), compressedAudioDigest(output))
            val duration = android.media.MediaMetadataRetriever().use {
                it.setDataSource(output.absolutePath)
                it.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            }
            assertTrue("Finalized audio must retain its full duration", duration >= 1900)
            assertDecodesAndAdvances(output)
        } finally {
            source.delete()
            output.delete()
        }
    }

    private fun compressedAudioDigest(file: File): ByteArray {
        val extractor = android.media.MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            extractor.selectTrack(0)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val buffer = ByteBuffer.allocate(1024 * 1024)
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                buffer.position(0)
                buffer.limit(size)
                digest.update(buffer)
                if (!extractor.advance()) break
            }
            return digest.digest()
        } finally { extractor.release() }
    }

    private fun assertDecodesAndAdvances(file: File) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
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
