package com.aro.music.data.download

import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class DownloadCollectionKind { ALBUM, PLAYLIST }

@Serializable
data class DownloadCollection(
    val id: String,
    val title: String,
    val kind: DownloadCollectionKind,
    val songIds: List<String>,
    val artwork: String? = null,
) {
    fun localIds(records: Map<String, String>, ownedIds: Set<String>): List<String> =
        songIds.mapNotNull { original -> (records[original] ?: original).takeIf { it in ownedIds } }.distinct()
}

const val ALL_DOWNLOADED_SONGS = "all-songs"

fun downloadedSongIds(collectionId: String, collections: List<DownloadCollection>,
    records: Map<String, String>, ownedIds: Set<String>): List<String> =
    if (collectionId == ALL_DOWNLOADED_SONGS) ownedIds.toList()
    else collections.firstOrNull { it.id == collectionId }?.localIds(records, ownedIds).orEmpty()

@Singleton
class DownloadedCollections @Inject constructor(
    private val store: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
    private val images: dagger.Lazy<ImageLoader>,
) {
    private val key = stringPreferencesKey("aro_download_collections_v1")
    private val json = Json { ignoreUnknownKeys = true }
    val collections = store.data.map { json.decodeFromString<List<DownloadCollection>>(it[key] ?: "[]") }.distinctUntilChanged()

    suspend fun save(collection: DownloadCollection) {
        val normalized = collection.copy(songIds = collection.songIds.distinct())
        store.edit { prefs ->
            val previous = json.decodeFromString<List<DownloadCollection>>(prefs[key] ?: "[]")
            prefs[key] = json.encodeToString(previous.filterNot { it.id == collection.id } + normalized)
        }
        val artwork = collection.artwork ?: return
        if (artwork.startsWith("file:")) return
        val cached = withContext(Dispatchers.IO) {
            try {
                val result = images.get().execute(ImageRequest.Builder(context).data(artwork).size(512).allowHardware(false).build()) as? SuccessResult
                    ?: return@withContext null
                val directory = File(context.filesDir, "download-covers").apply { check(isDirectory || mkdirs()) }
                val name = java.security.MessageDigest.getInstance("SHA-256").digest(collection.id.toByteArray()).joinToString("") { "%02x".format(it) }
                val file = File(directory, "$name.jpg")
                val temp = File.createTempFile("cover-", ".jpg", directory)
                try {
                    temp.outputStream().use { check(result.drawable.toBitmap().compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it)) }
                    check(temp.renameTo(file))
                } finally { temp.delete() }
                file.toURI().toString()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        } ?: return
        store.edit { prefs ->
            val current = json.decodeFromString<List<DownloadCollection>>(prefs[key] ?: "[]")
            prefs[key] = json.encodeToString(current.map { if (it.id == collection.id && it.artwork == artwork) it.copy(artwork = cached) else it })
        }
    }
}
