package com.aro.music.presentation.sharing

import com.aro.music.data.model.Lyrics
import com.aro.music.data.model.SyncedLine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LyricCardSelectionTest {
    @Test fun `synced lines are cleaned without removing repeated verses`() {
        val lyrics = Lyrics(plain = listOf("Fallback"), synced = listOf(
            SyncedLine(0, "  "), SyncedLine(1, "[00:01.00]First"),
            SyncedLine(2, "First"), SyncedLine(3, "Second\nThird")))
        assertEquals(listOf("First", "First", "Second", "Third"), lyricCardLines(lyrics))
        assertEquals(listOf("Plain"), lyricCardLines(Lyrics(plain = listOf("\n Plain "), synced = emptyList())))
        assertTrue(lyricCardLines(null).isEmpty())
    }
    @Test fun `selection preserves song order even when tapped backwards`() {
        assertEquals(listOf("One", "Three"), selectedLyricLines(listOf("One", "Two", "Three"), linkedSetOf(2, 0)))
    }
    @Test fun `excerpt bounds include separators and prevent empty or excessive selections`() {
        assertFalse(isValidLyricExcerpt(emptyList()))
        assertFalse(isValidLyricExcerpt(listOf(" ")))
        assertTrue(isValidLyricExcerpt(List(6) { "Line" }))
        assertFalse(isValidLyricExcerpt(List(7) { "Line" }))
        assertTrue(isValidLyricExcerpt(listOf("A".repeat(300))))
        assertFalse(isValidLyricExcerpt(listOf("A".repeat(300), "B")))
        assertFalse(isValidLyricExcerpt(listOf("One\nTwo")))
    }
}
