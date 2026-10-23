package com.aro.music.data.download

import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal suspend fun copyAudio(input: InputStream, output: OutputStream, limit: Long, expected: Long?, progress: suspend (Long) -> Unit): Long {
    val buffer = ByteArray(64 * 1024)
    var copied = 0L
    while (true) {
        coroutineContext.ensureActive()
        val count = input.read(buffer)
        if (count == -1) break
        copied += count
        if (copied > limit || (expected != null && copied > expected)) throw IOException("Audio exceeds expected size")
        output.write(buffer, 0, count)
        progress(copied)
    }
    if (copied == 0L || (expected != null && copied != expected)) throw IOException("Incomplete audio")
    return copied
}
