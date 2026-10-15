package com.aro.music.data.update

import com.google.common.truth.Truth.assertThat
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RangeApkDownloadTest {
    private val bytes = ByteArray(200_003) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun server(badRange: Boolean = false, block: (String, CopyOnWriteArrayList<Long>) -> Unit) {
        val offsets = CopyOnWriteArrayList<Long>()
        val executor = Executors.newFixedThreadPool(5)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext("/apk") { exchange ->
            val match = Regex("bytes=(\\d+)-(\\d+)").matchEntire(exchange.requestHeaders.getFirst("Range"))!!
            val start = match.groupValues[1].toInt(); val end = match.groupValues[2].toInt()
            offsets += start.toLong()
            exchange.responseHeaders.add("Content-Range", "bytes ${if (badRange) start + 1 else start}-$end/${bytes.size}")
            exchange.sendResponseHeaders(206, (end - start + 1).toLong())
            try { exchange.responseBody.use { it.write(bytes, start, end - start + 1) } } finally { exchange.close() }
        }
        server.start()
        try { block("http://127.0.0.1:${server.address.port}/apk", offsets) }
        finally { server.stop(0); executor.shutdownNow() }
    }

    @Test fun `parallel ranges resume existing bytes and produce exact verified APK`() = server { url, offsets ->
        val dir = Files.createTempDirectory("aro-range").toFile()
        try {
            dir.resolve("part-0").writeBytes(bytes.copyOfRange(0, 1234))
            val result = runBlocking { RangeApkDownload().download(url, sha, dir) }
            assertThat(result.readBytes()).isEqualTo(bytes)
            assertThat(offsets).contains(1234L)
            assertThat(dir.resolve("part-0").exists()).isFalse()
        } finally { dir.deleteRecursively() }
    }

    @Test fun `wrong Content-Range cannot be saved as an installable APK`() = server(badRange = true) { url, _ ->
        val dir = Files.createTempDirectory("aro-bad-range").toFile()
        try {
            assertThrows<java.io.IOException> { runBlocking { RangeApkDownload().download(url, sha, dir) } }
            assertThat(dir.resolve("update.apk").exists()).isFalse()
        } finally { dir.deleteRecursively() }
    }

    @Test fun `digest mismatch discards both output and corrupt resume parts`() = server { url, _ ->
        val dir = Files.createTempDirectory("aro-bad-digest").toFile()
        try {
            assertThrows<java.io.IOException> { runBlocking { RangeApkDownload().download(url, "0".repeat(64), dir) } }
            assertThat(dir.listFiles().orEmpty()).isEmpty()
        } finally { dir.deleteRecursively() }
    }
}
