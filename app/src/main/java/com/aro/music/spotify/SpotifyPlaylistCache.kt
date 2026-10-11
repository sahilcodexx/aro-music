package com.aro.music.spotify

import android.content.Context
import androidx.annotation.Keep
import com.aro.music.data.model.Playlist
import com.aro.music.data.model.Song
import com.google.gson.Gson

/** Completed playlist snapshots; partial previews never replace a complete saved snapshot. */
internal class SpotifyPlaylistCache(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gson = Gson()
    fun read(id: String): SpotifyRepository.ResolvedPlaylist? = runCatching {
        val snapshot = preferences.getString(id, null)?.let { gson.fromJson(it, Snapshot::class.java) }
            ?: return null
        if (!snapshot.isFresh() || snapshot.playlist.id != id || snapshot.playlist.songIds != snapshot.songs.map { it.id }) return null
        SpotifyRepository.ResolvedPlaylist(snapshot.playlist, snapshot.songs, snapshot.totalTracks)
    }.getOrNull()
    fun write(result: SpotifyRepository.ResolvedPlaylist) {
        preferences.edit().putString(result.playlist.id, gson.toJson(Snapshot(System.currentTimeMillis(), result.playlist, result.songs, result.totalTracks))).apply()
    }
    @Keep data class Snapshot(val fetchedAt: Long, val playlist: Playlist, val songs: List<Song>, val totalTracks: Int) {
        fun isFresh(now: Long = System.currentTimeMillis()) = now - fetchedAt in 0 until MAX_AGE_MS
    }
    companion object {
        const val PREFERENCES = "spotify_playlist_snapshots"
        const val MAX_AGE_MS = 6 * 60 * 60_000L
    }
}
