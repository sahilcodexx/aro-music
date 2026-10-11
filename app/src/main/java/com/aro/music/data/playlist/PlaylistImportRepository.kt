package com.aro.music.data.playlist

import androidx.room.withTransaction
import com.aro.music.data.database.AroDatabase
import com.aro.music.data.innertube.InnerTubeRepository
import com.aro.music.data.model.Playlist
import com.aro.music.data.preferences.PlaylistPreferencesRepository
import com.aro.music.data.repository.MusicRepository
import com.aro.music.spotify.SpotifyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PlaylistImportRepository @Inject constructor(
    private val youtube: InnerTubeRepository,
    private val spotify: SpotifyRepository,
    private val music: MusicRepository,
    private val playlists: PlaylistPreferencesRepository,
    private val database: AroDatabase,
) {
    data class Progress(val processed: Int, val total: Int? = null)
    data class Imported(val playlist: Playlist, val notAdded: Int = 0)
    enum class Failure { INVALID_LINK, CONNECT_SPOTIFY, UNAVAILABLE, EMPTY, SAVE_FAILED }
    class ImportException(val failure: Failure) : Exception(failure.name)

    suspend fun import(link: String, onProgress: (Progress) -> Unit = {}): Imported = withContext(Dispatchers.IO) {
        val parsed = PlaylistLink.parse(link) ?: throw ImportException(Failure.INVALID_LINK)
        var total = 0
        val (remote, songs) = when (parsed.provider) {
            PlaylistLink.Provider.YOUTUBE -> {
                val result = youtube.getRemotePlaylist("VL${parsed.id}", Int.MAX_VALUE) { count -> onProgress(Progress(count)) }
                    ?: throw ImportException(Failure.UNAVAILABLE)
                total = result.songs.size
                result.playlist to result.songs
            }
            PlaylistLink.Provider.SPOTIFY -> {
                val result = spotify.resolvePlaylist("spotify:${parsed.id}", onProgress = { processed, count -> onProgress(Progress(processed, count)) }, forceRefresh = true)
                    ?: throw ImportException(Failure.CONNECT_SPOTIFY)
                total = result.totalTracks
                result.playlist to result.songs
            }
        }
        val unique = songs.distinctBy { it.id }
        if (unique.isEmpty()) throw ImportException(Failure.EMPTY)
        music.cacheCatalogSongs(unique)
        val available = music.getSongsByIds(unique.map { it.id }).first().map { it.id }.toSet()
        if (unique.any { it.id !in available }) throw ImportException(Failure.SAVE_FAILED)
        val existing = playlists.userPlaylistsFlow.first().find { it.id == parsed.localId }
        val saved = database.withTransaction {
            if (existing == null) {
                playlists.createPlaylist(name = remote.name, songIds = unique.map { it.id },
                    coverImageUri = remote.coverImageUri, customId = parsed.localId)
            } else {
                val updated = existing.copy(songIds = unique.map { it.id }, coverImageUri = remote.coverImageUri ?: existing.coverImageUri)
                playlists.updatePlaylist(updated)
                updated
            }
        }
        Imported(saved, (total - unique.size).coerceAtLeast(0))
    }
}
