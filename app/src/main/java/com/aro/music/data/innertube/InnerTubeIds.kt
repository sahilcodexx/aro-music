package com.aro.music.data.innertube

import java.security.MessageDigest

/**
 * Stable synthetic ids for InnerTube (YouTube Music) entities.
 *
 * InnerTube identifies everything with opaque string keys — a videoId like
 * `dQw4w9WgXcQ`, a browseId like `MPREb_...` for an album or `UC...` for an
 * artist. The app stores ids as `Long` everywhere (Room primary keys, favourite
 * rows, sort and pagination queries), so every remote key is folded into a
 * negative `Long` that can never collide with MediaStore's positive ids.
 *
 * Offsets 3T–20T are already used by the other cloud sources, so 21T/22T/23T
 * are used here. The `INNERTUBE_` salt keeps the digest distinct from the other
 * sources even when two of them happen to hash the same underlying string.
 *
 * The mapping is a pure function of the remote key, so the same track always
 * yields the same id across processes and reinstalls. That is what lets a
 * favourited search result still be found in Room later.
 */
internal object InnerTubeIds {

    private const val SONG_ID_OFFSET = 21_000_000_000_000L
    private const val ALBUM_ID_OFFSET = 22_000_000_000_000L
    private const val ARTIST_ID_OFFSET = 23_000_000_000_000L

    /** Keeps hashes inside one trillion so they cannot carry into the next offset band. */
    private const val HASH_MODULUS = 1_000_000_000L

    // Ids are emitted as -(OFFSET + hash) with hash in [0, HASH_MODULUS), so each
    // source owns a closed negative band. These are derived from the offsets
    // rather than hard-coded so renumbering an offset cannot desynchronise them.
    private val ALBUM_ID_BAND =
        -(ALBUM_ID_OFFSET + HASH_MODULUS - 1)..-ALBUM_ID_OFFSET
    private val ARTIST_ID_BAND =
        -(ARTIST_ID_OFFSET + HASH_MODULUS - 1)..-ARTIST_ID_OFFSET

    /**
     * Content-URI scheme for InnerTube tracks. The host segment is the videoId,
     * which is what the player needs in order to resolve a stream URL lazily —
     * YouTube stream URLs are signed and expire within hours, so they are never
     * stored on the [com.aro.music.data.model.Song] itself.
     */
    const val SCHEME = "innertube"

    fun songId(videoId: String): Long = -offsetId("song", videoId, SONG_ID_OFFSET)

    fun albumId(browseId: String): Long = -offsetId("album", browseId, ALBUM_ID_OFFSET)

    fun artistId(browseId: String): Long = -offsetId("artist", browseId, ARTIST_ID_OFFSET)

    /**
     * True when [id] falls in the InnerTube album band.
     *
     * The repository needs this to tell a remote album id from a local
     * MediaStore one *without* having to look anything up, because the mapping
     * is a one-way hash: an id is all that survives navigation, so the browseId
     * has to come from the registry. Band membership is what tells the
     * repository a network fetch is worth attempting.
     */
    fun isAlbumId(id: Long): Boolean = id in ALBUM_ID_BAND

    /** True when [id] falls in the InnerTube artist band. See [isAlbumId]. */
    fun isArtistId(id: Long): Boolean = id in ARTIST_ID_BAND

    /** `innertube://dQw4w9WgXcQ` — parsed back by [videoIdFromContentUri]. */
    fun contentUri(videoId: String): String = "$SCHEME://$videoId"

    fun isInnerTubeUri(contentUriString: String?): Boolean =
        contentUriString?.startsWith("$SCHEME://", ignoreCase = true) == true

    /** Returns the videoId, or null when [contentUriString] is not an InnerTube URI. */
    fun videoIdFromContentUri(contentUriString: String?): String? {
        if (contentUriString == null) return null
        val prefix = "$SCHEME://"
        if (!contentUriString.startsWith(prefix, ignoreCase = true)) return null
        return contentUriString.substring(prefix.length).takeIf { it.isNotBlank() }
    }

    private fun offsetId(kind: String, remoteKey: String, offset: Long): Long {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("INNERTUBE_${kind.uppercase()}_$remoteKey".toByteArray())
        // Mask off the sign bit so the value is always non-negative before the
        // offset is added; the caller re-applies the sign.
        var acc = 0L
        for (i in 0 until 8) {
            acc = (acc shl 8) or (digest[i].toLong() and 0xFF)
        }
        return (acc and Long.MAX_VALUE) % HASH_MODULUS + offset
    }
}