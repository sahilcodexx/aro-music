package com.aro.music.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files

class HomeLayoutPreferencesTest {
    @Test fun `unknown and duplicate sections normalize without hiding the whole home`() {
        val result = normalizeHomeLayout(listOf("STATS", "unknown", "STATS"), HomeSection.entries.map { it.name }.toSet())
        assertEquals(HomeSection.STATS, result.order.first())
        assertEquals(HomeSection.entries.toSet(), result.order.toSet())
        assertEquals(6, result.order.size)
        assertFalse(HomeSection.STATS in result.hidden)
    }
    @Test fun `layout survives recreation without changing unrelated preferences`() = runTest {
        val directory = Files.createTempDirectory("home-layout")
        try {
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { directory.resolve("test.preferences_pb").toFile() })
            val other = stringPreferencesKey("unrelated")
            store.edit { it[other] = "retained" }
            val layout = HomeLayout(HomeSection.entries.reversed(), setOf(HomeSection.DISCOVER, HomeSection.STATS))
            HomeLayoutPreferences(store).save(layout)
            assertEquals(layout, HomeLayoutPreferences(store).layout.first())
            assertEquals("retained", store.data.first()[other])
            HomeLayoutPreferences(store).save(HomeLayout())
            assertEquals(HomeLayout(), HomeLayoutPreferences(store).layout.first())
        } finally { directory.toFile().deleteRecursively() }
    }
}
