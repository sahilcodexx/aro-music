package com.aro.music.data.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Copy compressed samples into a finalized container; no decoding, gain, or re-encoding. */
internal suspend fun remuxAudio(source: File, output: FileDescriptor, opus: Boolean) {
    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    try {
        extractor.setDataSource(source.absolutePath)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("No audio track")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        if (opus) check(format.getString(MediaFormat.KEY_MIME) == "audio/opus") { "Expected Opus audio" }
        val writer = MediaMuxer(output, if (opus) MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer = writer
        val target = writer.addTrack(format)
        writer.start()
        val buffer = ByteBuffer.allocateDirect(1024 * 1024)
        val info = MediaCodec.BufferInfo()
        val startUs = extractor.sampleTime
        var samples = 0L
        while (true) {
            coroutineContext.ensureActive()
            check(extractor.sampleSize <= buffer.capacity()) { "Audio sample too large" }
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            check(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "Encrypted audio is unsupported" }
            info.set(0, size, (extractor.sampleTime - startUs).coerceAtLeast(0),
                if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            writer.writeSampleData(target, buffer, info)
            samples++
            if (!extractor.advance()) break
        }
        check(samples > 0) { "Empty audio track" }
        writer.stop()
    } finally {
        try { muxer?.release() } finally { extractor.release() }
    }
}
