package com.aro.music.data.worker

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media as AudioMedia
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aro.music.data.database.AlbumEntity
import com.aro.music.data.database.ArtistEntity
import com.aro.music.data.database.MusicDao
import com.aro.music.data.database.SongEntity
import com.aro.music.data.database.SourceType
import com.aro.music.data.preferences.UserPreferencesRepository
import com.aro.music.utils.DirectoryRuleResolver
import com.aro.music.utils.LocalArtworkUri
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Invoked exclusively by the confirmed Library import action, never by startup/cloud sync. */
@HiltWorker
class LocalMusicImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val musicDao: MusicDao,
    private val preferences: UserPreferencesRepository
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!inputData.getBoolean(CONFIRMED, false) || !hasAudioPermission(applicationContext)) {
            return@withContext Result.failure()
        }
        try {
            val minimumDuration = preferences.minSongDurationFlow.first().toLong()
            val rules = DirectoryRuleResolver(preferences.allowedDirectoriesFlow.first(), preferences.blockedDirectoriesFlow.first())
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(AudioMedia._ID, AudioMedia.TITLE, AudioMedia.ARTIST, AudioMedia.ARTIST_ID,
                AudioMedia.ALBUM, AudioMedia.ALBUM_ID, AudioMedia.DURATION, AudioMedia.DATA, AudioMedia.DATE_ADDED,
                AudioMedia.MIME_TYPE, AudioMedia.YEAR, AudioMedia.TRACK)
            val songs = mutableListOf<SongEntity>()
            applicationContext.contentResolver.query(uri, projection,
                "${AudioMedia.IS_MUSIC} != 0 AND ${AudioMedia.DURATION} >= ?", arrayOf(minimumDuration.toString()), "${AudioMedia._ID} ASC")?.use { cursor ->
                fun text(column: String): String = cursor.getString(cursor.getColumnIndexOrThrow(column)).orEmpty()
                fun number(column: String): Long = cursor.getLong(cursor.getColumnIndexOrThrow(column))
                val total = cursor.count
                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    val path = text(AudioMedia.DATA)
                    val parent = File(path).parent.orEmpty()
                    if (rules.isBlocked(parent)) continue
                    val id = number(AudioMedia._ID)
                    if (id <= 0L) continue
                    val duration = number(AudioMedia.DURATION)
                    if (duration < minimumDuration) continue
                    songs += SongEntity(
                        id = id, title = text(AudioMedia.TITLE).ifBlank { File(path).nameWithoutExtension },
                        artistName = text(AudioMedia.ARTIST), artistId = number(AudioMedia.ARTIST_ID),
                        albumName = text(AudioMedia.ALBUM), albumId = number(AudioMedia.ALBUM_ID),
                        contentUriString = ContentUris.withAppendedId(uri, id).toString(),
                        albumArtUriString = LocalArtworkUri.buildSongUri(id), duration = duration,
                        genre = null, filePath = path, parentDirectoryPath = parent,
                        dateAdded = number(AudioMedia.DATE_ADDED) * 1000L, mimeType = text(AudioMedia.MIME_TYPE),
                        year = number(AudioMedia.YEAR).toInt(), trackNumber = number(AudioMedia.TRACK).toInt() % 1000,
                        sourceType = SourceType.LOCAL
                    )
                    if (songs.size % 100 == 0) setProgress(workDataOf("scanned" to songs.size, "total" to total))
                }
            } ?: return@withContext Result.failure()
            coroutineContext.ensureActive()
            val albums = songs.groupBy { it.albumId }.map { (id, tracks) ->
                val first = tracks.first()
                AlbumEntity(id, first.albumName, first.artistName, first.artistId,
                    first.albumArtUriString, tracks.size, first.dateAdded, first.year)
            }
            val artists = songs.groupBy { it.artistId }.map { (id, tracks) ->
                ArtistEntity(id, tracks.first().artistName, tracks.size)
            }
            val added = musicDao.importLocalMusic(songs, albums, artists)
            Result.success(workDataOf("added" to added, "local_total" to musicDao.getLocalSongCount().first()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Permission/provider failures retain the existing Room library; no destructive rescan.
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "aro-local-music-import"
        const val CONFIRMED = "confirmed_local_import"
        fun audioPermission(): String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        fun hasAudioPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED
    }
}
