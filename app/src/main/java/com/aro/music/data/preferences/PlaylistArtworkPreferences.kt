package com.aro.music.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aro.music.data.model.Playlist
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.playlistArtworkDataStore by preferencesDataStore(name = "playlist_artwork")

/** Local artwork overrides for remote playlists; never edits the provider's playlist. */
@Singleton
class PlaylistArtworkPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val overridesKey = stringPreferencesKey("remote_overrides")

    private fun decode(value: String?): Map<String, String> =
        value?.let { runCatching { Json.decodeFromString<Map<String, String>>(it) }.getOrNull() }.orEmpty()

    val overridesFlow = context.playlistArtworkDataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { decode(it[overridesKey]) }

    suspend fun set(playlistId: String, artwork: String?) {
        context.playlistArtworkDataStore.edit { preferences ->
            // Empty string deliberately means first-song art, rather than the provider cover.
            val overrides = decode(preferences[overridesKey]) + (playlistId to artwork.orEmpty())
            preferences[overridesKey] = Json.encodeToString(overrides)
        }
    }

    fun apply(playlist: Playlist, overrides: Map<String, String>): Playlist =
        if (overrides.containsKey(playlist.id)) playlist.copy(coverImageUri = overrides[playlist.id]?.takeIf(String::isNotBlank))
        else playlist
}
