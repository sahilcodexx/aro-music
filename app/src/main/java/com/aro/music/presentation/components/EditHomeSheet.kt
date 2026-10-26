@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.aro.music.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import com.aro.music.R
import com.aro.music.data.preferences.HomeLayout
import com.aro.music.data.preferences.HomeSection
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun EditHomeSheet(layout: HomeLayout, onDismiss: () -> Unit, onSave: (HomeLayout) -> Unit) {
    var order by remember { mutableStateOf(layout.order) }
    var hidden by remember { mutableStateOf(layout.hidden) }
    val list = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(lazyListState = list, onMove = { from, to ->
        order = order.toMutableList().apply { add(to.index, removeAt(from.index)) }
    })
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.home_edit_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.home_edit_hint), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
            LazyColumn(state = list, modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(order, key = { it.name }) { section ->
                    val label = stringResource(section.label())
                    val up = stringResource(R.string.home_edit_move_up)
                    val down = stringResource(R.string.home_edit_move_down)
                    ReorderableItem(reorder, key = section.name) { dragging ->
                        Surface(modifier = Modifier.semantics {
                            customActions = buildList {
                                val index = order.indexOf(section)
                                fun move(to: Int): Boolean { order = order.toMutableList().apply { add(to, removeAt(index)) }; return true }
                                if (index > 0) add(CustomAccessibilityAction(up) { move(index - 1) })
                                if (index < order.lastIndex) add(CustomAccessibilityAction(down) { move(index + 1) })
                            }
                        }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shadowElevation = if (dragging) 4.dp else 0.dp) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.DragIndicator, stringResource(R.string.cd_drag_handle), Modifier.draggableHandle())
                                Text(label, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
                                Checkbox(modifier = Modifier.semantics { contentDescription = label }, checked = section !in hidden,
                                    enabled = section in hidden || order.size - hidden.size > 1,
                                    onCheckedChange = { checked -> hidden = if (checked) hidden - section else hidden + section })
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { order = HomeSection.entries.toList(); hidden = emptySet() }) { Text(stringResource(R.string.home_edit_reset)) }
                Button(onClick = { onSave(HomeLayout(order, hidden)) }) { Text(stringResource(R.string.home_edit_save)) }
            }
        }
    }
}
private fun HomeSection.label() = when (this) {
    HomeSection.RECENT -> R.string.home_edit_recent
    HomeSection.SPEED_DIAL -> R.string.home_speed_dial
    HomeSection.DAILY_MIX -> R.string.home_edit_daily_mix
    HomeSection.YOUR_MIX -> R.string.home_edit_your_mix
    HomeSection.DISCOVER -> R.string.home_edit_discover
    HomeSection.STATS -> R.string.presentation_batch_g_stats_title
}
