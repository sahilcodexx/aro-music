package com.aro.music.data.innertube

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InnerTubeArtworkTest {
    @Test fun `resizable google artwork requests player resolution and keeps crop suffix`() {
        assertEquals("https://yt3.googleusercontent.com/cover=w1080-h1080-l90-rj", InnerTubeArtwork.highQuality("https://yt3.googleusercontent.com/cover=w120-h120-l90-rj"))
        assertEquals("https://lh3.googleusercontent.com/cover=s1080", InnerTubeArtwork.highQuality("https://lh3.googleusercontent.com/cover=s192"))
    }
    @Test fun `unrelated local and signed remote URLs retain their original representation`() {
        listOf("content://media/42", "telegram_art://42/33", "https://example.com/image=s192?token=abc", "https://i.scdn.co/image/abc").forEach {
            assertEquals(it, InnerTubeArtwork.highQuality(it))
        }
    }
    @Test fun `previously cached missing youtube artwork gets a video fallback only for youtube songs`() {
        assertEquals("https://i.ytimg.com/vi/seed/hqdefault.jpg", InnerTubeArtwork.forSong(null, "innertube://seed"))
        assertNull(InnerTubeArtwork.forSong(null, "content://media/42"))
        assertEquals("https://example.com/album.jpg", InnerTubeArtwork.forSong("https://example.com/album.jpg", "innertube://seed"))
    }
}
