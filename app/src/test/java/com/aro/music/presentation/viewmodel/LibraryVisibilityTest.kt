package com.aro.music.presentation.viewmodel

import com.aro.music.data.model.Playlist
import com.aro.music.data.model.StorageFilter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LibraryVisibilityTest {
    @Test fun `offline always shows local without changing the selected online filter`() {
        for (selected in StorageFilter.entries) for (hideLocal in listOf(false, true)) {
            assertEquals(StorageFilter.OFFLINE, effectiveLibraryStorageFilter(selected, hideLocal, false))
            assertEquals(if (hideLocal) StorageFilter.ONLINE else selected,
                effectiveLibraryStorageFilter(selected, hideLocal, true))
        }
    }

    @Test fun `offline playlists keep local collections and hide remote-only collections`() {
        val localIds = setOf("1", "2")
        val local = Playlist("mine", "My playlist", listOf("1", "cloud"))
        assertTrue(isOfflineLibraryPlaylist(local, localIds))
        assertTrue(isOfflineLibraryPlaylist(local.copy(songIds = emptyList()), localIds))
        assertFalse(isOfflineLibraryPlaylist(local.copy(songIds = listOf("cloud")), localIds))
        for (source in listOf("SPOTIFY", "TELEGRAM", "NAVIDROME", "JELLYFIN", "NETEASE", "QQMUSIC")) {
            assertFalse(isOfflineLibraryPlaylist(local.copy(source = source), localIds))
        }
    }
}
