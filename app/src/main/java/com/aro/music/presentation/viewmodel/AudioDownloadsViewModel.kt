package com.aro.music.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.aro.music.R
import com.aro.music.data.download.AudioDownloads
import com.aro.music.data.model.Song
import com.aro.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class AudioDownloadsViewModel @Inject constructor(
    private val downloads: AudioDownloads,
    private val music: MusicRepository,
    @ApplicationContext private val context: Context,
    @com.aro.music.di.AppScope private val appScope: kotlinx.coroutines.CoroutineScope,
    private val downloadedCollections: com.aro.music.data.download.DownloadedCollections
) : ViewModel() {
    val collections = downloadedCollections.collections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val songs = downloads.songs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val records = downloads.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val work = downloads.work.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _queueing = MutableStateFlow(false)
    val queueing = _queueing.asStateFlow()
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message = _message.asSharedFlow()
    fun download(song: Song) { viewModelScope.launch {
        try { _message.emit(context.getString(if (downloads.enqueue(song)) R.string.audio_download_queued else R.string.audio_download_exists)) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { _message.emit(context.getString(R.string.audio_download_unavailable)) }
    } }
    fun downloadAll(songs: List<Song>, collection: com.aro.music.data.download.DownloadCollection? = null) {
        if (_queueing.value) return
        _queueing.value = true
        // Keep queue submission alive when the user opens Downloads or leaves this page.
        appScope.launch {
            var queued = 0
            var skipped = 0
            var failed = 0
            try {
                collection?.let { downloadedCollections.save(it) }
                for (song in songs.distinctBy { it.id }) {
                    if (com.aro.music.data.innertube.InnerTubeIds.videoIdFromContentUri(song.contentUriString) == null) {
                        skipped++
                        continue
                    }
                    try { if (downloads.enqueue(song)) queued++ else skipped++ }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { failed++ }
                }
                _message.emit(context.getString(R.string.audio_download_batch_result, queued, skipped, failed))
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { _message.emit(context.getString(R.string.audio_download_unavailable)) }
            finally { _queueing.value = false }
        }
    }
    fun retryCollection(collection: com.aro.music.data.download.DownloadCollection) {
        viewModelScope.launch { downloadAll(music.getSongsByIds(collection.songIds).first(), collection) }
    }
    fun retry(songId: String) { viewModelScope.launch { music.getSong(songId).first()?.let { download(it) } } }
    fun refresh() { viewModelScope.launch { downloads.refresh() } }
    fun cancel(id: java.util.UUID) { WorkManager.getInstance(context).cancelWorkById(id) }
}
