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

    fun parseHomeSections(root: JsonObject?): List<InnerTubeHomeSection> =
        collectRenderers(root, setOf("musicCarouselShelfRenderer", "musicShelfRenderer")).mapNotNull { shelf ->
            val header = shelf["header"].asObj()?.get("musicCarouselShelfBasicHeaderRenderer").asObj()
                ?: shelf
            val title = header["title"].asObj()?.runsText()?.joinToString("")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val songs = mutableListOf<InnerTubeTrack>()
            val albums = mutableListOf<InnerTubeAlbum>()
            val artists = mutableListOf<InnerTubeArtist>()
            val playlists = mutableListOf<InnerTubePlaylist>()
            collectRenderers(shelf, ITEM_RENDERERS).forEach { item ->
                val payload = item.unwrapItemPayload() ?: return@forEach
                // Prefer the tile's primary browse target. A playlist's play
                // overlay can have a videoId without making the tile a song.
                val browseId = payload.browseEndpoint()
                when {
                    browseId?.startsWith("VL") == true -> parsePlaylist(item)?.let(playlists::add)
                    browseId?.startsWith("MPRE") == true -> parseAlbum(item)?.let(albums::add)
                    browseId?.startsWith("UC") == true -> parseArtist(item)?.let(artists::add)
                    else -> parseItem(item)?.let(songs::add)
                }
            }
            if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()) return@mapNotNull null
            InnerTubeHomeSection(title, header["strapline"].asObj()?.runsText()?.joinToString(""),
                songs.distinctBy { it.videoId }, albums.distinctBy { it.browseId },
                artists.distinctBy { it.browseId }, playlists.distinctBy { it.browseId },
                findSectionEndpoint(header)?.str("browseId"),
                findSectionEndpoint(header)?.str("params"))
        }

    private fun findSectionEndpoint(header: JsonObject): JsonObject? {
        val button = header["moreContentButton"].asObj()?.get("buttonRenderer").asObj()
        return button?.get("navigationEndpoint").asObj()?.get("browseEndpoint").asObj()
            ?: header["title"].asObj()?.get("runs").asArr()?.firstOrNull().asObj()
                ?.get("navigationEndpoint").asObj()?.get("browseEndpoint").asObj()
    }

    fun artistMetadata(root: JsonObject?): Triple<String?, String?, String?> {
        val header = collectRenderers(root, setOf("musicImmersiveHeaderRenderer", "musicVisualHeaderRenderer")).firstOrNull()
        val subscribe = header?.get("subscriptionButton2").asObj()?.get("subscribeButtonRenderer").asObj()
            ?: header?.get("subscriptionButton").asObj()?.get("subscribeButtonRenderer").asObj()
        fun text(node: JsonElement?): String? = node.asObj()?.runsText()?.joinToString("")?.takeIf { it.isNotBlank() }
            ?: node.asObj()?.str("simpleText")
        val subscribers = text(subscribe?.get("subscriberCountWithSubscribeText"))
            ?: text(subscribe?.get("longSubscriberCountText")) ?: text(subscribe?.get("shortSubscriberCountText"))
            ?: text(subscribe?.get("subscriberCountWithSubscribeText")) ?: text(subscribe?.get("subscriberCountText"))
        val monthly = text(header?.get("monthlyListenerCount"))
        val description = collectRenderers(root, setOf("musicDescriptionShelfRenderer")).firstOrNull()?.let { text(it["description"]) }
        return Triple(subscribers, monthly, description)
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
    val renderer = collectRenderers(root, setOf(
        "musicResponsiveHeaderRenderer", "musicDetailHeaderRenderer",
        "musicImmersiveHeaderRenderer", "musicVisualHeaderRenderer",
    )).firstOrNull() ?: return null

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

/**
 * True when the tree holds at least one recognised item renderer.
 *
 * Used to decide whether a client returned real content, as distinct from
 * parsing the tree into a specific item type. The charts browse legitimately
 * contains artist rows that are not tracks, so a track count would reject a
 * perfectly good response.
 */
fun containsItemRenderer(root: JsonObject?): Boolean =
    collectRenderers(root, ITEM_RENDERERS).isNotEmpty()

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
                // Same array-descent requirement as [walk]: dropping array
                // children hides everything under `tabs[]`.
                when (val child = entry.value) {
                    is JsonObject ->
                        if (entry.key in keys) out += child
                        else collect(child, keys, depth + 1, out)

                    is JsonArray -> collect(child, keys, depth + 1, out)
                    else -> Unit
                }
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

    private val DURATION_REGEX = Regex("""\d+:[0-5]\d(?::[0-5]\d)?""")
    private val YEAR_REGEX = Regex("""\d{4}""")
    private val SONG_COUNT_REGEX = Regex("""(\d[\d,]*)\s+songs?""", RegexOption.IGNORE_CASE)

    /** Subtitle runs with separators and type labels removed, trimmed. */
    private fun cleanedSubtitleRuns(node: JsonObject): List<String> =
        subtitleRuns(node)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "•" }
            .dropWhile(::isTypeLabel)

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
            text.equals("Artist", true) ||
            text.equals("EP", true) ||
            text.equals("Single", true)

    /** Depth-limited DFS. An unbounded walk risks pathological traversals. */
    private fun walk(node: JsonElement, out: MutableMap<String, InnerTubeTrack>, depth: Int) {
        if (depth > MAX_DEPTH) return
        when (node) {
            is JsonObject -> for (entry in node) {
                val key = entry.key
                // Dispatch on the child's real type. An earlier version did
                // `entry.value.asObj() ?: continue`, which silently dropped every
                // JsonArray child — and the whole renderer tree hangs off
                // `contents.tabbedSearchResultsRenderer.tabs[]`, so that skipped
                // the entire response and reported "no results" on a perfectly
                // good 200.
                when (val child = entry.value) {
                    is JsonObject ->
                        if (key in ITEM_RENDERERS) {
                            parseItem(child)?.let { out.putIfAbsent(it.videoId, it) }
                        } else {
                            walk(child, out, depth + 1)
                        }

                    is JsonArray -> walk(child, out, depth + 1)
                    else -> Unit
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

        // Charts, shelves and carousels hang the play target off the thumbnail
        // overlay instead of the row itself, so without this the whole charts
        // browse parsed to zero tracks.
        payload["overlay"].asObj()
            ?.get("musicItemThumbnailOverlayRenderer").asObj()
            ?.get("content").asObj()
            ?.get("musicPlayButtonRenderer").asObj()
            ?.get("playNavigationEndpoint").asObj()
            ?.get("watchEndpoint").asObj()
            ?.str("videoId")
            ?.let { return it }

        return null
    }

    private fun findTitle(node: JsonObject): String? {
        // Music list rows carry their text in `flexColumns[i].<flexRenderer>.text.runs`.
        // The runs are NOT directly on the flex renderer, so reading `.runs` there
        // silently yields nothing and every row gets discarded for a missing title.
        flexColumnText(node, 0)?.let { return it }

        // Fall back to any flex column that has text, for rows with no leading title.
        flexColumnTexts(node).firstOrNull { it.isNotBlank() }?.let { return it }

        node["title"].asObj()?.runsText()?.firstOrNull()?.let { return it }
        node.str("title")?.let { return it }
        return null
    }

    /**
     * Text runs of the nth flex column, or null when absent.
     *
     * Handles both shapes seen in the wild: the current
     * `...FlexColumnRenderer.text.runs` and the flatter `...FlexColumnRenderer.runs`.
     */
    private fun flexColumnRuns(node: JsonObject, index: Int): List<String>? {
        val flex = node["flexColumns"].asArr()?.getOrNull(index)?.asObj()?.firstObj(
            "musicResponsiveListItemFlexColumnRenderer",
            "musicTwoRowItemFlexColumnRenderer",
            "musicMultiRowItemFlexColumnRenderer",
        ) ?: return null
        val text = flex["text"].asObj()
        return text?.runsText()?.takeIf { it.isNotEmpty() }
            ?: flex.runsText().takeIf { it.isNotEmpty() }
    }

    /** Concatenated text of the nth flex column, e.g. "KARMA & Sez On The Beat". */
    private fun flexColumnText(node: JsonObject, index: Int): String? =
        flexColumnRuns(node, index)
            ?.joinToString("")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** All flex columns that carry text, in order. */
    private fun flexColumnTexts(node: JsonObject): List<String> {
        val out = mutableListOf<String>()
        val columns = node["flexColumns"].asArr() ?: return out
        for (i in columns.indices) {
            flexColumnText(node, i)?.let(out::add)
        }
        return out
    }

    private fun subtitleRuns(node: JsonObject): List<String> {
        val subtitle = node["subtitle"] ?: return emptyList()
        val obj = subtitle.asObj()
        return (obj?.runsText() ?: subtitle.asArr()?.mapNotNull { it.asObj()?.str("text") })
            .orEmpty()
    }

    private fun findArtist(node: JsonObject): String {
        // Music list rows have no `subtitle` member at all — the byline lives in
        // the second flex column, e.g. "KARMA & Sez On The Beat • Album • 2020".
        // Split on the separator so a leading "Song"/"Video" label and bullet
        // separators never leak into the artist name.
        flexColumnRuns(node, 1)
            ?.joinToString("")
            ?.split(BYLINE_SEPARATOR)
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotBlank() && !it.equals("Song", true) && !it.equals("Video", true) }
            ?.let { return it }

        cleanedSubtitleRuns(node).firstOrNull()?.let { return it }

        // Long byline, used by two-row renderers and by queue rows.
        val byline = (node["longBylineText"].asObj()?.runsText()
            ?: node["longBylineText"].asArr()?.mapNotNull { it.asObj()?.str("text") }
            ).orEmpty()
        byline.firstOrNull { it.isNotBlank() }?.let { return it.trim() }

        // Short byline, which carries "Artist • Album" or "Artist • Song".
        (node["shortBylineText"].asObj()?.runsText()
            ?: node["shortBylineText"].asArr()?.mapNotNull { it.asObj()?.str("text") }
            ).orEmpty()
            .firstOrNull { it.isNotBlank() && it != "•" }
            ?.let { return it.trim().substringBefore(" •").trim() }

        return ""
    }

    private val BYLINE_SEPARATOR = Regex("\\s*(?:•|\\|)\\s*|\\s+&\\s+|\\s+,\\s+")

    private fun findAlbum(node: JsonObject): String? {
        // Subtitle position is not an album field: it can contain view/play counts.
        // Use the display text of an actual album browse link, including byline runs.
        val albumId = findBrowseId(node, "MUSIC_PAGE_TYPE_ALBUM") ?: return null
        fun linkedTitle(element: JsonElement, depth: Int): String? {
            if (depth > MAX_DEPTH) return null
            when (element) {
                is JsonObject -> {
                    if (element.browseEndpoint() == albumId) {
                        element.str("text")?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                    for (child in element.values) linkedTitle(child, depth + 1)?.let { return it }
                }
                is JsonArray -> for (child in element) linkedTitle(child, depth + 1)?.let { return it }
                else -> Unit
            }
            return null
        }
        return linkedTitle(node, 0)
    }

    private fun findDurationSeconds(node: JsonObject): Int? {
        // List rows put the duration in the first *fixed* column, not a flex one.
        val fixed = node["fixedColumns"].asArr()?.firstOrNull()?.asObj()
            ?.firstObj("musicResponsiveListItemFixedColumnRenderer")
            ?.get("text").asObj()
            ?.runsText()
            ?.joinToString("")
            ?.takeIf { it.isNotBlank() }

        val text = fixed
            ?: node["lengthText"].asObj()?.runsText()?.joinToString("")?.takeIf { it.isNotBlank() }
            ?: node["lengthText"].asArr()?.mapNotNull { it.asObj()?.str("text") }?.joinToString("")?.takeIf { it.isNotBlank() }
            ?: node["lengthText"].asObj()?.str("simpleText")
            ?: node.str("lengthSeconds")
            ?: (cleanedSubtitleRuns(node) + flexColumnRuns(node, 1).orEmpty().joinToString("").split(BYLINE_SEPARATOR).map { it.trim() }).firstOrNull { it.matches(DURATION_REGEX) }
            ?: return null

        text.toIntOrNull()?.let { return it }
        val parts = text.trim().split(':').mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    private fun findArtwork(node: JsonObject): String? {
        // Radio uses thumbnail.thumbnails; list rows and carousels wrap it
        // in musicThumbnailRenderer under thumbnail or thumbnailRenderer.
        val thumbnail = node["thumbnailRenderer"].asObj() ?: node["thumbnail"].asObj() ?: return null
        val image = thumbnail["musicThumbnailRenderer"].asObj()?.get("thumbnail").asObj() ?: thumbnail
        val thumbs = image["thumbnails"].asArr() ?: return null
        val largest = thumbs.mapNotNull { it.asObj() }
            .filter { !it.str("url").isNullOrBlank() }
            .maxByOrNull { (it.str("width")?.toIntOrNull() ?: 0).toLong() * (it.str("height")?.toIntOrNull() ?: 0) }
        return InnerTubeArtwork.highQuality(largest?.str("url"))
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
        fun find(element: JsonElement, depth: Int): String? {
            if (depth > 15) return null
            when (element) {
                is JsonObject -> {
                    val browse = element["browseEndpoint"].asObj()
                    if (browse != null) {
                        val id = browse.str("browseId")
                        val page = browse["browseEndpointContextSupportedConfigs"].asObj()
                            ?.get("browseEndpointContextMusicConfig").asObj()?.str("pageType")
                            ?: browse["browseEndpointContext"].asObj()?.str("pageType")
                            ?: when {
                                id?.startsWith("VL") == true -> "MUSIC_PAGE_TYPE_PLAYLIST"
                                id?.startsWith("MPRE") == true -> "MUSIC_PAGE_TYPE_ALBUM"
                                id?.startsWith("UC") == true -> "MUSIC_PAGE_TYPE_ARTIST"
                                else -> null
                            }
                        if (page == matchPageType) return id
                    }
                    for (child in element.values) find(child, depth + 1)?.let { return it }
                }
                is JsonArray -> for (child in element) find(child, depth + 1)?.let { return it }
                else -> Unit
            }
            return null
        }
        return find(node, 0)
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
        fun find(node: JsonElement, depth: Int): String? {
            if (depth > MAX_DEPTH) return null
            when (node) {
                is JsonObject -> {
                    for (key in listOf("nextRadioContinuationData", "nextContinuationData", "reloadContinuationData", "continuationCommand")) {
                        node[key].asObj()?.str("continuation")?.let { return it }
                        node[key].asObj()?.str("token")?.let { return it }
                    }
                    for (child in node.values) find(child, depth + 1)?.let { return it }
                }
                is JsonArray -> for (child in node) find(child, depth + 1)?.let { return it }
                else -> Unit
            }
            return null
        }
        return find(root, 0)
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

    // ── suggestions ───────────────────────────────────────────────────────

    /**
     * YouTube Music's live query suggestions, from the
     * `music/get_search_suggestions` response.
     *
     * Ported from Meld's `GetSearchSuggestionsResponse` walk: section 0 holds
     * plain text completions (`searchSuggestionRenderer`), section 1 holds
     * recommended items (`musicResponsiveListItemRenderer`) that carry a
     * playable videoId. The generic [parse] ignores both because suggestion
     * rows carry no byline to map onto a [InnerTubeTrack].
     */
    fun parseSuggestions(root: JsonObject?): InnerTubeSuggestions {
        if (root == null) return InnerTubeSuggestions(emptyList(), emptyList())

        val sections = root["contents"].asArr()
            ?.mapNotNull { it.asObj()?.get("searchSuggestionsSectionRenderer").asObj() }
            .orEmpty()

        val queries = sections.getOrNull(0)
            ?.get("contents").asArr()
            .orEmpty()
            .mapNotNull { content ->
                val renderer = content.asObj()
                    ?.get("searchSuggestionRenderer").asObj()
                    ?: return@mapNotNull null
                renderer["suggestion"].asObj()?.runsText()
                    ?.joinToString("")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }

        val items = sections.getOrNull(1)
            ?.get("contents").asArr()
            .orEmpty()
            .mapNotNull { content ->
                val renderer = content.asObj()
                    ?.get("musicResponsiveListItemRenderer").asObj()
                    ?: return@mapNotNull null
                parseItem(renderer)?.takeIf { it.videoId.isNotBlank() }
            }

        return InnerTubeSuggestions(queries = queries, recommendedItems = items)
    }

    /**
     * The "Lyrics" tab endpoint from a `next` response, Meld's `NextPage`
     * `lyricsEndpoint` extraction.
     *
     * It is tab index 1 of the watch-next tabbed renderer and carries the
     * `MPLYt…` browseId the lyrics browse needs. Null when the track has no
     * lyrics page, which is the common case for user uploads.
     */
    fun findLyricsEndpoint(root: JsonObject?): Pair<String, String?>? {
        val tab = root
            ?.get("contents").asObj()
            ?.get("singleColumnMusicWatchNextResultsRenderer").asObj()
            ?.get("tabbedRenderer").asObj()
            ?.get("watchNextTabbedResultsRenderer").asObj()
            ?.get("tabs").asArr()
            ?.getOrNull(1)
            ?.asObj()
            ?.get("tabRenderer").asObj()
            ?: return null

        val browse = tab["endpoint"].asObj()
            ?.get("browseEndpoint").asObj()
            ?: return null
        val browseId = browse.str("browseId") ?: return null
        return browseId to browse.str("params")
    }

    // ── lyrics ────────────────────────────────────────────────────────────

    /**
     * Line-timed lyrics from the ANDROID_MUSIC lyrics browse, Meld's
     * `TimedLyricsResponse` walk.
     *
     * The component tree is deep but every level is optional; the lines sit in
     * `contents.elementRenderer.newElement.type.componentType.model
     * .timedLyricsModel.lyricsData.timedLyricsData`, each with its cue range.
     * Returned as LRC text so [com.aro.music.utils.LyricsUtils.parseLyrics]
     * can consume it unchanged.
     */
    fun parseTimedLyrics(root: JsonObject?): String? {
        val lines = root
            ?.get("contents").asObj()
            ?.get("elementRenderer").asObj()
            ?.get("newElement").asObj()
            ?.get("type").asObj()
            ?.get("componentType").asObj()
            ?.get("model").asObj()
            ?.get("timedLyricsModel").asObj()
            ?.get("lyricsData").asObj()
            ?.get("timedLyricsData").asArr()
            ?: return null

        val out = StringBuilder()
        for (line in lines) {
            val obj = line.asObj() ?: continue
            val text = obj.str("lyricLine") ?: continue
            val startMs = obj["cueRange"].asObj()?.str("startTimeMilliseconds")
                ?.toLongOrNull()
                ?: continue
            val minutes = startMs / 60_000
            val seconds = (startMs % 60_000) / 1_000
            val hundredths = (startMs % 1_000) / 10
            if (out.isNotEmpty()) out.append('\n')
            out.append("[%02d:%02d.%02d]%s".format(minutes, seconds, hundredths, text))
        }
        return out.toString().takeIf { it.isNotBlank() }
    }

    /**
     * Untimed plain lyrics from the WEB_REMIX lyrics browse, Meld's
     * `musicDescriptionShelfRenderer` walk.
     *
     * Fallback for tracks whose mobile lyrics page answers empty: the same
     * `MPLYt…` browseId asked as WEB_REMIX still carries the one-block text.
     */
    fun parsePlainLyrics(root: JsonObject?): String? {
        val sections = root
            ?.get("contents").asObj()
            ?.get("sectionListRenderer").asObj()
            ?.get("contents").asArr()
            ?: return null
        for (section in sections) {
            val shelf = section.asObj()
                ?.get("musicDescriptionShelfRenderer").asObj()
                ?: continue
            val description = shelf["description"].asObj() ?: continue
            // `runsText()` answers an empty list when runs are absent, so the
            // simpleText fallback has to be selected explicitly rather than
            // chained with `?:` — an empty list is not null.
            val runs = description.runsText().takeIf { it.isNotEmpty() }
                ?: listOfNotNull((description["simpleText"] as? JsonPrimitive)?.content)
            val text = runs.joinToString("").trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }

    /**
     * Video captions as timed LRC text, from the `get_transcript` response.
     *
     * Meld's `GetTranscriptResponse` walk: each cue group holds one cue with a
     * start offset and text. Musical-note decorations are trimmed exactly as
     * Meld trims them so instrumental captions do not render as a wall of ♪.
     */
    fun parseTranscript(root: JsonObject?): String? {
        val groups = root
            ?.get("actions").asArr()
            ?.firstNotNullOfOrNull { action ->
                action.asObj()
                    ?.get("updateEngagementPanelAction").asObj()
                    ?.get("content").asObj()
                    ?.get("transcriptRenderer").asObj()
                    ?.get("body").asObj()
                    ?.get("transcriptBodyRenderer").asObj()
                    ?.get("cueGroups").asArr()
            }
            ?: return null

        val out = StringBuilder()
        for (group in groups) {
            val cue = group.asObj()
                ?.get("transcriptCueGroupRenderer").asObj()
                ?.get("cues").asArr()
                ?.firstOrNull()
                ?.asObj()
                ?.get("transcriptCueRenderer").asObj()
                ?: continue
            val startMs = (cue["startOffsetMs"] as? JsonPrimitive)?.content?.toLongOrNull()
                ?: continue
            val text = cue["cue"].asObj()
                ?.str("simpleText")
                ?.trim('♪')
                ?.trim(' ')
                .orEmpty()
            val minutes = startMs / 60000
            val seconds = (startMs / 1000) % 60
            val millis = startMs % 1000
            if (out.isNotEmpty()) out.append('\n')
            out.append("[%02d:%02d.%03d]%s".format(minutes, seconds, millis, text))
        }
        return out.toString().takeIf { it.isNotBlank() }
    }
}

/** Live query suggestions: text completions plus recommended playable items. */
data class InnerTubeSuggestions(
    val queries: List<String>,
    val recommendedItems: List<InnerTubeTrack>,
)
