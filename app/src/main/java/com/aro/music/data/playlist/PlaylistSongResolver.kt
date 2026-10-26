package com.aro.music.data.playlist

import com.aro.music.data.innertube.InnerTubeIds
import com.aro.music.data.model.Song
import com.aro.music.data.preferences.UserPreferencesRepository
import com.aro.music.data.repository.MusicRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Persist metadata at the playlist boundary; playing a track must not be required first. */
class PlaylistSongResolver @Inject constructor(
    private val music: MusicRepository,
    private val preferences: UserPreferencesRepository,
) {
    suspend fun resolve(ids: List<String>, supplied: List<Song> = emptyList(), requireComplete: Boolean = false): List<Song> = withContext(Dispatchers.IO) {
        var saved = music.getSongsByIds(ids).first()
        val missing = ids.toSet() - saved.map { it.id }.toSet()
        if (missing.isNotEmpty()) {
            val provided = supplied.filter { it.id in missing }.associateBy { it.id }
            val snapshot = preferences.getPlaybackQueueSnapshotOnce()?.items.orEmpty()
            val restored = snapshot.filter { it.mediaId in missing && it.mediaId !in provided }.mapNotNull { item ->
                val video = InnerTubeIds.videoIdFromContentUri(item.uri)
                if (video?.matches(Regex("[A-Za-z0-9_-]{11}")) != true || item.mediaId.toLongOrNull() == null) null
                else Song.emptySong().copy(id = item.mediaId, contentUriString = item.uri,
                    title = item.title.orEmpty(), artist = item.artist.orEmpty(), album = item.albumTitle.orEmpty(),
                    albumArtUriString = item.artworkUri, duration = item.durationMs ?: 0)
            }
            val candidates = (provided.values + restored).filter {
                it.id.toLongOrNull() != null && InnerTubeIds.videoIdFromContentUri(it.contentUriString)?.matches(Regex("[A-Za-z0-9_-]{11}")) == true
            }.distinctBy { it.id }
            // Existing rows (including local paths and likes) are never overwritten.
            if (candidates.isNotEmpty()) music.cacheCatalogSongs(candidates)
            saved = music.getSongsByIds(ids).first()
        }
        val byId = saved.associateBy { it.id }
        if (requireComplete && ids.any { it !in byId }) throw IOException("Playlist metadata incomplete")
        ids.mapNotNull(byId::get)
    }
}
