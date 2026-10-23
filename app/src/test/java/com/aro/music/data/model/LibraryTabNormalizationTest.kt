package com.aro.music.data.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LibraryTabNormalizationTest {
    @Test fun `legacy folders alias does not create a second artists tab`() {
        val result = normalizeLibraryTabOrder(listOf("LIKED", "FOLDERS", "ARTIST", "ARTISTS", "UNKNOWN", "SONGS"))
        assertEquals("LIKED", result.first())
        assertEquals(1, result.count { it == "ARTIST" })
        assertFalse(result.contains("FOLDERS"))
        assertFalse(result.contains("UNKNOWN"))
        assertEquals(6, result.size)
        assertEquals(result, normalizeLibraryTabOrder(result))
        assertEquals(LibraryTabId.ARTISTS, LibraryTabId.fromStorageKey("FOLDERS"))
        assertEquals(LibraryTabId.ARTISTS, "FOLDERS".toLibraryTabIdOrNull())
    }
}
