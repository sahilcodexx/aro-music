package com.aro.music.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.work.*
import com.aro.music.R
import com.aro.music.data.database.*
import com.aro.music.data.innertube.*
import com.aro.music.data.repository.MusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.coroutineContext

@HiltWorker
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val music: MusicRepository,
    private val dao: MusicDao,
    private val innerTube: InnerTubeRepository,
    private val engine: InnerTubeEngine,
    private val downloads: AudioDownloads
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) { transferSlots.withPermit {
        val originalId = inputData.getString("song_id") ?: return@withContext Result.failure()
        var pending: android.net.Uri? = null
        var stage = "start"
        val raw = java.io.File.createTempFile("audio-", ".part", applicationContext.cacheDir)
        try {
            setForeground(foreground())
            val song = music.getSong(originalId).first() ?: return@withContext Result.failure()
            val videoId = InnerTubeIds.videoIdFromContentUri(song.contentUriString) ?: return@withContext Result.failure()
            stage = "resolve"
            setProgress(workDataOf("phase" to stage))
            val stream = innerTube.resolveDownloadStream(videoId) ?: throw IOException("Audio unavailable")
            val url = android.net.Uri.parse(stream.audioUrl)
            check(url.scheme in setOf("https", "sabr"))
            val sourceMime = stream.mimeType?.substringBefore(';') ?: "audio/mp4"
            val mime = downloadAudioMime(sourceMime, stream.codecs)
            val opus = mime == "audio/ogg"
            val extension = if (opus) "opus" else "m4a"
            stage = "save"
            val resolver = applicationContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, audioFileName(song.title, extension))
                put(MediaStore.Audio.Media.TITLE, song.title)
                put(MediaStore.Audio.Media.ARTIST, song.displayArtist)
                put(MediaStore.Audio.Media.ALBUM, song.album)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Aro")
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values))
            pending = uri
            val expected = stream.contentLengthBytes?.takeIf { it > 0 && url.scheme != "sabr" }
            check(expected == null || expected <= MAX_BYTES)
            val total = stream.contentLengthBytes?.takeIf { it > 0 }
            var written = 0L
            var reportedAt = 0L
            // shortcut: interrupted transfers restart; add byte checkpoints if resumable downloads are needed.
            stage = "transfer"
            raw.outputStream().use { output ->
                do {
                    val source: DataSource = if (url.scheme == "sabr") InnerTubeSabrDataSource(engine, forDownload = true)
                    else DefaultHttpDataSource.Factory().setConnectTimeoutMs(20_000).setReadTimeoutMs(30_000)
                        .setDefaultRequestProperties(stream.headers).createDataSource()
                    val chunked = stream.useRangeChunks && expected != null
                    val spec = DataSpec.Builder().setUri(url).setPosition(written).apply {
                        if (chunked) setLength(minOf(stream.rangeChunkSizeBytes.coerceIn(256L * 1024, 8L * 1024 * 1024), expected!! - written))
                    }.build()
                    val before = written
                    DataSourceInputStream(source, spec).use { input ->
                        copyAudio(input, output, MAX_BYTES - before,
                            if (spec.length != C.LENGTH_UNSET.toLong()) spec.length else expected?.minus(before)) { bytes ->
                            written = before + bytes
                            if (System.currentTimeMillis() - reportedAt > 500) {
                                setProgress(workDataOf("bytes" to written, "total" to (total ?: -1), "phase" to stage))
                                reportedAt = System.currentTimeMillis()
                            }
                        }
                    }
                    if (written == before) throw IOException("Empty audio response")
                    if (!chunked) break
                } while (written < expected!!)
            }
            if (written == 0L || (expected != null && written != expected)) throw IOException("Incomplete audio")
            stage = "finalize"
            setProgress(workDataOf("bytes" to written, "total" to written, "phase" to stage))
            // Finalize streamed fragments without re-encoding their audio samples.
            resolver.openFileDescriptor(uri, "rw").use { descriptor ->
                checkNotNull(descriptor)
                remuxAudio(raw, descriptor.fileDescriptor, opus)
            }
            val duration = MediaMetadataRetriever().use { metadata ->
                metadata.setDataSource(applicationContext, uri)
                metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
            check(duration > 0 && (song.duration <= 0 || duration >= song.duration * 0.9)) { "Incomplete audio" }
            stage = "save"
            val localId = android.content.ContentUris.parseId(uri)
            val localMetadata = resolver.query(uri, arrayOf(MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.ARTIST_ID), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) to cursor.getLong(1) else 0L to 0L
            } ?: (0L to 0L)
            // Native IDs keep downloaded copies out of the streamed album's track list.
            val local = song.copy(albumId = localMetadata.first, artistId = localMetadata.second,
                artist = song.displayArtist, artists = emptyList(), id = localId.toString(), contentUriString = uri.toString(), path = uri.toString(),
                duration = duration, mimeType = mime, isFavorite = false, dateAdded = System.currentTimeMillis(),
                telegramFileId = null, telegramChatId = null, neteaseId = null, gdriveFileId = null,
                qqMusicMid = null, navidromeId = null, jellyfinId = null)
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null) == 1)
            dao.importLocalMusic(listOf(local.toEntity(Environment.DIRECTORY_MUSIC + "/Aro/${audioFileName(song.title, extension)}", Environment.DIRECTORY_MUSIC + "/Aro")),
                listOf(AlbumEntity(local.albumId, local.album, local.artist, local.artistId, local.albumArtUriString, 1, local.dateAdded, local.year)),
                listOf(ArtistEntity(local.artistId, local.artist, 1)))
            downloads.record(originalId, local.id)
            pending = null
            Result.success(workDataOf("local_id" to local.id))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: IOException) {
            if (runAttemptCount < 2) Result.retry() else failure(stage, error)
        }
        catch (error: Exception) { failure(stage, error) }
        finally {
            raw.delete()
            pending?.let { uri -> withContext(NonCancellable) { runCatching { applicationContext.contentResolver.delete(uri, null, null) } } }
        }
    }

    }

    private fun failure(stage: String, error: Exception): Result {
        // Never log signed URLs, response bodies, account data, or song metadata.
        timber.log.Timber.w("Audio download failed at %s (%s)", stage, error.javaClass.simpleName)
        return Result.failure(workDataOf("failure_stage" to stage))
    }

    private fun foreground(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("audio-downloads", applicationContext.getString(R.string.audio_downloads), NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "audio-downloads")
            .setSmallIcon(R.drawable.ic_aro_notification)
            .setContentTitle(inputData.getString("title"))
            .setContentText(applicationContext.getString(R.string.audio_download_running))
            .setOngoing(true).setProgress(0, 0, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, applicationContext.getString(android.R.string.cancel), WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return ForegroundInfo(20000 + (id.hashCode() and 0x3fff), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
    companion object {
        private const val MAX_BYTES = 512L * 1024 * 1024
        private val transferSlots = Semaphore(2)
    }
}
