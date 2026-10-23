package com.aro.music.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.aro.music.data.model.Album
import com.aro.music.data.model.Artist
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SavedLibraryRepositoryTest {
    @Test fun `album artist and song likes remain separate across recreation`() = runTest {
        val directory = Files.createTempDirectory("aro-saved-library")
        try {
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope,
                produceFile = { directory.resolve("settings.preferences_pb").toFile() })
            val liked = stringSetPreferencesKey("favorite_song_ids")
            store.edit { it[liked] = setOf("song-1", "song-2") }
            val repository = SavedLibraryRepository(store)
            assertTrue(repository.albums.first().isEmpty())
            assertTrue(repository.artists.first().isEmpty())
            val album = Album(42, "NO FORMULA", "Raga", 2026, 1, null, 10)
            val artist = Artist(7, "Raga", 10)
            repository.toggleAlbum(album)
            assertEquals(listOf(album), repository.albums.first())
            assertTrue(repository.artists.first().isEmpty())
            repository.toggleArtist(artist)
            val reopened = SavedLibraryRepository(store)
            assertEquals(listOf(album), reopened.albums.first())
            assertEquals(listOf(artist), reopened.artists.first())
            repository.toggleAlbum(album)
            assertTrue(reopened.albums.first().isEmpty())
            assertEquals(listOf(artist), reopened.artists.first())
            assertEquals(setOf("song-1", "song-2"), store.data.first()[liked])
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun `hidden Spotify playlists survive recreation and restore only on request`() = runTest {
        val directory = Files.createTempDirectory("aro-hidden-spotify")
        try {
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope,
                produceFile = { directory.resolve("settings.preferences_pb").toFile() })
            val repository = SavedLibraryRepository(store)
            repository.hideSpotifyPlaylist("spotify:mix")
            repository.hideSpotifyPlaylist("spotify:mix")
            val reopened = SavedLibraryRepository(store)
            assertEquals(setOf("spotify:mix"), reopened.hiddenSpotifyPlaylists.first())
            repository.restoreSpotifyPlaylists()
            assertTrue(reopened.hiddenSpotifyPlaylists.first().isEmpty())
            assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { repository.hideSpotifyPlaylist("local:mix") } }
        } finally { directory.toFile().deleteRecursively() }
    }
}
