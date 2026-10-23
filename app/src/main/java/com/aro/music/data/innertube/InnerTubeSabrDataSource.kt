package com.aro.music.data.innertube

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

/** Bridges InnerTubeX's demultiplexed SABR audio into Media3's byte reader. */
class InnerTubeSabrDataSource(private val engine: InnerTubeEngine, private val forDownload: Boolean = false) : BaseDataSource(true) {
    private var uri: Uri? = null
    private var scope: CoroutineScope? = null
    private var chunks: Channel<ByteArray>? = null
    private var current = ByteArray(0)
    private var offset = 0
    private var skip = 0L
    private var remaining = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        val videoId = requireNotNull(dataSpec.uri.host) { "SABR URI has no video ID" }
        val audio = runBlocking(Dispatchers.IO) { engine.sabrAudio(videoId, forDownload) }
        val channel = Channel<ByteArray>(2)
        chunks = channel
        skip = dataSpec.position
        remaining = dataSpec.length
        current = ByteArray(0)
        offset = 0
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { producer ->
            producer.launch {
                try {
                    audio.collect { channel.send(it) }
                    channel.close()
                } catch (error: Throwable) {
                    channel.close(error)
                }
            }
        }
        opened = true
        transferStarted(dataSpec)
        return dataSpec.length
    }

    override fun read(buffer: ByteArray, bufferOffset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        while (offset >= current.size) {
            val received = runBlocking { chunks?.receiveCatching() } ?: throw IOException("SABR source is closed")
            val next = received.getOrNull() ?: run {
                received.exceptionOrNull()?.let { throw IOException("SABR audio request failed", it) }
                return C.RESULT_END_OF_INPUT
            }
            current = next
            offset = minOf(skip, next.size.toLong()).toInt()
            skip -= offset
        }
        val count = minOf(length, current.size - offset, if (remaining == C.LENGTH_UNSET.toLong()) Int.MAX_VALUE else remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        current.copyInto(buffer, bufferOffset, offset, offset + count)
        offset += count
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        scope?.cancel()
        chunks?.cancel()
        scope = null
        chunks = null
        current = ByteArray(0)
        uri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
