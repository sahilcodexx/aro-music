package com.aro.music.data.download

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DownloadedCollectionsTest {
    @Test fun `all songs includes standalone and collection downloads without leaking to other pages`() {
        val collection = DownloadCollection("playlist:one", "Playlist", DownloadCollectionKind.PLAYLIST, listOf("b", "a", "missing"))
        val owned = linkedSetOf("standalone", "11", "12")
        val records = mapOf("a" to "11", "b" to "12", "missing" to "deleted")
        assertEquals(listOf("standalone", "11", "12"), downloadedSongIds(ALL_DOWNLOADED_SONGS, listOf(collection), records, owned))
        assertEquals(listOf("12", "11"), downloadedSongIds(collection.id, listOf(collection), records, owned))
        assertEquals(emptyList<String>(), downloadedSongIds("deleted-playlist", listOf(collection), records, owned))
    }

    @Test fun `collection cards retain metadata ordering and independent membership across recreation`() = runTest {
        val dir = Files.createTempDirectory("aro-download-collections")
        try {
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope,
                produceFile = { dir.resolve("settings.preferences_pb").toFile() })
            val liked = stringSetPreferencesKey("favorite_song_ids")
            store.edit { it[liked] = setOf("untouched") }
            val repo = DownloadedCollections(store, mockk(), mockk())
            val album = DownloadCollection("album:1", "Release", DownloadCollectionKind.ALBUM, listOf("first", "second", "first"))
            val playlist = DownloadCollection("playlist:1", "My playlist", DownloadCollectionKind.PLAYLIST, listOf("second", "first"))
            repo.save(album)
            repo.save(playlist)
            val reopened = DownloadedCollections(store, mockk(), mockk())
            val cards = reopened.collections.first()
            assertEquals(2, cards.size)
            assertEquals(listOf("first", "second"), cards.first().songIds)
            assertEquals(listOf("second", "first"), cards.last().songIds)
            repo.save(playlist.copy(title = "Renamed"))
            assertEquals(2, reopened.collections.first().size)
            assertEquals(setOf("untouched"), store.data.first()[liked])
        } finally { dir.toFile().deleteRecursively() }
    }

    @Test fun `offline collection only plays existing owned copies in original track order`() {
        val card = DownloadCollection("playlist:1", "Playlist", DownloadCollectionKind.PLAYLIST, listOf("a", "b", "c", "a"))
        assertEquals(listOf("11", "10"), card.localIds(mapOf("a" to "11", "b" to "10", "c" to "deleted"), setOf("10", "11")))
        assertEquals(emptyList<String>(), card.localIds(mapOf("a" to "11"), emptySet()))
    }
}
