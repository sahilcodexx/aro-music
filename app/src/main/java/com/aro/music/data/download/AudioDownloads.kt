package com.aro.music.data.download

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.*
import com.aro.music.data.innertube.InnerTubeIds
import com.aro.music.data.model.Song
import com.aro.music.data.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal fun audioFileName(title: String, extension: String): String {
    require(extension in setOf("m4a", "webm", "opus"))
    return title.replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_").trim().trim('.').trim().take(100).ifBlank { "Audio" } + ".$extension"
}

internal fun downloadAudioMime(sourceMime: String, codecs: String?): String {
    require(sourceMime.startsWith("audio/")) { "Expected audio, not a streaming manifest" }
    return if (sourceMime.contains("webm") || sourceMime.contains("opus") || codecs?.contains("opus", ignoreCase = true) == true) "audio/ogg" else "audio/mp4"
}

@Singleton
class AudioDownloads @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DataStore<Preferences>,
    private val music: MusicRepository
) {
    private val key = stringPreferencesKey("aro_audio_downloads_v1")
    val records = store.data.map { Json.decodeFromString<Map<String, String>>(it[key] ?: "{}") }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val songs = records.flatMapLatest { records -> music.getSongsByIds(records.values.toList()) }
        .map { songs -> val owned = ownedIds().orEmpty(); songs.filter { it.id in owned } }.flowOn(Dispatchers.IO)

    private fun ownedIds(): Set<String>? = runCatching {
        context.contentResolver.query(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(android.provider.MediaStore.Audio.Media._ID),
            "${android.provider.MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ? AND ${android.provider.MediaStore.MediaColumns.IS_PENDING} = 0",
            arrayOf(context.packageName), null)?.use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getLong(0).toString()) }
            }
    }.getOrNull()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val previous = records.first()
        val owned = ownedIds() ?: return@withContext
        store.edit { prefs ->
            val current = Json.decodeFromString<Map<String, String>>(prefs[key] ?: "{}")
            prefs[key] = Json.encodeToString(current.filter { (original, local) -> local in owned || previous[original] != local })
        }
    }
    val work = WorkManager.getInstance(context).getWorkInfosByTagFlow(TAG)

    suspend fun enqueue(song: Song): Boolean = withContext(Dispatchers.IO) {
        check(InnerTubeIds.videoIdFromContentUri(song.contentUriString) != null) { "This source cannot be downloaded" }
        records.first()[song.id]?.let { localId ->
            val local = music.getSong(localId).first()
            if (local != null && local.id in ownedIds().orEmpty() && runCatching { context.contentResolver.openAssetFileDescriptor(Uri.parse(local.contentUriString), "r")?.use { true } == true }.getOrDefault(false)) return@withContext false
        }
        music.cacheCatalogSongs(listOf(song))
        val request = OneTimeWorkRequestBuilder<AudioDownloadWorker>()
            .setInputData(workDataOf("song_id" to song.id, "title" to song.title.take(200)))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS)
            .addTag(TAG).addTag("audio-song:${song.id}").addTag("audio-title:${song.title.take(100)}").build()
        WorkManager.getInstance(context).enqueueUniqueWork("aro-audio-${song.id}", ExistingWorkPolicy.KEEP, request).result.get()
        true
    }
    suspend fun record(originalId: String, localId: String) {
        store.edit { prefs ->
            val existing = Json.decodeFromString<Map<String, String>>(prefs[key] ?: "{}")
            prefs[key] = Json.encodeToString(existing + (originalId to localId))
        }
    }
    companion object { const val TAG = "aro-audio-download" }
}
