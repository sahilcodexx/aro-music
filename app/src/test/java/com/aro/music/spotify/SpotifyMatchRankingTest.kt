package com.aro.music.spotify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpotifyMatchRankingTest {
    @Test fun `studio candidate ranks before unwanted live cover`() {
        assertEquals(0.30, SpotifyMatchRanking.variantPenalty("Imagine", "Imagine Live Cover"), 0.0001)
        assertEquals(0.0, SpotifyMatchRanking.variantPenalty("Imagine", "Imagine Official Audio"), 0.0001)
    }
    @Test fun `intentional live version has no penalty`() {
        assertEquals(0.0, SpotifyMatchRanking.variantPenalty("Imagine Live", "Imagine Live"), 0.0001)
    }
    @Test fun `markers only match whole words`() {
        assertEquals(0.0, SpotifyMatchRanking.variantPenalty("Song", "Alive"), 0.0001)
    }
}
