package com.aro.music.presentation.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.aro.music.data.database.MusicDao
import com.aro.music.data.worker.LocalMusicImportWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException

@HiltViewModel
class LocalMusicImportViewModel @Inject constructor(
    private val musicDao: MusicDao,
    connectivity: ConnectivityStateHolder,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val isOnline = connectivity.isOnline
    private val workManager = WorkManager.getInstance(context)
    private val preferences = context.getSharedPreferences("local-music-import", Context.MODE_PRIVATE)
    private val requestedId = MutableStateFlow(preferences.getString("work", null))
    private val enqueueMutex = Mutex()
    private val _enqueueFailed = MutableStateFlow(false)
    val enqueueFailed = _enqueueFailed.asStateFlow()
    fun clearEnqueueFailure() { _enqueueFailed.value = false }
    val importWork = combine(
        workManager.getWorkInfosForUniqueWorkFlow(LocalMusicImportWorker.WORK_NAME), requestedId
    ) { work, id -> work.firstOrNull { !it.state.isFinished } ?: work.firstOrNull { it.id.toString() == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Call only after the user's confirmation and the Android audio permission result. */
    fun importConfirmed() {
        if (!LocalMusicImportWorker.hasAudioPermission(context)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                enqueueMutex.withLock {
                    val active = workManager.getWorkInfosForUniqueWork(LocalMusicImportWorker.WORK_NAME).get()
                        .firstOrNull { !it.state.isFinished }
                    val id = if (active != null) active.id else {
                        val request = OneTimeWorkRequestBuilder<LocalMusicImportWorker>()
                            .setInputData(workDataOf(LocalMusicImportWorker.CONFIRMED to true)).build()
                        workManager.enqueueUniqueWork(LocalMusicImportWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request).result.get()
                        request.id
                    }
                    preferences.edit().putString("work", id.toString()).apply()
                    requestedId.value = id.toString()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _enqueueFailed.value = true
            }
        }
    }

    fun cancelImport(id: java.util.UUID) { workManager.cancelWorkById(id) }

    suspend fun shouldOpenOfflineLibrary(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager?.let { it.getNetworkCapabilities(it.activeNetwork) }
        val online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return !online && musicDao.getLocalSongCount().first() > 0
    }
}
