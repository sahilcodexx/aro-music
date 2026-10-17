package com.aro.music.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aro.music.utils.shapes.RoundedStarShape
import kotlinx.collections.immutable.ImmutableList

private data class SetupIconTile(
    val size: Dp,
    val tint: Color,
    val container: Color,
    val alignment: Alignment,
    val rotation: Float,
    val shape: Shape,
    val x: Dp,
    val y: Dp
)

/** Keeps the setup illustration composition while using Aro's expressive shapes and colour roles. */
@Composable
fun PermissionIconCollage(
    @DrawableRes icons: ImmutableList<Int>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    padding: Dp = 0.dp,
    celebration: Boolean = false
) {
    val colours = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth().height(height).padding(padding)) {
        val unit = minOf(200.dp, maxHeight)
        val centreShape = if (celebration) RoundedStarShape(sides = 8, curve = 0.1)
            else RoundedCornerShape(topStart = 44.dp, topEnd = 16.dp, bottomEnd = 44.dp, bottomStart = 16.dp)
        val tiles = listOf(
            SetupIconTile(unit * 0.82f, colours.onSecondaryContainer, colours.secondaryContainer,
                Alignment.Center, -8f, centreShape, 0.dp, 0.dp),
            SetupIconTile(unit * 0.38f, colours.onSurfaceVariant, colours.surfaceContainerHighest,
                Alignment.TopStart, 8f, CircleShape, maxWidth * 0.04f, maxHeight * 0.04f),
            SetupIconTile(unit * 0.40f, colours.onPrimaryContainer, colours.primaryContainer,
                Alignment.BottomEnd, 0f, CircleShape, -maxWidth * 0.04f, -maxHeight * 0.04f),
            SetupIconTile(unit * 0.45f, colours.onTertiaryContainer, colours.tertiaryContainer,
                Alignment.TopEnd, -12f, RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomEnd = 28.dp, bottomStart = 12.dp),
                -maxWidth * 0.08f, maxHeight * 0.08f),
            SetupIconTile(unit * 0.33f, colours.onPrimary, colours.primary,
                Alignment.BottomStart, 8f, RoundedStarShape(sides = 8, curve = 0.1),
                maxWidth * 0.08f, -maxHeight * 0.07f)
        )
        icons.take(tiles.size).forEachIndexed { index, drawable ->
            val tile = tiles[index]
            Icon(
                painter = painterResource(drawable), contentDescription = null, tint = tile.tint,
                modifier = Modifier.size(tile.size).align(tile.alignment).offset(tile.x, tile.y)
                    .graphicsLayer { rotationZ = tile.rotation }
                    .background(tile.container, tile.shape).clip(tile.shape).padding(tile.size * 0.22f)
            )
        }
    }
}
