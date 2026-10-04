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
import io.ktor.http.HttpHeaders
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

        /** Music clients, tried in order. ANDROID_MUSIC avoids most bot checks. */
        private val MUSIC_CLIENTS = listOf(
            YouTubeClient.ANDROID_MUSIC,
            YouTubeClient.IOS_MUSIC,
            YouTubeClient.WEB_REMIX,
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
            defaultRequest {
                headers.append(
                    HttpHeaders.UserAgent,
                    YouTubeClient.USER_AGENT_WEB,
                )
            }
            engine {
                config {
                    retryOnConnectionFailure(true)
                }
            }
        }
    }

    private val innerTube: InnerTube by lazy { InnerTube(httpClient = httpClient) }

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
                val root = body.takeIf { it.isNotBlank() }
                    ?.let { json.parseToJsonElement(it) as? JsonObject }
                if (root == null) {
                    Timber.d("$TAG: search '$query' empty body via $client, trying next")
                    continue
                }
                if (!accept(root)) {
                    Timber.d("$TAG: search '$query' no usable rows via $client, trying next")
                    continue
                }
                return root
            } catch (e: Exception) {
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
                client = YouTubeClient.ANDROID_MUSIC,
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
            Timber.w(e, "$TAG: next failed for $videoId")
            InnerTubeSearchPage(emptyList(), continuation)
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
        var lastError: Throwable? = null
        for (client in MUSIC_CLIENTS) {
            try {
                val response = innerTube.browse(
                    client = client,
                    browseId = browseId,
                    params = params,
                    continuation = continuation,
                )
                val body = response.bodyAsTextLimited(SEARCH_BODY_MAX_BYTES)
                if (body.isBlank()) continue
                return@withContext json.parseToJsonElement(body) as? JsonObject
            } catch (e: Exception) {
                lastError = e
                Timber.w(e, "$TAG: browse $browseId failed via $client")
            }
        }
        Timber.w(lastError, "$TAG: browse exhausted all clients for $browseId")
        null
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
            stream?.let { toInnerTubeStream(videoId, it) }
        } catch (e: Exception) {
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
