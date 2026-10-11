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
    private val connectedKey = booleanPreferencesKey("connected")
    val isLoggedInFlow = context.dataStore.data.map { it[connectedKey] == true }.distinctUntilChanged()

    private val hashSync = SpotifyHashSync(context)

    init {
        Spotify.onHashExpired = {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { hashSync.forceRefresh() }
        }
    }

    suspend fun connect(spDc: String, spKey: String = ""): Result<Unit> = mutex.withLock {
        hashSync.loadCachedHashes()
        hashSync.sync()
        val result = SpotifyAuth.fetchAccessToken(spDc, spKey)
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        result.map { token ->
            context.dataStore.edit {
                it[cookieKey] = spDc; it[spKeyKey] = spKey
                it[tokenKey] = token.accessToken; it[expiryKey] = token.accessTokenExpirationTimestampMs
                it[connectedKey] = true
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
        if (!prefs[tokenKey].isNullOrBlank() && (prefs[expiryKey] ?: 0L) > System.currentTimeMillis() + 60_000) {
            Spotify.accessToken = prefs[tokenKey]
            return@withLock true
        }
        val cookie = prefs[cookieKey] ?: return@withLock false
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

    suspend fun logout() = mutex.withLock { clearSession() }
    private suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
        context.getSharedPreferences(SpotifyPlaylistCache.PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply()
        Spotify.accessToken = null
        SpotifyMetadataRegistry.clear()
        SpotifyRecommendationEngine.invalidateProfile()
        SpotifyProfileCache.clearAllPersisted(context)
    }
}
