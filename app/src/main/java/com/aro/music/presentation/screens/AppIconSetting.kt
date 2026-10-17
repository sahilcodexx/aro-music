package com.aro.music.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aro.music.R
import com.aro.music.data.appearance.AppIcon
import com.aro.music.presentation.viewmodel.AppIconViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppIconSetting(viewModel: AppIconViewModel = hiltViewModel()) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val changing by viewModel.changing.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(failed) {
        if (failed) {
            Toast.makeText(context, R.string.app_icon_change_failed, Toast.LENGTH_SHORT).show()
            viewModel.clearFailure()
        }
    }
    SettingsItem(
        title = stringResource(R.string.app_icon_title), subtitle = stringResource(selected.label),
        leadingIcon = { Image(painterResource(selected.artwork), null, Modifier.size(24.dp)) },
        onClick = { showPicker = true }
    )
    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { if (!changing) showPicker = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.app_icon_title), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.app_icon_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (changing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(88.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(AppIcon.entries, key = { it.name }) { icon ->
                        val active = icon == selected
                        Surface(
                            modifier = Modifier.fillMaxWidth().selectable(selected = active, enabled = !changing,
                                role = Role.RadioButton, onClick = { viewModel.select(icon) }),
                            shape = RoundedCornerShape(16.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(if (active) 2.dp else 1.dp,
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Image(painterResource(icon.artwork), null, Modifier.size(64.dp))
                                Text(stringResource(icon.label), style = MaterialTheme.typography.labelMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}
