package com.aro.music.presentation.sharing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aro.music.R
import com.aro.music.data.model.Lyrics
import com.aro.music.data.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricCardSheet(song: Song, lyrics: Lyrics, onDismiss: () -> Unit) {
    val lines = remember(lyrics) { lyricCardLines(lyrics) }
    var selected by rememberSaveable(song.id, lines) { mutableStateOf(arrayListOf<Int>()) }
    var preview by rememberSaveable(song.id, lines) { mutableStateOf(false) }
    val excerpt = selectedLyricLines(lines, selected.toSet())
    if (preview && isValidLyricExcerpt(excerpt)) {
        SongShareSheet(song, onDismiss = { preview = false }, lyricText = excerpt.joinToString("\n"))
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            LyricCardSelectionContent(lines, selected.toSet(), onToggle = { index ->
                selected = ArrayList(if (index in selected) selected - index else selected + index)
            }, onPreview = { preview = true }, onDismiss = onDismiss)
        }
    }
}

@Composable
internal fun LyricCardSelectionContent(
    lines: List<String>, selected: Set<Int>, onToggle: (Int) -> Unit,
    onPreview: () -> Unit, onDismiss: () -> Unit
) {
    val excerpt = selectedLyricLines(lines, selected)
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.lyric_card_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.lyric_card_hint, LYRIC_CARD_MAX_LINES, LYRIC_CARD_MAX_CHARACTERS),
            style = MaterialTheme.typography.bodyMedium)
        if (lines.isEmpty()) Text(stringResource(R.string.lyric_card_empty))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                val checked = index in selected
                val enabled = checked || isValidLyricExcerpt(selectedLyricLines(lines, selected + index))
                Surface(shape = MaterialTheme.shapes.large,
                    color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().testTag("lyric_line_$index")
                        .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle(index) })
                        .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked, onCheckedChange = null, enabled = enabled)
                        Text(line, Modifier.padding(start = 12.dp),
                            color = when {
                                checked -> MaterialTheme.colorScheme.onSecondaryContainer
                                enabled -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            })
                    }
                }
            }
        }
        Text(stringResource(R.string.lyric_card_count, excerpt.size, excerpt.joinToString("\n").length),
            style = MaterialTheme.typography.labelLarge)
        Button(onClick = onPreview, enabled = isValidLyricExcerpt(excerpt),
            modifier = Modifier.fillMaxWidth().testTag("lyric_card_preview")) {
            Text(stringResource(R.string.lyric_card_preview))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_song_close)) }
    }
}
