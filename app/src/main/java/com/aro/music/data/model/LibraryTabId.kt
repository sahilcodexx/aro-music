package com.aro.music.data.model

import androidx.compose.runtime.Immutable

@Immutable
enum class LibraryTabId(
    val storageKey: String,
    val title: String,
    val defaultSort: SortOption
) {
    SONGS("SONGS", "SONGS", SortOption.SongTitleAZ),
    ALBUMS("ALBUMS", "ALBUMS", SortOption.AlbumTitleAZ),
    ARTISTS("ARTIST", "ARTISTS", SortOption.ArtistNameAZ),
    PLAYLISTS("PLAYLISTS", "PLAYLISTS", SortOption.PlaylistNameAZ),
    FOLDERS("FOLDERS", "ARTISTS", SortOption.ArtistNameAZ),
    LIKED("LIKED", "LIKED", SortOption.LikedSongDateLiked),
    DOWNLOADS("DOWNLOADS", "DOWNLOADS", SortOption.SongTitleAZ);

    companion object {
        fun fromStorageKey(key: String): LibraryTabId =
            entries.firstOrNull { it.storageKey == (if (key == "FOLDERS" || key == "ARTISTS") "ARTIST" else key) } ?: SONGS
    }
}

fun String.toLibraryTabIdOrNull(): LibraryTabId? =
    LibraryTabId.entries.firstOrNull { it.storageKey == (if (this == "FOLDERS" || this == "ARTISTS") "ARTIST" else this) }
/** Fold the former streaming-only Folders/Artists alias into the saved Artists tab. */
fun normalizeLibraryTabOrder(order: List<String>): List<String> =
    (order.map { if (it == "FOLDERS" || it == "ARTISTS") "ARTIST" else it }
        .filter { key -> LibraryTabId.entries.any { it.storageKey == key } } +
        listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "LIKED", "DOWNLOADS")).distinct()
