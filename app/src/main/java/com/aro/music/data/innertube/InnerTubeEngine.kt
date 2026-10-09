package com.aro.music.data.innertube

import android.content.Context
import com.metrolist.innertubex.InnerTube
import dagger.hilt.android.qualifiers.ApplicationContext
import com.metrolist.innertubex.bodyAsTextLimited
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the InnerTube HTTP client, session and stream extractor.
 *
 * The caller of InnerTube owns the supplied [HttpClient], so this class owns the
 * whole stack and exposes only the capabilities the app needs: [search],
 * [browse], [extractStream], and radio/queue continuations via [next].
 *
 * Requests are deliberately **not** serialised. InnerTubeX is built for
 * concurrent use: it holds its session in an `AtomicReference`, every request
 * method starts by taking an immutable `sessionSnapshot()`, its retry loop uses
 * only local state, and the one genuinely racy field — the lazily bootstrapped
 * `visitorData` — is already guarded by its own `visitorDataFetchMutex` with a
 * de-duplicating in-flight holder. An earlier version wrapped every call in a
 * local `Mutex`, which serialised a whole search (three tab queries plus several
 * playlist browses) into one long queue for no safety gain.
 */
@Singleton
class InnerTubeEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "InnerTubeEngine"

        /** Browse/search bodies are a few hundred KB; this is a safety bound. */
        private const val SEARCH_BODY_MAX_BYTES = 8 * 1024 * 1024

        /**
         * Bodies above this are rejected before they are parsed.
         *
         * Parsing JSON into a kotlinx object tree costs roughly an order of
         * magnitude more heap than the text, so a multi-megabyte body becomes
         * tens of megabytes of live objects. Some clients answer 200 with a
         * multi-megabyte catalogue shell that carries no playable rows anyway, and
         * parsing those is what exhausted the 256MB heap and crashed the app.
         */
        private const val MAX_USEFUL_BODY_BYTES = 1_500_000

        /**
         * Cheap substring tests for "this response has playable rows".
         *
         * Runs on the raw text before any parsing so an unusable response costs a
         * scan rather than a large object tree. Two markers because the catalogue
         * speaks both shapes: list rows (`musicResponsiveListItemRenderer`) and
         * the two-column tiles that make up every home-feed shelf
         * (`musicTwoRowItemRenderer`) — a home browse whose A/B variant ships no
         * quick-picks list would otherwise fail the guard on all clients and the
         * feed would stay empty.
         */
        private val MUSIC_ROW_MARKERS = listOf(
            "musicResponsiveListItemRenderer",
            "musicTwoRowItemRenderer",
        )

        /**
         * Music clients, tried in order.
         *
         * WEB_REMIX leads because it is what Meld uses for every catalogue
         * endpoint (`next`, home browse, search) and is the only client this
         * InnerTubeX build ships working defaults for. ANDROID_MUSIC used to
         * lead, but the library's own `PlaybackClientCatalog` now marks it
         * `ClientLifecycle.BROKEN` / `PROBE_ONLY` — every `next` and `browse`
         * came back HTTP 400, which silently killed the radio queue and the
         * home feed. It is kept last only as a data point for endpoints that
         * might still accept it; playback itself never goes through these.
         */
        private val MUSIC_CLIENTS = listOf(
            YouTubeClient.WEB_REMIX,
            YouTubeClient.IOS_MUSIC,
            YouTubeClient.ANDROID_MUSIC,
        )

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
            coerceInputValues = true
        }
    }

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }
            // NOTE: deliberately no `defaultRequest { User-Agent }` here.
            // InnerTubeX's `ytClient` already sets a per-client User-Agent via
            // `userAgent(...)`, and `headers.append` would *add* to it rather than
            // replace it. The result was a comma-joined header such as
            //   User-Agent: com.google.android.apps.youtube.music/5.34.51 (...),Mozilla/5.0 (...)
            // which YouTube answers with HTTP 400 on every endpoint. That made
            // all three clients fail and silently pushed every request onto the
            // JioSaavn/iTunes fallback.
            engine {
                config {
                    retryOnConnectionFailure(true)
                }
            }
        }
    }

    private val innerTube: InnerTube by lazy { InnerTube(httpClient = httpClient) }
    private val browseSessionMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun ensureBrowseSession() {
        if (!innerTube.visitorData.isNullOrBlank()) return
        browseSessionMutex.lock()
        try {
            if (innerTube.visitorData.isNullOrBlank()) {
                // Home continuation tokens belong to the visitor that fetched
                // the first page; initialize it before concurrent home/explore.
                kotlinx.coroutines.withTimeoutOrNull(5_000L) { innerTube.fetchFreshVisitorData() }
            }
        } finally { browseSessionMutex.unlock() }
    }


    private val streamCache = android.util.LruCache<String, ExtractedStream>(32)

    @OptIn(com.metrolist.innertubex.sabr.ExperimentalSabrApi::class)
    suspend fun sabrAudio(videoId: String): kotlinx.coroutines.flow.Flow<ByteArray> {
        val cached = streamCache[videoId]?.takeIf {
            it.expiresAt?.toEpochMilliseconds()?.let { expiry -> expiry > System.currentTimeMillis() + 60_000L } != false
        }
        val stream = cached ?: extractor.extract(videoId, hints = ContentHints(wantVideo = false))
            ?: throw java.io.IOException("No audio stream for $videoId")
        streamCache.put(videoId, stream)
        val bootstrap = stream.sabrBootstrap ?: throw java.io.IOException("Missing SABR bootstrap for $videoId")
        return com.metrolist.innertubex.sabr.SabrAudioStream(httpClient, bootstrap).bytes()
    }

    fun headersFor(audioUrl: String): Map<String, String> =
        streamCache.snapshot().values.firstOrNull { it.audioUrl == audioUrl }?.headers.orEmpty()

    private val extractor: InnerTubeExtractor by lazy {
        val configStore = RemotePlayerConfigStore(
            httpClient = httpClient,
            repository = com.metrolist.innertubex.cipher.PlayerConfigRepository.disabled(),
        )
        val cipher = YouTubeCipherService(httpClient, configStore)
        InnerTubeExtractor(
            configParser = YtConfigParserImpl(
                httpClient = httpClient,
                innerTube = innerTube,
                remotePlayerConfigStore = configStore,
                cipherService = cipher,
            ),
            cipherService = cipher,
            innerTube = innerTube,
        )
    }

    /**
     * Searches YouTube Music.
     *
     * @param query free text search
     * @param params YouTube Music browse params filter, e.g. for songs-only
     * @param continuation paging token from a previous [search] response
     */
    suspend fun search(
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
    ): InnerTubeSearchPage = withContext(Dispatchers.IO) {
        val root = searchJson(
            query = query,
            params = params,
            continuation = continuation,
            // Only settle on a client that actually returned tracks. Without
            // this, a client that answers 200 with an empty shelf would end
            // the fallback chain and the query would look like "no results".
            accept = { InnerTubeSearchParser.parse(it).tracks.isNotEmpty() },
        )
        InnerTubeSearchParser.parse(root)
    }

    /**
     * Raw search response, for the tabs whose rows are not tracks.
     *
     * [search] collapses every response into tracks, which is the wrong shape
     * for the album and artist tabs — those rows carry a browseId and no
     * videoId — so they parse the renderer tree themselves. Clients still fall
     * through [MUSIC_CLIENTS], so callers should treat null as "nothing found"
     * rather than "request failed".
     */
    suspend fun searchRaw(
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
    ): JsonObject? = withContext(Dispatchers.IO) {
        searchJson(query = query, params = params, continuation = continuation)
    }

    /**
     * Tries each music client in order and returns the first response that
     * [accept] approves. Safe to call concurrently — see the class comment.
     */
    private suspend fun searchJson(
        query: String?,
        params: String?,
        continuation: String?,
        accept: (JsonObject) -> Boolean = { true },
    ): JsonObject? {
        var lastError: Throwable? = null
        for (client in MUSIC_CLIENTS) {
            try {
                val response = innerTube.search(
                    client = client,
                    query = query,
                    params = params,
                    continuation = continuation,
                )
                val body = response.bodyAsTextLimited(SEARCH_BODY_MAX_BYTES)
                // Guard *before* parsing: a huge or row-less body is unusable, and
                // parsing it is what ran the heap out.
                if (body.isBlank()) {
                    Timber.d("$TAG: search '$query' empty body via $client, trying next")
                    continue
                }
                if (body.length > MAX_USEFUL_BODY_BYTES) {
                    Timber.d(
                        "$TAG: search '$query' body ${body.length}B over cap via $client, " +
                            "trying next"
                    )
                    continue
                }
                if (!MUSIC_ROW_MARKERS.any { body.contains(it) }) {
                    Timber.d("$TAG: search '$query' no music rows via $client, trying next")
                    continue
                }
                val root = json.parseToJsonElement(body) as? JsonObject
                if (root == null) {
                    Timber.d("$TAG: search '$query' unparseable via $client, trying next")
                    continue
                }
                if (!accept(root)) {
                    Timber.d("$TAG: search '$query' no usable rows via $client, trying next")
                    continue
                }
                return root
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
                Timber.w(e, "$TAG: search failed via $client")
            }
        }
        Timber.w(lastError, "$TAG: search exhausted all clients for '$query'")
        return null
    }

    /**
     * Fetches the next page of a radio/queue feed (the "autoplay" track list).
     *
     * @param videoId seed track
     * @param continuation paging token from a previous [next] response
     */
    suspend fun next(
        videoId: String,
        playlistId: String? = null,
        continuation: String? = null,
    ): InnerTubeSearchPage = withContext(Dispatchers.IO) {
        try {
            val response = innerTube.next(
                // Meld asks `next` as WEB_REMIX; ANDROID_MUSIC answers HTTP 400
                // on this endpoint in the current InnerTubeX build.
                client = YouTubeClient.WEB_REMIX,
                videoId = videoId,
                playlistId = playlistId,
                continuation = continuation,
            )
            val parsed = InnerTubeSearchParser.parse(
                json.parseToJsonElement(
                    response.bodyAsTextLimited(SEARCH_BODY_MAX_BYTES),
                ) as? JsonObject,
            )
            Timber.d("$TAG: next($videoId) → ${parsed.tracks.size} tracks")
            parsed
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "$TAG: next failed for $videoId")
            throw e
        }
    }

    /**
     * Raw `next` response for a track.
     *
     * [next] collapses the response into tracks, which is right for a queue and
     * wrong for everything else the page carries: the "Lyrics" and "Related"
     * tabs arrive as tab endpoints inside the same response, and Meld reads them
     * from exactly this JSON. Returns the untouched object tree so callers can
     * pull those endpoints out, or null when the request failed.
     */
    suspend fun nextRaw(
        videoId: String,
        playlistId: String? = null,
        continuation: String? = null,
    ): JsonObject? = withContext(Dispatchers.IO) {
        try {
            val response = innerTube.next(
                // Same client rule as [next]: Meld reads the watch-next page
                // (queue rows, lyrics tab endpoint) from WEB_REMIX.
                client = YouTubeClient.WEB_REMIX,
                videoId = videoId,
                playlistId = playlistId,
                continuation = continuation,
            )
            val body = response.bodyAsTextLimited(SEARCH_BODY_MAX_BYTES)
            if (body.isBlank()) return@withContext null
            json.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "$TAG: nextRaw failed for $videoId")
            null
        }
    }

    /**
     * YouTube Music's live query suggestions — the list that drops down under
     * the search field while typing.
     *
     * Meld asks `music/get_search_suggestions` as WEB_REMIX; the response holds
     * two sections, text completions and recommended items, which
     * [InnerTubeSearchParser.parseSuggestions] walks. Returns null on failure —
     * suggestions are decorative and must never surface as an error.
     */
    suspend fun searchSuggestions(query: String): JsonObject? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null
        try {
            val response = innerTube.getSearchSuggestions(
                client = YouTubeClient.WEB_REMIX,
                input = query,
            )
            val body = response.bodyAsTextLimited(SEARCH_BODY_MAX_BYTES)
            if (body.isBlank()) return@withContext null
            json.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "$TAG: suggestions failed for '$query'")
            null
        }
    }

    /**
     * Fetches a browse response as raw JSON.
     *
     * Covers the charts feed behind [getTrendingSongs] and the album, artist and
     * playlist pages, whose responses embed the tracklist that
     * [InnerTubeSearchParser.parse] already knows how to read. Like [search], it
     * falls through [MUSIC_CLIENTS] and returns null when every client comes
     * back empty.
     */
    suspend fun browse(
        browseId: String,
        params: String? = null,
        continuation: String? = null,
    ): JsonObject? = withContext(Dispatchers.IO) {
        ensureBrowseSession()
        var lastError: Throwable? = null
        for (client in MUSIC_CLIENTS) {
            try {
                val response = innerTube.browse(
                    client = client,
                    browseId = browseId.takeIf { continuation.isNullOrBlank() },
                    params = params,
                    continuation = continuation,
                )
                val body = response.bodyAsTextLimited(SEARCH_BODY_MAX_BYTES)
                // Same pre-parse guards as search: reject oversized and row-less
                // bodies before building an object tree from them.
                if (body.isBlank() || body.length > MAX_USEFUL_BODY_BYTES) continue
                val hasRows = MUSIC_ROW_MARKERS.any { body.contains(it) }
                if (!hasRows && !continuation.isNullOrBlank()) {
                    // A continuation page has no music rows by nature.
                    return@withContext json.parseToJsonElement(body) as? JsonObject
                }
                if (!hasRows) continue
                val root = json.parseToJsonElement(body) as? JsonObject
                if (root == null) continue
                // Accept a client only when it returned actual item renderers.
                // This must not be "parses to tracks": the charts browse is a
                // mixed page whose leading rows are artists, which carry no
                // videoId, so a track-count check rejected a perfectly good
                // response and the home feed stayed empty.
                if (!continuation.isNullOrBlank()) {
                    return@withContext root
                }
                if (!InnerTubeSearchParser.containsItemRenderer(root)) {
                    Timber.d("$TAG: browse $browseId no rows via $client, trying next")
                    continue
                }
                return@withContext root
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
                Timber.w(e, "$TAG: browse $browseId failed via $client")
            }
        }
        Timber.w(lastError, "$TAG: browse exhausted all clients for $browseId")
        null
    }

    /**
     * A browse response with none of the guards [browse] applies.
     *
     * The lyrics page is the reason it exists: its `MPLYt…` browseId answers
     * with a component tree that contains no `musicResponsiveListItemRenderer`
     * at all, so the row-marker check in [browse] would reject the only
     * response that matters. [clients] pins the client per caller — timed
     * lyrics need ANDROID_MUSIC, the plain description shelf needs WEB_REMIX.
     */
    suspend fun browseRaw(
        browseId: String,
        params: String? = null,
        clients: List<YouTubeClient> = MUSIC_CLIENTS,
    ): JsonObject? = withContext(Dispatchers.IO) {
        for (client in clients) {
            try {
                val response = innerTube.browse(
                    client = client,
                    browseId = browseId,
                    params = params,
                )
                // `expectSuccess` is off, so an HTTP 400 arrives as a normal
                // response whose body is an error JSON object. Without this
                // check the error body would "succeed" the loop and the next
                // client would never be tried — exactly how ANDROID_MUSIC's
                // 400s used to swallow the whole fallback chain.
                if (!response.status.isSuccess()) {
                    Timber.d(
                        "$TAG: browseRaw $browseId HTTP ${response.status.value} via $client, " +
                            "trying next"
                    )
                    continue
                }
                val body = response.bodyAsTextLimited(SEARCH_BODY_MAX_BYTES)
                if (body.isBlank()) continue
                val root = json.parseToJsonElement(body) as? JsonObject
                if (root != null) return@withContext root
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w(e, "$TAG: browseRaw $browseId failed via $client")
            }
        }
        null
    }

    /**
     * Line-timed lyrics in LRC form, exactly as Meld's `YouTube.timedLyrics`
     * produces them.
     *
     * Meld asks the `MPLYt…` browse as ANDROID_MUSIC — the only client YouTube
     * serves per-line cue ranges to; the same browseId as WEB_REMIX comes back
     * as one untimed text block. On this InnerTubeX build ANDROID_MUSIC answers
     * 400 (see [MUSIC_CLIENTS]), so IOS_MUSIC gets a turn too: it is a mobile
     * client and may still carry cue ranges. An untimed answer parses to null,
     * which is the same signal as "no lyrics page", so the plain-shelf fallback
     * in the repository chain is never skipped.
     */
    suspend fun timedLyrics(browseId: String, params: String?): String? =
        withContext(Dispatchers.IO) {
            val root = browseRaw(
                browseId = browseId,
                params = params,
                clients = listOf(YouTubeClient.ANDROID_MUSIC, YouTubeClient.IOS_MUSIC),
            ) ?: return@withContext null
            InnerTubeSearchParser.parseTimedLyrics(root)
        }

    /**
     * Video captions as timed LRC text, Meld's `YouTube.transcript` port.
     *
     * Captions exist for a minority of music tracks, which is why they sit
     * behind the timed lyrics rather than in front of them. The cue groups
     * arrive with a start offset per line; the parser turns those into
     * `[mm:ss.mmm]` LRC timestamps, so the lyrics view can scroll them like any
     * other synced lyrics.
     */
    suspend fun transcript(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = innerTube.getTranscript(
                client = YouTubeClient.WEB,
                videoId = videoId,
            )
            val body = response.bodyAsTextLimited(SEARCH_BODY_MAX_BYTES)
            if (body.isBlank()) return@withContext null
            val root = json.parseToJsonElement(body) as? JsonObject ?: return@withContext null
            InnerTubeSearchParser.parseTranscript(root)
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "$TAG: transcript failed for $videoId")
            null
        }
    }

    /**
     * Resolves a playable audio stream for [videoId].
     *
     * Prefers high quality audio and lets the extractor pick the transport
     * (direct / HLS / SABR). Returns null when YouTube refuses playback, which
     * happens for region locked or age restricted tracks.
     */
    suspend fun extractStream(
        videoId: String,
        audioQuality: AudioQuality = AudioQuality.HIGH,
    ): InnerTubeStream? = withContext(Dispatchers.IO) {
        try {
            val stream = extractor.extract(
                videoId = videoId,
                hints = ContentHints(wantVideo = false),
                audioQuality = audioQuality,
            )
            stream?.let { streamCache.put(videoId, it); toInnerTubeStream(videoId, it) }
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "$TAG: extractStream failed for $videoId")
            null
        }
    }

    /**
     * Warms the player-config cache so the first real playback request does not
     * pay for the cipher fetch. Safe to call opportunistically at app start.
     */
    suspend fun prewarm(): Boolean = withContext(Dispatchers.IO) {
        try {
            extractor.prewarm()
            true
        } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w(e, "$TAG: prewarm failed")
            false
        }
    }

    fun shutdown() {
        runCatching { innerTube.close() }
        runCatching { httpClient.close() }
    }

    private fun toInnerTubeStream(videoId: String, s: ExtractedStream) = InnerTubeStream(
        videoId = videoId,
        audioUrl = s.audioUrl,
        headers = s.headers,
        expiresAt = s.expiresAt,
        contentLengthBytes = s.contentLengthBytes,
        mimeType = s.mimeType,
        codecs = s.codecs,
        bitrate = s.bitrate,
        itag = s.itag,
        clientName = s.clientName,
        useRangeChunks = s.useRangeChunks,
        rangeChunkSizeBytes = s.rangeChunkSizeBytes,
    )
}

/** A resolved, playable audio stream. */
data class InnerTubeStream(
    val videoId: String,
    val audioUrl: String,
    val headers: Map<String, String>,
    val expiresAt: kotlin.time.Instant?,
    val contentLengthBytes: Long?,
    val mimeType: String?,
    val codecs: String?,
    val bitrate: Int?,
    val itag: Int,
    val clientName: String,
    val useRangeChunks: Boolean,
    val rangeChunkSizeBytes: Long,
)

/** One page of tracks plus the token needed to fetch the page after it. */
data class InnerTubeSearchPage(
    val tracks: List<InnerTubeTrack>,
    val continuation: String?,
)

/**
 * A single track from a search or radio response, before mapping to the app's
 * [com.aro.music.data.model.Song].
 */
data class InnerTubeTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val playlistId: String? = null,
    val durationSeconds: Int? = null,
    val artworkUrl: String? = null,
    val isExplicit: Boolean = false,
    val params: String? = null,
)

/** An album row from an album-filtered search, keyed by its browseId. */
data class InnerTubeAlbum(
    val browseId: String,
    val title: String,
    val artist: String? = null,
    val year: Int? = null,
    val artworkUrl: String? = null,
    val songCount: Int? = null,
)

/**
 * An artist row from an artist-filtered search.
 *
 * [browseId] is the artist channel id (`UC…`), which is what YouTube Music
 * browse endpoints expect for `MUSIC_PAGE_TYPE_ARTIST`.
 */
data class InnerTubeArtist(
    val browseId: String,
    val title: String,
    val artworkUrl: String? = null,
)

/**
 * A playlist row from a playlist-filtered search.
 *
 * [browseId] is the browsable playlist id (`VLPL…`). It is kept verbatim as the
 * `Playlist.id` rather than hashed, because playlists are not Room rows — their
 * ids only ever have to round-trip back to the network.
 */
data class InnerTubePlaylist(
    val browseId: String,
    val title: String,
    val author: String? = null,
    val artworkUrl: String? = null,
    val songCount: Int? = null,
)
