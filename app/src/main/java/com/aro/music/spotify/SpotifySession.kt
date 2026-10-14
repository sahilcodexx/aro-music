package com.aro.music.spotify

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

internal val Context.dataStore by preferencesDataStore(name = "spotify_session")

/** Meld's cookie session: developer client credentials are not used by its GraphQL API. */
@Singleton
class SpotifySession @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    private val cookieKey = stringPreferencesKey("sp_dc")
    private val spKeyKey = stringPreferencesKey("sp_key")
    private val tokenKey = stringPreferencesKey("access_token")
    private val expiryKey = longPreferencesKey("token_expiry")
    private val loggedOutKey = booleanPreferencesKey("explicitly_logged_out")
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private var pendingWebConnection: kotlinx.coroutines.Deferred<Result<Unit>>? = null
    private val connectedKey = booleanPreferencesKey("connected")
    val isLoggedInFlow = context.dataStore.data.map { it[connectedKey] == true }.distinctUntilChanged()

    private val hashSync = SpotifyHashSync(context)

    init {
        scope.launch {
            val prefs = context.dataStore.data.first()
            if (prefs[connectedKey] == true) ensureAuthenticated()
            else if (prefs[loggedOutKey] != true) {
                val values = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    listOf("https://open.spotify.com/", "https://accounts.spotify.com/")
                        .flatMap { android.webkit.CookieManager.getInstance().getCookie(it).orEmpty().split(';') }
                        .mapNotNull { entry ->
                            val parts = entry.trim().split('=', limit = 2)
                            if (parts.size == 2 && parts[1].isNotBlank()) parts[0] to parts[1] else null
                        }.toMap()
                }
                values["sp_dc"]?.let { connectWebSession(it, values["sp_key"].orEmpty()).await() }
            }
        }
        Spotify.onHashExpired = {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { hashSync.forceRefresh() }
        }
    }

    @Synchronized
    fun connectWebSession(spDc: String, spKey: String = ""): kotlinx.coroutines.Deferred<Result<Unit>> {
        val pending = pendingWebConnection
        if (pending?.isActive == true) return pending
        return scope.async { connect(spDc, spKey) }.also { pendingWebConnection = it }
    }

    suspend fun connect(spDc: String, spKey: String = ""): Result<Unit> = mutex.withLock {
        hashSync.loadCachedHashes()
        // Token verification must not wait on the unrelated hash registry.
        scope.launch { hashSync.sync() }
        val result = SpotifyAuth.fetchAccessToken(spDc, spKey)
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        result.map { token ->
            context.dataStore.edit {
                it[cookieKey] = spDc; it[spKeyKey] = spKey
                it[tokenKey] = token.accessToken; it[expiryKey] = token.accessTokenExpirationTimestampMs
                it[connectedKey] = true
                it[loggedOutKey] = false
            }
            context.getSharedPreferences(SpotifyPlaylistCache.PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply()
            Spotify.accessToken = token.accessToken
            SpotifyRecommendationEngine.invalidateProfile()
            SpotifyProfileCache.clearAllPersisted(context)
        }
    }

    suspend fun ensureAuthenticated(): Boolean = mutex.withLock {
        val prefs = context.dataStore.data.first()
        if (prefs[connectedKey] != true) return@withLock false
        val cookie = prefs[cookieKey] ?: run { clearSession(); return@withLock false }
        if (!prefs[tokenKey].isNullOrBlank() && (prefs[expiryKey] ?: 0L) > System.currentTimeMillis() + 60_000) {
            Spotify.accessToken = prefs[tokenKey]
            return@withLock true
        }
        val result = SpotifyAuth.fetchAccessToken(cookie, prefs[spKeyKey].orEmpty())
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        val token = result.getOrNull()
        if (token == null) {
            Spotify.accessToken = null
            if ((result.exceptionOrNull() as? Spotify.SpotifyException)?.statusCode == 401) clearSession()
            return@withLock false
        }
        context.dataStore.edit { it[tokenKey] = token.accessToken; it[expiryKey] = token.accessTokenExpirationTimestampMs }
        Spotify.accessToken = token.accessToken
        true
    }

    suspend fun logout() {
        pendingWebConnection?.cancel()
        mutex.withLock { clearSession() }
    }
    private suspend fun clearSession() {
        context.dataStore.edit { it.clear(); it[loggedOutKey] = true }
        context.getSharedPreferences(SpotifyPlaylistCache.PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply()
        Spotify.accessToken = null
        SpotifyMetadataRegistry.clear()
        SpotifyRecommendationEngine.invalidateProfile()
        SpotifyProfileCache.clearAllPersisted(context)
    }
}
