package com.aro.music.data.innertube

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which YouTube Music browseId produced which synthetic app id.
 *
 * Navigation only carries the synthetic id (`Screen.AlbumDetail` takes an
 * `albumId: String`), but the network call needs the original `MPRE…`/`UC…`/
 * `VL…` browseId. Since the id is a one-way hash the browseId cannot be
 * recovered from it, so it is captured here at the moment the entity is parsed.
 *
 * Mappings persist independently of the Room schema so saved library items
 * can reopen their remote catalog after process death.
 */
@Singleton
class InnerTubeBrowseRegistry @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
) {
    private val preferences = context.getSharedPreferences("catalog_browse_ids", android.content.Context.MODE_PRIVATE)

    private val albumBrowseIds = ConcurrentHashMap<Long, String>()
    private val artistBrowseIds = ConcurrentHashMap<Long, String>()

    fun putAlbum(albumId: Long, browseId: String) {
        albumBrowseIds[albumId] = browseId
        preferences.edit().putString("album:$albumId", browseId).apply()
    }

    fun putArtist(artistId: Long, browseId: String) {
        artistBrowseIds[artistId] = browseId
        preferences.edit().putString("artist:$artistId", browseId).apply()
    }

    fun albumBrowseId(albumId: Long): String? = albumBrowseIds[albumId] ?: preferences.getString("album:$albumId", null)

    fun artistBrowseId(artistId: Long): String? = artistBrowseIds[artistId] ?: preferences.getString("artist:$artistId", null)

    /** True when this album id is known to have come from YouTube Music. */
    fun isKnownAlbum(albumId: Long): Boolean = albumBrowseId(albumId) != null

    fun isKnownArtist(artistId: Long): Boolean = artistBrowseId(artistId) != null
}