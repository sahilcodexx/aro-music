package com.aro.music.presentation.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.size.Size
import kotlin.math.roundToInt

internal val LocalPlayerArtworkMotion = staticCompositionLocalOf<PlayerArtworkMotion?> { null }

internal class PlayerArtworkMotion(val fraction: () -> Float) {
    var mini by mutableStateOf<Rect?>(null)
    var full by mutableStateOf<Rect?>(null)
    var origin by mutableStateOf<LayoutCoordinates?>(null)
    var imageReady by mutableStateOf(false)
    fun active(): Boolean = imageReady && fraction() in 0.015f..0.985f && mini != null && full != null && origin?.isAttached == true
}

@Composable
internal fun Modifier.playerArtworkEndpoint(mini: Boolean, enabled: Boolean = true): Modifier {
    val motion = LocalPlayerArtworkMotion.current ?: return this
    if (!enabled) return this
    return onGloballyPositioned { coordinates ->
        val rect = coordinates.boundsInWindow(clipBounds = false)
        if (rect.width > 0 && rect.height > 0) {
            if (mini && motion.fraction() < 0.02f) motion.mini = rect
            else if (!mini && motion.fraction() > 0.02f) motion.full = rect
        }
    }.graphicsLayer { alpha = if (motion.active()) 0f else 1f }
}

internal fun artworkMotionRect(start: Rect, end: Rect, fraction: Float): Rect {
    val t = fraction.coerceIn(0f, 1f)
    fun mix(a: Float, b: Float) = a + (b - a) * t
    return Rect(mix(start.left, end.left), mix(start.top, end.top), mix(start.right, end.right), mix(start.bottom, end.bottom))
}

@Composable
internal fun BoxScope.PlayerArtworkMotionOverlay(motion: PlayerArtworkMotion, model: Any?, targetSize: Size) {
    val radius = with(LocalDensity.current) { 18.dp.toPx() }
    Layout(modifier = Modifier.zIndex(5f).onGloballyPositioned { motion.origin = it }, content = {
        SmartImage(model = model, contentDescription = null, targetSize = targetSize, crossfadeDurationMillis = 0,
            onState = { motion.imageReady = it is coil.compose.AsyncImagePainter.State.Success },
            modifier = Modifier.graphicsLayer {
                alpha = if (motion.active()) 1f else 0f
                val start = motion.mini
                val end = motion.full
                val origin = motion.origin
                if (start != null && end != null && origin?.isAttached == true) {
                    val rect = artworkMotionRect(start, end, motion.fraction())
                    val root = origin.positionInWindow()
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = rect.left - root.x
                    translationY = rect.top - root.y
                    scaleX = rect.width / end.width
                    scaleY = rect.height / end.height
                    val corner = (end.width / 2f) + (radius - end.width / 2f) * motion.fraction().coerceIn(0f, 1f)
                    shape = RoundedCornerShape(androidx.compose.foundation.shape.CornerSize(corner))
                    clip = true
                }
            })
    }) { measurables, _ ->
        val target = motion.full
        val width = target?.width?.roundToInt()?.coerceAtLeast(1) ?: 1
        val height = target?.height?.roundToInt()?.coerceAtLeast(1) ?: 1
        val image = measurables.single().measure(Constraints.fixed(width, height))
        // An overlay must never contribute the full cover's dimensions to the mini-player.
        layout(0, 0) { image.place(0, 0) }
    }
}
