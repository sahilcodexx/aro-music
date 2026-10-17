package com.aro.music.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aro.music.R

@Composable
fun PlaylistCreationTypeDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onManualSelected: () -> Unit,
    onImportSelected: (() -> Unit)? = null
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_creation_choice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onManualSelected, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.playlist_creation_choice_manual))
                }
                if (onImportSelected != null) {
                    FilledTonalButton(onClick = onImportSelected, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.playlist_creation_choice_import))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
