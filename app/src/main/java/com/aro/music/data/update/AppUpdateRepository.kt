package com.aro.music.data.update

import android.content.Context
import android.os.Build
import com.aro.music.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

sealed interface UpdateDialog {
    data class Available(val release: AppRelease) : UpdateDialog
    data object Current : UpdateDialog
    data object Failed : UpdateDialog
}
data class AppUpdateState(val checking: Boolean = false, val dialog: UpdateDialog? = null)

@Singleton
class AppUpdateRepository @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(AppUpdateState())
    val state = mutableState.asStateFlow()

    suspend fun check(manual: Boolean = false) {
        if (!mutex.tryLock()) return
        mutableState.value = AppUpdateState(checking = true)
        try {
            val current = AppVersion.parse(BuildConfig.VERSION_NAME) ?: error("Invalid installed version")
            val latest = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val age = now - preferences.getLong("checked_at", 0)
                val cached = preferences.getString("releases", null)
                val attemptAge = now - preferences.getLong("attempted_at", 0)
                if (!manual && cached != null && age in 0 until 6 * 60 * 60_000L) {
                    AppReleaseParser.latest(cached, current, Build.SUPPORTED_ABIS.toList())
                } else if (!manual && attemptAge in 0 until 15 * 60_000L) {
                    cached?.let { AppReleaseParser.latest(it, current, Build.SUPPORTED_ABIS.toList()) }
                } else {
                    preferences.edit().putLong("attempted_at", now).apply()
                    val response = fetchReleases()
                    val parsed = AppReleaseParser.latest(response, current, Build.SUPPORTED_ABIS.toList())
                    preferences.edit().putString("releases", response).putLong("checked_at", System.currentTimeMillis()).apply()
                    parsed
                }
            }
            val dialog = when {
                latest != null && latest.version > current && (manual || preferences.getString("dismissed_tag", null) != latest.tag) -> UpdateDialog.Available(latest)
                manual -> UpdateDialog.Current
                else -> null
            }
            mutableState.value = AppUpdateState(dialog = dialog)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = AppUpdateState(dialog = if (manual) UpdateDialog.Failed else null)
        } finally {
            mutableState.value = mutableState.value.copy(checking = false)
            mutex.unlock()
        }
    }

    fun dismiss() {
        (mutableState.value.dialog as? UpdateDialog.Available)?.let {
            preferences.edit().putString("dismissed_tag", it.release.tag).apply()
        }
        mutableState.value = mutableState.value.copy(dialog = null)
    }

    private fun fetchReleases(): String {
        val connection = URL(AroProject.RELEASES_API).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000; connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Aro/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            check(connection.responseCode == 200) { "Release check failed" }
            return connection.inputStream.bufferedReader().use { reader ->
                val data = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    check(data.length + count <= 2_000_000) { "Release response too large" }
                    data.append(buffer, 0, count)
                }
                data.toString()
            }
        } finally { connection.disconnect() }
    }
}
