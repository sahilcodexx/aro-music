package com.aro.music.presentation.components

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.aro.music.R
import com.aro.music.data.worker.LocalMusicImportWorker
import com.aro.music.presentation.viewmodel.LocalMusicImportViewModel

@Composable
fun LocalMusicImportPrompt(
    showImportChoices: Boolean,
    onDismissChoices: () -> Unit,
    onImportPlaylist: () -> Unit,
    viewModel: LocalMusicImportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val work by viewModel.importWork.collectAsStateWithLifecycle()
    var confirmLocal by remember { mutableStateOf(false) }
    val preferences = remember { context.getSharedPreferences("local-music-import", Context.MODE_PRIVATE) }
    var dismissed by remember { mutableStateOf(preferences.getString("dismissed", null)) }
    val deniedMessage = stringResource(R.string.local_import_permission_denied)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.importConfirmed()
        else Toast.makeText(context, deniedMessage, Toast.LENGTH_LONG).show()
    }
    val enqueueFailed by viewModel.enqueueFailed.collectAsStateWithLifecycle()
    val enqueueFailureMessage = stringResource(R.string.local_import_failed_body)
    LaunchedEffect(enqueueFailed) {
        if (enqueueFailed) {
            Toast.makeText(context, enqueueFailureMessage, Toast.LENGTH_LONG).show()
            viewModel.clearEnqueueFailure()
        }
    }
    val busy = work?.let { !it.state.isFinished } == true
    if (showImportChoices) AlertDialog(
        onDismissRequest = onDismissChoices,
        title = { Text(stringResource(R.string.local_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.local_import_choose_source))
                FilledTonalButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    onDismissChoices(); confirmLocal = true
                }) { Text(stringResource(R.string.local_import_from_phone)) }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                    onDismissChoices(); onImportPlaylist()
                }) { Text(stringResource(R.string.local_import_playlist_file)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismissChoices) { Text(stringResource(R.string.local_import_cancel)) } }
    )
    if (confirmLocal) AlertDialog(
        onDismissRequest = { confirmLocal = false },
        title = { Text(stringResource(R.string.local_import_confirm_title)) },
        text = { Text(stringResource(R.string.local_import_confirm_body)) },
        confirmButton = {
            TextButton(onClick = {
                confirmLocal = false
                if (LocalMusicImportWorker.hasAudioPermission(context)) viewModel.importConfirmed()
                else permission.launch(LocalMusicImportWorker.audioPermission())
            }) { Text(stringResource(R.string.local_import_confirm)) }
        },
        dismissButton = { TextButton(onClick = { confirmLocal = false }) { Text(stringResource(R.string.local_import_cancel)) } }
    )
    val current = work ?: return
    if (current.state == WorkInfo.State.CANCELLED) return
    val token = "${current.id}:${if (current.state.isFinished) current.state.name else "active"}"
    if (dismissed == token) return
    fun dismissResult() {
        dismissed = token
        preferences.edit().putString("dismissed", token).apply()
    }
    when (current.state) {
        WorkInfo.State.SUCCEEDED -> AlertDialog(
            onDismissRequest = ::dismissResult,
            title = { Text(stringResource(R.string.local_import_done_title)) },
            text = { Text(stringResource(R.string.local_import_done_body,
                current.outputData.getInt("added", 0), current.outputData.getInt("local_total", 0))) },
            confirmButton = { TextButton(onClick = ::dismissResult) { Text(stringResource(R.string.local_import_done)) } }
        )
        WorkInfo.State.FAILED -> AlertDialog(
            onDismissRequest = ::dismissResult,
            title = { Text(stringResource(R.string.local_import_failed_title)) },
            text = { Text(stringResource(R.string.local_import_failed_body)) },
            confirmButton = { TextButton(onClick = { dismissResult(); confirmLocal = true }) { Text(stringResource(R.string.local_import_retry)) } },
            dismissButton = { TextButton(onClick = ::dismissResult) { Text(stringResource(R.string.local_import_cancel)) } }
        )
        else -> AlertDialog(
            onDismissRequest = ::dismissResult,
            title = { Text(stringResource(R.string.local_import_progress_title)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.local_import_progress_body, current.progress.getInt("scanned", 0)))
            } },
            confirmButton = { TextButton(onClick = ::dismissResult) { Text(stringResource(R.string.local_import_background)) } },
            dismissButton = { TextButton(onClick = { viewModel.cancelImport(current.id); dismissResult() }) { Text(stringResource(R.string.local_import_cancel)) } }
        )
    }
}
