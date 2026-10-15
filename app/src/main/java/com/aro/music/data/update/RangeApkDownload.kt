package com.aro.music.data.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*

/** Four bounded HTTP ranges, retained across retries; only a verified file is installable. */
class RangeApkDownload {
    suspend fun download(url: String, sha256: String, directory: File, progress: (Long, Long) -> Unit = { _, _ -> }): File = withContext(Dispatchers.IO) {
        require(sha256.matches(Regex("[0-9a-f]{64}")))
        directory.mkdirs()
        val target = File(directory, "update.apk")
        if (target.exists() && digest(target) == sha256) return@withContext target
        target.delete()
        val total = connection(url, 0, 0).let { probe ->
            try {
                checkRange(probe, 0, 0).also { check(it in 1..350_000_000L) { "Invalid APK size" } }
            } finally { probe.disconnect() }
        }
        val chunk = (total + 3) / 4
        val parts = (0..3).mapNotNull { index ->
            val start = index * chunk
            if (start >= total) null else Triple(start, minOf(total - 1, start + chunk - 1), File(directory, "part-$index"))
        }
        parts.forEach { (start, end, file) -> if (file.length() > end - start + 1) file.delete() }
        val completed = AtomicLong(parts.sumOf { it.third.length() })
        progress(completed.get(), total)
        coroutineScope {
            parts.map { (start, end, file) -> async {
                var attempt = 0
                while (file.length() < end - start + 1) {
                    ensureActive()
                    val offset = start + file.length()
                    val remote = connection(url, offset, end)
                    try {
                        if (checkRange(remote, offset, end) != total) throw IOException("APK changed during download")
                        remote.inputStream.use { input ->
                            java.io.FileOutputStream(file, true).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var remaining = end - offset + 1
                                while (remaining > 0) {
                                    ensureActive()
                                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                    if (n < 0) throw IOException("Incomplete range")
                                    output.write(buffer, 0, n)
                                    remaining -= n
                                    progress(completed.addAndGet(n.toLong()), total)
                                }
                                if (input.read() != -1) throw IOException("Oversized range")
                            }
                        }
                    } catch (error: IOException) {
                        if (++attempt >= 3) throw error
                        delay(1000L * attempt)
                    } finally { remote.disconnect() }
                }
            } }.awaitAll()
        }
        target.outputStream().buffered().use { output ->
            parts.forEach { (_, _, file) -> file.inputStream().use { it.copyTo(output) } }
        }
        if (digest(target) != sha256) {
            target.delete()
            parts.forEach { it.third.delete() }
            throw IOException("APK checksum mismatch")
        }
        parts.forEach { it.third.delete() }
        target
    }

    private fun connection(url: String, start: Long, end: Long): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 25_000
        setRequestProperty("Range", "bytes=$start-$end")
        setRequestProperty("Accept-Encoding", "identity")
        setRequestProperty("User-Agent", "Aro-Updater")
    }

    private fun checkRange(connection: HttpURLConnection, start: Long, end: Long): Long {
        if (connection.responseCode != 206) throw IOException("Server did not honor byte range")
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(connection.getHeaderField("Content-Range").orEmpty())
            ?: throw IOException("Missing byte range")
        if (match.groupValues[1].toLong() != start || match.groupValues[2].toLong() != end) throw IOException("Incorrect byte range")
        return match.groupValues[3].toLong()
    }

    private fun digest(file: File): String = file.inputStream().use { input ->
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
        hash.digest().joinToString("") { "%02x".format(it) }
    }
}
