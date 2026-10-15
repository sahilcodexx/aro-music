package com.aro.music.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Fixed geometry reserves the same space as the loaded song rows. */
@Composable
fun SongRowsSkeleton(modifier: Modifier = Modifier, count: Int = 6) {
    Column(modifier.fillMaxWidth().padding(16.dp).clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(count) {
            Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
                ShimmerBox(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
                Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShimmerBox(Modifier.fillMaxWidth(0.8f).height(18.dp).clip(RoundedCornerShape(9.dp)))
                    ShimmerBox(Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(7.dp)))
                }
            }
        }
    }
}
