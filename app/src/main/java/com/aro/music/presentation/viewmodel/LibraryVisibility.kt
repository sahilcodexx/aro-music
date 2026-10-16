package com.aro.music.presentation.viewmodel

import com.aro.music.data.model.Playlist
import com.aro.music.data.model.Song
import com.aro.music.data.model.StorageFilter

internal fun effectiveLibraryStorageFilter(selected: StorageFilter, hideLocal: Boolean, online: Boolean): StorageFilter = when {
    !online -> StorageFilter.OFFLINE
    hideLocal -> StorageFilter.ONLINE
    else -> selected
}

internal fun Song.isLocalLibrarySong(): Boolean = contentUriString.startsWith("content://") ||
    contentUriString.startsWith("file://") || contentUriString.startsWith("/")

internal fun isOfflineLibraryPlaylist(playlist: Playlist, localSongIds: Set<String>): Boolean =
    playlist.source in setOf("LOCAL", "AI", "SMART", "QUEUE") &&
        (playlist.songIds.isEmpty() || playlist.songIds.any { it in localSongIds })
