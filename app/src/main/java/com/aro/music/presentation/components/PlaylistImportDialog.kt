package com.aro.music.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aro.music.R
import com.aro.music.data.playlist.PlaylistImportRepository.Failure
import com.aro.music.presentation.viewmodel.PlaylistImportState

@Composable
fun PlaylistImportDialog(
    visible: Boolean, state: PlaylistImportState, onImport: (String) -> Unit,
    onDismiss: () -> Unit, onOpenPlaylist: (String) -> Unit, onConnectSpotify: () -> Unit,
) {
    if (!visible) return
    var link by rememberSaveable { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.playlist_import_title), style = MaterialTheme.typography.headlineSmall)
                val result = state.result
                if (result != null) {
                    Text(stringResource(R.string.playlist_import_saved, result.playlist.songIds.size, result.playlist.name))
                    if (result.notAdded > 0) Text(stringResource(R.string.playlist_import_not_added, result.notAdded), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { onOpenPlaylist(result.playlist.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.playlist_import_open))
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.playlist_import_done)) }
                } else {
                    Text(stringResource(R.string.playlist_import_subtitle), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(link, onValueChange = { link = it }, modifier = Modifier.fillMaxWidth().testTag("playlist_import_link"),
                        label = { Text(stringResource(R.string.playlist_import_hint)) }, singleLine = true,
                        enabled = !state.isLoading, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    Text(stringResource(R.string.playlist_import_copy_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.isLoading) {
                        val progress = state.progress
                        if (progress?.total != null && progress.total > 0) LinearProgressIndicator(progress = { progress.processed.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(when {
                            progress == null -> stringResource(R.string.playlist_import_loading)
                            progress.total != null -> stringResource(R.string.playlist_import_progress_total, progress.processed, progress.total)
                            else -> stringResource(R.string.playlist_import_progress, progress.processed)
                        }, style = MaterialTheme.typography.bodyMedium)
                    }
                    state.error?.let { failure ->
                        Text(stringResource(when (failure) {
                            Failure.INVALID_LINK -> R.string.playlist_import_invalid
                            Failure.CONNECT_SPOTIFY -> R.string.playlist_import_connect
                            Failure.UNAVAILABLE -> R.string.playlist_import_unavailable
                            Failure.EMPTY -> R.string.playlist_import_empty
                            Failure.SAVE_FAILED -> R.string.playlist_import_save_failed
                        }), color = MaterialTheme.colorScheme.error)
                        if (failure == Failure.CONNECT_SPOTIFY) TextButton(onClick = onConnectSpotify) { Text(stringResource(R.string.playlist_import_accounts)) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlist_import_cancel)) }
                        Button(onClick = { onImport(link) }, enabled = link.isNotBlank() && !state.isLoading) { Text(stringResource(R.string.playlist_import_action)) }
                    }
                }
            }
        }
    }
}
