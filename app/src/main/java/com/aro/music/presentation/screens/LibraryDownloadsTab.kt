@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.aro.music.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.aro.music.R
import com.aro.music.presentation.components.MiniPlayerHeight
import com.aro.music.presentation.viewmodel.AudioDownloadsViewModel
import com.aro.music.presentation.viewmodel.PlayerViewModel

@Composable
fun LibraryDownloadsTab(playerViewModel: PlayerViewModel, bottomBarHeight: Dp, onOpenCollection: (String) -> Unit,
    viewModel: AudioDownloadsViewModel = hiltViewModel()) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val work by viewModel.work.collectAsStateWithLifecycle()
    val context = LocalContext.current
    androidx.lifecycle.compose.LifecycleResumeEffect(viewModel) { viewModel.refresh(); onPauseOrDispose {} }
    LaunchedEffect(viewModel) { viewModel.message.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    val active = work.filter { !it.state.isFinished || it.state == WorkInfo.State.FAILED }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp,
        bottom = bottomBarHeight + MiniPlayerHeight + 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.audio_download_summary, active.count { !it.state.isFinished }, songs.size), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.audio_download_location), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { DownloadedCollectionCards(collections, records, songsById, onOpenCollection) }
        if (songs.isEmpty() && active.isEmpty() && collections.isEmpty()) item { Text(stringResource(R.string.audio_download_empty), modifier = Modifier.padding(vertical = 24.dp)) }
        items(active, key = { it.id }) { task ->
            val songId = task.tags.firstOrNull { it.startsWith("audio-song:") }?.removePrefix("audio-song:")
            DownloadProgressCard(task, onRetry = { songId?.let(viewModel::retry) }, onCancel = { viewModel.cancel(task.id) })
        }
    }
}

@Composable
private fun DownloadProgressCard(task: WorkInfo, onRetry: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val title = task.tags.firstOrNull { it.startsWith("audio-title:") }?.removePrefix("audio-title:").orEmpty()
    val done = task.progress.getLong("bytes", 0).coerceAtLeast(0)
    val total = task.progress.getLong("total", -1)
    val phase = task.progress.getString("phase")
    val failed = task.state == WorkInfo.State.FAILED
    val queued = task.state == WorkInfo.State.ENQUEUED || task.state == WorkInfo.State.BLOCKED || phase == null
    val status = when {
        failed -> when (task.outputData.getString("failure_stage")) {
            "resolve" -> R.string.audio_download_resolve_failed
            "transfer" -> R.string.audio_download_transfer_failed
            "finalize" -> R.string.audio_download_finalize_failed
            "save" -> R.string.audio_download_save_failed
            else -> R.string.audio_download_retry
        }
        queued -> R.string.audio_download_waiting
        phase == "resolve" -> R.string.audio_download_resolving
        phase == "finalize" -> R.string.audio_download_finishing
        else -> R.string.audio_download_running
    }
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(androidx.compose.ui.res.painterResource(R.drawable.aro_download_24), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp).size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(stringResource(status), style = MaterialTheme.typography.bodySmall)
                }
                if (failed) TextButton(onClick = onRetry) { Text(stringResource(R.string.audio_download_retry_action)) }
                else IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, stringResource(android.R.string.cancel)) }
            }
            if (!failed) {
                val fraction = if (total > 0) (done.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else null
                if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (done > 0) {
                    val size = android.text.format.Formatter.formatShortFileSize(context, done)
                    Text(if (fraction != null) stringResource(R.string.audio_download_progress,
                        (fraction * 100).toInt(), size, android.text.format.Formatter.formatShortFileSize(context, total)) else size,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
