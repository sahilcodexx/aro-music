package com.aro.music.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.ColorScheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.aro.music.presentation.viewmodel.ColorSchemePair
import androidx.core.graphics.ColorUtils

val LocalArtworkThemeActive = staticCompositionLocalOf { false }

val LocalAroDarkTheme = staticCompositionLocalOf { false }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION")
@Composable
fun AroStatusBarStyle(
    color: Color,
    useDarkIcons: Boolean = ColorUtils.calculateLuminance(color.toArgb()) > 0.55,
    navigationColor: Color? = null,
    useDarkNavigationIcons: Boolean = navigationColor
        ?.let { ColorUtils.calculateLuminance(it.toArgb()) > 0.55 }
        ?: useDarkIcons
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val updateNavigationBar = navigationColor != null
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = useDarkIcons

            if (updateNavigationBar) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                isAppearanceLightNavigationBars = useDarkNavigationIcons
            }
        }
    }
}

val DarkColorScheme = darkColorScheme(
    primary = AroPurplePrimary,
    secondary = AroPink,
    tertiary = AroOrange,
    background = AroPurpleDark,
    surface = AroSurface,
    onPrimary = AroWhite,
    onSecondary = AroWhite,
    onTertiary = AroWhite,
    onBackground = AroWhite,
    onSurface = AroLightPurple, // Texto sobre superficies
    error = Color(0xFFFF5252),
    onError = AroWhite
)

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = AroWhite,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = AroPink,
    onSecondary = AroWhite,
    secondaryContainer = AroPink.copy(alpha = 0.15f),
    onSecondaryContainer = AroPink.copy(alpha = 0.85f),
    tertiary = AroOrange,
    onTertiary = AroBlack,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = 0.6f),
    surfaceTint = LightPrimary,
    error = Color(0xFFD32F2F),
    onError = AroWhite
)

/** Shared tonal surfaces keep cards, sheets, and settings in the active palette. */
internal fun tonalSurfaceScheme(scheme: ColorScheme, dark: Boolean): ColorScheme {
    val background = scheme.background
    val lowest = if (dark) lerp(background, Color.Black, 0.10f) else background
    val low = lerp(background, scheme.primaryContainer, 0.18f)
    val container = lerp(background, scheme.primaryContainer, 0.30f)
    val high = lerp(background, scheme.primaryContainer, 0.42f)
    val highest = lerp(background, scheme.primaryContainer, 0.54f)
    val surfaces = listOf(background, lowest, low, container, high, highest)
    fun foreground(candidate: Color): Color {
        fun minimumContrast(color: Color) = surfaces.minOf { surface ->
            val a = color.luminance()
            val b = surface.luminance()
            (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
        }
        if (minimumContrast(candidate) >= 4.5f) return candidate
        return if (minimumContrast(Color.White) >= minimumContrast(Color.Black)) Color.White else Color.Black
    }
    return scheme.copy(
        surfaceContainerLowest = lowest,
        surfaceContainerLow = low,
        surfaceContainer = container,
        surfaceContainerHigh = high,
        surfaceContainerHighest = highest,
        surfaceVariant = highest,
        surfaceDim = if (dark) background else high,
        surfaceBright = if (dark) highest else background,
        onSurface = foreground(scheme.onSurface),
        onSurfaceVariant = foreground(scheme.onSurfaceVariant)
    )
}

/** Keep the artwork hue while reserving bright containers for controls. */
internal fun subtleArtworkScheme(scheme: ColorScheme, dark: Boolean): ColorScheme {
    val background = scheme.primaryContainer.copy(alpha = if (dark) 0.35f else 0.12f)
        .compositeOver(if (dark) Color(0xFF141414) else Color.White)
    return tonalSurfaceScheme(scheme.copy(
        background = background, surface = background,
        onBackground = readableArtworkText(scheme.onBackground, background),
        onPrimary = readableArtworkText(scheme.onPrimary, scheme.primary),
        onPrimaryContainer = readableArtworkText(scheme.onPrimaryContainer, scheme.primaryContainer),
    ), dark)
}

// Desaturating a cover can reduce contrast in the generated palette. Keep its
// text tones when readable, otherwise choose the contrasting neutral endpoint.
private fun readableArtworkText(candidate: Color, background: Color): Color {
    fun contrast(color: Color): Float {
        val first = color.luminance()
        val second = background.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
    if (contrast(candidate) >= 4.5f) return candidate
    return if (contrast(Color.White) >= contrast(Color.Black)) Color.White else Color.Black
}

@Composable
fun AroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemePairOverride: ColorSchemePair? = null,
    subtleArtworkBackground: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val selectedColorScheme = when {
        colorSchemePairOverride == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Tema dinámico del sistema como prioridad si no hay override
            try {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } catch (e: Exception) {
                // Fallback a los defaults si dynamic colors falla (raro, pero posible en algunos dispositivos)
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        colorSchemePairOverride != null -> {
            // Usar el esquema del álbum si se proporciona
            if (darkTheme) colorSchemePairOverride.dark else colorSchemePairOverride.light
        }
        // Fallback final a los defaults si no hay override ni dynamic colors aplicables
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val targetScheme = if (subtleArtworkBackground && colorSchemePairOverride != null) {
        subtleArtworkScheme(selectedColorScheme, darkTheme)
    } else tonalSurfaceScheme(selectedColorScheme, darkTheme)
    val background by animateColorAsState(targetScheme.background, tween(500), label = "ArtworkBackground")
    val finalColorScheme = targetScheme.copy(background = background, surface = background)

    AroStatusBarStyle(
        color = finalColorScheme.background,
        navigationColor = finalColorScheme.background
    )

    CompositionLocalProvider(LocalAroDarkTheme provides darkTheme,
        LocalArtworkThemeActive provides (subtleArtworkBackground && colorSchemePairOverride != null)) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
