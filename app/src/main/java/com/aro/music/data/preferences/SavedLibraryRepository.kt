package com.aro.music.data.preferences

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.aro.music.data.model.Album
import com.aro.music.data.model.Artist
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Explicit saves are independent of song likes and catalog cache membership. */
@Singleton
class SavedLibraryRepository @Inject constructor(private val store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) {
    private val albumsKey = stringPreferencesKey("saved_library_albums_v1")
    private val artistsKey = stringPreferencesKey("saved_library_artists_v1")
    private val hiddenKey = stringSetPreferencesKey("hidden_spotify_playlists_v1")
    private val json = Json { ignoreUnknownKeys = true }
    val albums = store.data.map { json.decodeFromString<List<Album>>(it[albumsKey] ?: "[]") }.distinctUntilChanged()
    val artists = store.data.map { json.decodeFromString<List<Artist>>(it[artistsKey] ?: "[]") }.distinctUntilChanged()
    val hiddenSpotifyPlaylists = store.data.map { it[hiddenKey].orEmpty() }.distinctUntilChanged()

    suspend fun toggleAlbum(album: Album) {
        store.edit {
            val saved = json.decodeFromString<List<Album>>(it[albumsKey] ?: "[]")
            it[albumsKey] = json.encodeToString(if (saved.any { old -> old.id == album.id }) saved.filterNot { old -> old.id == album.id } else saved + album)
        }
    }
    suspend fun toggleArtist(artist: Artist) {
        store.edit {
            val saved = json.decodeFromString<List<Artist>>(it[artistsKey] ?: "[]")
            it[artistsKey] = json.encodeToString(if (saved.any { old -> old.id == artist.id }) saved.filterNot { old -> old.id == artist.id } else saved + artist)
        }
    }
    suspend fun hideSpotifyPlaylist(id: String) {
        require(id.startsWith("spotify:"))
        store.edit { it[hiddenKey] = it[hiddenKey].orEmpty() + id }
    }
    suspend fun restoreSpotifyPlaylists() { store.edit { it.remove(hiddenKey) } }
}
