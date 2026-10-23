package com.aro.music.data.download

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AudioDownloadsTest {
    @Test fun `copy rejects incomplete oversized and empty audio and preserves bytes`() = kotlinx.coroutines.runBlocking {
        val bytes = ByteArray(150_000) { (it % 251).toByte() }
        val output = java.io.ByteArrayOutputStream()
        var progress = 0L
        assertEquals(bytes.size.toLong(), copyAudio(bytes.inputStream(), output, 200_000, bytes.size.toLong()) { progress = it })
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(bytes.size.toLong(), progress)
        for ((data, limit, expected) in listOf(Triple(byteArrayOf(), 10L, null), Triple(bytes, 200_000L, 150_001L), Triple(bytes, 100L, null))) {
            assertThrows(java.io.IOException::class.java) { kotlinx.coroutines.runBlocking { copyAudio(data.inputStream(), java.io.ByteArrayOutputStream(), limit, expected) {} } }
        }
    }

    @Test fun `Opus uses an audio library compatible container instead of rejected audio webm`() {
        assertEquals("audio/ogg", downloadAudioMime("audio/webm", "opus"))
        assertEquals("audio/ogg", downloadAudioMime("audio/opus", null))
        assertEquals("audio/mp4", downloadAudioMime("audio/mp4", "mp4a.40.2"))
        assertEquals("Title.opus", audioFileName("Title", "opus"))
        assertThrows(IllegalArgumentException::class.java) { downloadAudioMime("application/x-mpegURL", null) }
    }

    @Test fun `audio filenames cannot traverse folders or inject controls`() {
        val file = audioFileName("../Singer/Title\\Mix\n\u0000?", "m4a")
        assertFalse(file.contains('/'))
        assertFalse(file.contains('\\'))
        assertFalse(file.any(Char::isISOControl))
        assertTrue(file.endsWith(".m4a"))
        assertEquals("Audio.webm", audioFileName("   ", "webm"))
        assertEquals(104, audioFileName("a".repeat(150), "m4a").length)
        assertThrows(IllegalArgumentException::class.java) { audioFileName("Title", "../exe") }
    }
}
