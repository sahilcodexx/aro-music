package com.aro.music.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Mirrors the actual shelf dimensions, with compact metadata spacing and font scaling. */
@Composable
fun HomeSongShelfPlaceholder(title: String, label: String?, loading: Boolean, emptyMessage: String) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            label?.takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Box(Modifier.fillMaxWidth()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false) {
                items(if (loading) 3 else 1) {
                    Column(Modifier.width(144.dp).testTag("home_shelf_skeleton")) {
                        if (loading) ShimmerBox(Modifier.size(144.dp).clip(RoundedCornerShape(16.dp)))
                        else Spacer(Modifier.size(144.dp))
                        Spacer(Modifier.height(6.dp))
                        Box {
                            Text("Song title", style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2, color = Color.Transparent)
                            if (loading) ShimmerBox(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(6.dp)))
                        }
                        Spacer(Modifier.height(2.dp))
                        Box {
                            Text("Artist", style = MaterialTheme.typography.bodySmall, minLines = 1, maxLines = 1, color = Color.Transparent)
                            if (loading) ShimmerBox(Modifier.width(90.dp).height(10.dp).clip(RoundedCornerShape(6.dp)))
                        }
                    }
                }
            }
            if (!loading) Text(emptyMessage, Modifier.align(Alignment.Center).padding(24.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
