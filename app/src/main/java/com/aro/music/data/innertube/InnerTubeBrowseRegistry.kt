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
 * This is deliberately in-memory and not persisted. It is only consulted after
 * the user has just searched — the flow is "search, tap a result", so the entry
 * is always fresh — and a cold start with no entry simply falls back to the
 * Room copy, which is why search results are also written to the albums/artists
 * tables. Persisting this would need a schema column and a migration, which is
 * not worth it for a cache that only has to survive a tap.
 */
@Singleton
class InnerTubeBrowseRegistry @Inject constructor() {

    private val albumBrowseIds = ConcurrentHashMap<Long, String>()
    private val artistBrowseIds = ConcurrentHashMap<Long, String>()

    fun putAlbum(albumId: Long, browseId: String) {
        albumBrowseIds[albumId] = browseId
    }

    fun putArtist(artistId: Long, browseId: String) {
        artistBrowseIds[artistId] = browseId
    }

    fun albumBrowseId(albumId: Long): String? = albumBrowseIds[albumId]

    fun artistBrowseId(artistId: Long): String? = artistBrowseIds[artistId]

    /** True when this album id is known to have come from YouTube Music. */
    fun isKnownAlbum(albumId: Long): Boolean = albumBrowseIds.containsKey(albumId)

    fun isKnownArtist(artistId: Long): Boolean = artistBrowseIds.containsKey(artistId)
}