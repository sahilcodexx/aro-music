package com.aro.music.spotify

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpotifyResultTest {
    @Test fun `network failures remain results`() {
        val failure = java.io.IOException("offline")
        assertSame(failure, spotifyRunCatching<Unit> { throw failure }.exceptionOrNull())
    }
    @Test fun `cancellation propagates instead of starting fallback requests`() {
        assertThrows(CancellationException::class.java) {
            spotifyRunCatching<Unit> { throw CancellationException("queue replaced") }
        }
    }
}
