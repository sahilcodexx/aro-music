package com.aro.music.data.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Walks YouTube Music browse/search/next JSON and pulls out tracks.
 *
 * InnerTubeX models the top level of these responses but leaves the renderer
 * trees as raw [JsonObject], because YouTube changes renderers without notice.
 * This parser therefore walks defensively: every lookup is optional and an
 * unexpected shape yields fewer tracks rather than an exception.
 *
 * Helpers are deliberately defined locally as [asObj]/[asArr]/[str] instead of
 * using the kotlinx.serialization.json extension properties, so the lookups
 * below stay explicit and cannot silently resolve to a different overload.
 */
internal object InnerTubeSearchParser {

    private val ITEM_RENDERERS = setOf(
        "musicResponsiveListItemRenderer",
        "musicTwoRowItemRenderer",
        "musicMultiRowImageItemRenderer",
        "gridVideoRenderer",
        "compactVideoRenderer",
        "videoRenderer",
        "playlistPanelVideoRenderer",
        "reelItemRenderer",
    )

    private const val MAX_DEPTH = 40

    fun parse(root: JsonObject?): InnerTubeSearchPage {
        if (root == null) return InnerTubeSearchPage(emptyList(), null)
        val tracks = LinkedHashMap<String, InnerTubeTrack>()
        walk(root, tracks, 0)
        return InnerTubeSearchPage(tracks.values.toList(), findContinuation(root))
    }

    /**
     * Parses albums out of an album-filtered search.
     *
     * Callers must request the album tab filter, otherwise song rows (which
     * carry no album browseId) simply yield nothing. Dedupe is by browseId
     * because the same album appears once per shelf.
     */
    fun parseAlbums(root: JsonObject?): List<InnerTubeAlbum> {
        val items = collectRenderers(root, ITEM_RENDERERS).mapNotNull(::parseAlbum)
        return items.distinctBy { it.browseId }
    }

    /**
     * Parses artists out of an artist-filtered search.
     *
     * Artist rows point at a channel id (`UC...`) rather than a browseId, so
     * they are matched on the id prefix as well as on page type.
     */
    fun parseArtists(root: JsonObject?): List<InnerTubeArtist> {
        val items = collectRenderers(root, ITEM_RENDERERS).mapNotNull(::parseArtist)
        return items.distinctBy { it.browseId }
    }

    /**
 * Parses playlists out of a playlist-filtered search.
 *
 * Playlist rows carry a `VLPL…` browseId and a "N songs" subtitle, which is
 * the only place a song count is available without opening the playlist.
 */
fun parsePlaylists(root: JsonObject?): List<InnerTubePlaylist> {
    val items = collectRenderers(root, ITEM_RENDERERS).mapNotNull(::parsePlaylist)
    return items.distinctBy { it.browseId }
}

private fun parsePlaylist(renderer: JsonObject): InnerTubePlaylist? {
    val payload = renderer.unwrapItemPayload() ?: return null
    val browseId = findBrowseId(payload, "MUSIC_PAGE_TYPE_PLAYLIST")
        ?: payload.browseEndpoint()?.takeIf { it.startsWith("VL") }
        ?: return null
    val title = findTitle(payload) ?: return null

    // "Playlist • Artist • 100 songs"
    val subtitle = cleanedSubtitleRuns(payload)
    return InnerTubePlaylist(
        browseId = browseId,
        title = title,
        author = subtitle.firstOrNull(),
        artworkUrl = findArtwork(payload),
        songCount = subtitle.firstNotNullOfOrNull { SONG_COUNT_REGEX.find(it)?.groupValues?.get(1)?.toIntOrNull() },
    )
}

/**
 * Reads the page header from an album or playlist browse response.
 *
 * Those pages put their title and artwork in `header`, not in a list row, so the
 * generic item walk cannot see them. The returned [InnerTubeAlbum.browseId] is
 * always empty — the caller already knows the browseId it asked for. Returns null
 * when the header shape is unrecognised, so callers fall back to what they had.
 */
fun parsePageHeader(root: JsonObject?): InnerTubeAlbum? {
    val header = root?.get("header").asObj() ?: return null
    val renderer = header.firstObj(
        "musicResponsiveHeaderRenderer",
        "musicDetailHeaderRenderer",
    ) ?: return null

    val title = renderer.headerTitle() ?: return null
    val subtitle = cleanedSubtitleRuns(renderer)
    return InnerTubeAlbum(
        browseId = "",
        title = title,
        artist = subtitle.firstOrNull { !it.matches(YEAR_REGEX) },
        year = subtitle.firstNotNullOfOrNull { it.takeIf(YEAR_REGEX::matches)?.toIntOrNull() },
        artworkUrl = findArtwork(renderer),
        songCount = subtitle.firstNotNullOfOrNull { SONG_COUNT_REGEX.find(it)?.groupValues?.get(1)?.toIntOrNull() },
    )
}

/** Depth-limited DFS that yields each recognised item renderer exactly once. */
    private fun collectRenderers(root: JsonObject?, keys: Set<String>): List<JsonObject> {
        if (root == null) return emptyList()
        val out = mutableListOf<JsonObject>()
        collect(root, keys, 0, out)
        return out
    }

    private fun collect(
        node: JsonElement,
        keys: Set<String>,
        depth: Int,
        out: MutableList<JsonObject>,
    ) {
        if (depth > MAX_DEPTH) return
        when (node) {
            is JsonObject -> for (entry in node) {
                val child = entry.value.asObj() ?: continue
                if (entry.key in keys) out += child else collect(child, keys, depth + 1, out)
            }

            is JsonArray -> node.forEach { collect(it, keys, depth + 1, out) }
            else -> Unit
        }
    }

    private fun parseAlbum(renderer: JsonObject): InnerTubeAlbum? {
        val payload = renderer.unwrapItemPayload() ?: return null
        val browseId = findBrowseId(payload, "MUSIC_PAGE_TYPE_ALBUM")
            ?: payload.browseEndpoint()?.takeIf { it.startsWith("MPRE") }
            ?: return null
        val title = findTitle(payload) ?: return null

        // Subtitle is typically "Album • Artist • 2019".
        val subtitle = cleanedSubtitleRuns(payload)
        return InnerTubeAlbum(
            browseId = browseId,
            title = title,
            artist = subtitle.firstOrNull { !it.matches(YEAR_REGEX) },
            year = subtitle.firstNotNullOfOrNull { it.takeIf(YEAR_REGEX::matches)?.toIntOrNull() },
            artworkUrl = findArtwork(payload),
            songCount = null,
        )
    }

    private fun parseArtist(renderer: JsonObject): InnerTubeArtist? {
        val payload = renderer.unwrapItemPayload() ?: return null
        val browseId = findBrowseId(payload, "MUSIC_PAGE_TYPE_ARTIST")
            ?: payload.browseEndpoint()?.takeIf { it.startsWith("UC") }
            ?: return null
        val title = findTitle(payload) ?: return null
        return InnerTubeArtist(
            browseId = browseId,
            title = title,
            artworkUrl = findArtwork(payload),
        )
    }

    private fun JsonObject.unwrapItemPayload(): JsonObject? =
        firstObj(
            "musicTwoRowItemRenderer",
            "musicMultiRowImageItemRenderer",
            "musicResponsiveListItemRenderer",
        ) ?: this

    private val YEAR_REGEX = Regex("""\d{4}""")
    private val SONG_COUNT_REGEX = Regex("""(\d[\d,]*)\s+songs?""", RegexOption.IGNORE_CASE)

    /** Subtitle runs with separators and type labels removed, trimmed. */
    private fun cleanedSubtitleRuns(node: JsonObject): List<String> =
        subtitleRuns(node)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "•" }
            .dropWhile { it.equals("Song", true) || it.equals("Video", true) || it.equals("Album", true) }

    /**
     * Title of a page *header* renderer.
     *
     * Headers nest differently from list rows (`title.runs` rather than
     * `flexColumns`), and some also carry a `strapline` whose first run is a
     * type label such as "Album" — which is not the title, so it is skipped.
     */
    private fun JsonObject.headerTitle(): String? {
        val strapline = this["straplineTextOne"].asObj()?.runsText()?.firstOrNull()
        if (strapline != null && !isTypeLabel(strapline)) return strapline

        return this["title"].asObj()?.runsText()?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: this.str("title")
    }

    private fun isTypeLabel(text: String): Boolean =
        text.equals("Song", true) ||
            text.equals("Video", true) ||
            text.equals("Album", true) ||
            text.equals("Playlist", true) ||
            text.equals("Artist", true)

    /** Depth-limited DFS. An unbounded walk risks pathological traversals. */
    private fun walk(node: JsonElement, out: MutableMap<String, InnerTubeTrack>, depth: Int) {
        if (depth > MAX_DEPTH) return
        when (node) {
            is JsonObject -> {
                for (entry in node) {
                    val key = entry.key
                    val child = entry.value.asObj() ?: continue
                    if (key in ITEM_RENDERERS) {
                        parseItem(child)?.let { out.putIfAbsent(it.videoId, it) }
                    } else {
                        walk(child, out, depth + 1)
                    }
                }
            }

            is JsonArray -> node.forEach { walk(it, out, depth + 1) }
            else -> Unit
        }
    }

    private fun parseItem(renderer: JsonObject): InnerTubeTrack? {
        // Two-column and multi-row renderers nest the real payload one level down.
        val payload = renderer.firstObj(
            "musicTwoRowItemRenderer",
            "musicMultiRowImageItemRenderer",
            "musicResponsiveListItemRenderer",
        ) ?: renderer

        val videoId = findVideoId(renderer, payload) ?: return null
        val title = findTitle(payload) ?: return null

        return InnerTubeTrack(
            videoId = videoId,
            title = title,
            artist = findArtist(payload),
            album = findAlbum(payload),
            albumId = findBrowseId(payload, "MUSIC_PAGE_TYPE_ALBUM"),
            artistId = findBrowseId(payload, "MUSIC_PAGE_TYPE_ARTIST"),
            playlistId = findBrowseId(payload, "MUSIC_PAGE_TYPE_PLAYLIST"),
            durationSeconds = findDurationSeconds(payload),
            artworkUrl = findArtwork(payload),
            isExplicit = hasExplicitBadge(payload),
            params = findParams(payload),
        )
    }

    // ── field extraction ────────────────────────────────────────────────────

    private fun findVideoId(renderer: JsonObject, payload: JsonObject): String? {
        renderer["playlistPanelVideoRenderer"].asObj()?.str("videoId")?.let { return it }
        payload["playlistItemData"].asObj()?.str("videoId")?.let { return it }
        payload.str("videoId")?.let { return it }
        payload["watchEndpoint"].asObj()?.str("videoId")?.let { return it }
        payload["navigationEndpoint"].asObj()?.get("watchEndpoint").asObj()
            ?.str("videoId")?.let { return it }
        return null
    }

    private fun findTitle(node: JsonObject): String? {
        // Structured flex columns used by music list items.
        node["flexColumns"].asArr()?.forEach { column ->
            val flex = column.asObj()?.firstObj(
                "musicResponsiveListItemFlexColumnRenderer",
                "musicTwoRowItemFlexColumnRenderer",
            )
            flex?.runsText()?.firstOrNull()?.let { return it }
        }
        node["title"].asObj()?.runsText()?.firstOrNull()?.let { return it }
        node.str("title")?.let { return it }
        return null
    }

    private fun subtitleRuns(node: JsonObject): List<String> {
        val subtitle = node["subtitle"] ?: return emptyList()
        val obj = subtitle.asObj()
        return (obj?.runsText() ?: subtitle.asArr()?.mapNotNull { it.asObj()?.str("text") })
            .orEmpty()
    }

    private fun findArtist(node: JsonObject): String {
        cleanedSubtitleRuns(node).firstOrNull()?.let { return it }

        val byline = (node["longBylineText"].asObj()?.runsText()
            ?: node["longBylineText"].asArr()?.mapNotNull { it.asObj()?.str("text") }
            ).orEmpty()
        return byline.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    private fun findAlbum(node: JsonObject): String? = cleanedSubtitleRuns(node).getOrNull(1)

    private fun findDurationSeconds(node: JsonObject): Int? {
        val text = (node["lengthText"].asObj()?.runsText()?.joinToString("")
            ?: node["lengthText"].asArr()?.mapNotNull { it.asObj()?.str("text") }?.joinToString("")
            ?: node.str("lengthSeconds")
            ) ?: return null

        text.toIntOrNull()?.let { return it }
        val parts = text.split(':').mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    private fun findArtwork(node: JsonObject): String? {
        val thumbs = node["thumbnail"].asObj()
            ?.get("musicThumbnailRenderer").asObj()
            ?.get("thumbnail").asObj()
            ?.get("thumbnails").asArr()
            ?: return null

        // Thumbnails arrive smallest first; take the largest.
        return thumbs.mapNotNull { it.asObj()?.str("url") }.lastOrNull()
    }

    private fun hasExplicitBadge(node: JsonObject): Boolean =
        node["badges"].asArr()?.any { element ->
            element.asObj()
                ?.get("musicInlineBadgeRenderer").asObj()
                ?.str("icon") == "MUSIC_EXPLICIT_BADGE"
        } == true

    private fun findParams(node: JsonObject): String? =
        node["navigationEndpoint"].asObj()
            ?.get("watchEndpoint").asObj()
            ?.get("params").asArr()
            ?.firstOrNull()
            ?.let { it as? JsonPrimitive }
            ?.content

    /** Returns a browseId only when the page type matches the requested one. */
    private fun findBrowseId(node: JsonObject, matchPageType: String): String? {
        val browse = node["navigationEndpoint"].asObj()?.get("browseEndpoint").asObj()
            ?: return null
        val id = browse.str("browseId") ?: return null
        val page = browse["browseEndpointContext"].asObj()?.str("pageType")
        return if (page == null || page == matchPageType) id else null
    }

    /**
     * Raw browseId regardless of page type.
     *
     * Album and artist rows do not always populate `browseEndpointContext`, and
     * the two-row renderers used by search results sometimes nest the endpoint
     * one level below the item itself, so callers fall back to matching on the
     * id prefix (`MPRE…` for albums, `UC…` for artist channels).
     */
    private fun JsonObject.browseEndpoint(): String? =
        this["navigationEndpoint"].asObj()?.get("browseEndpoint").asObj()?.str("browseId")

    private fun findContinuation(root: JsonObject): String? {
        val containers = root["continuationContents"].asObj() ?: return null
        for (container in containers.values) {
            val renderers = container.asObj() ?: continue
            for (renderer in renderers.values) {
                val entries = renderer.asObj()?.get("continuations").asArr() ?: continue
                for (entry in entries) {
                    val obj = entry.asObj() ?: continue
                    val token = obj["nextContinuationData"].asObj()?.str("continuation")
                        ?: obj["reloadContinuationData"].asObj()?.str("continuation")
                    if (!token.isNullOrBlank()) return token
                }
            }
        }
        return null
    }

    // ── json helpers ───────────────────────────────────────────────────────

    /** Nullable-receiver version: safe to call on an absent member. */
    private fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

    /** Nullable-receiver version: safe to call on an absent member. */
    private fun JsonElement?.asArr(): JsonArray? = this as? JsonArray

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.firstObj(vararg keys: String): JsonObject? {
        for (key in keys) {
            this[key].asObj()?.let { return it }
        }
        return null
    }

    private fun JsonObject.runsText(): List<String> =
        this["runs"].asArr()?.mapNotNull { it.asObj()?.str("text") }.orEmpty()
}
