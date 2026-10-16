package com.aro.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.aro.music.data.preferences.AlbumArtPaletteStyle
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtworkThemeTest {
    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }

    @Test fun `artwork backgrounds remain subtle and text and tabs remain readable`() {
        val seeds = listOf(Color(0xFFCF70B5), Color(0xFF1D97CF), Color(0xFFDEC247), Color(0xFF6BAD75))
        // Neutral artwork desaturation uses Android Color APIs, exercised on device.
        for (seed in seeds) for (style in AlbumArtPaletteStyle.entries) {
            val pair = generateColorSchemeFromSeed(seed, style)
            for (dark in listOf(true, false)) {
                val original = if (dark) pair.dark else pair.light
                val scheme = subtleArtworkScheme(original, dark)
                assertTrue(contrast(scheme.onBackground, scheme.background) >= 4.5f, "$seed $style background $dark")
                assertTrue(contrast(scheme.onSurface, scheme.surface) >= 4.5f, "$seed $style surface $dark")
                assertTrue(contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f, "$seed $style nav labels $dark")
                assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 4.5f, "$seed $style selected nav icon $dark")
                val containers = listOf(scheme.surfaceContainerLowest, scheme.surfaceContainerLow,
                    scheme.surfaceContainer, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest)
                for (container in containers) {
                    assertTrue(contrast(scheme.onSurface, container) >= 4.5f, "$seed $style card text $dark")
                    assertTrue(contrast(scheme.onSurfaceVariant, container) >= 4.5f, "$seed $style card subtitle $dark")
                }
                if (dark) assertTrue(scheme.background.luminance() < 0.05f, "$seed $style background too bright")
                else assertTrue(scheme.background.luminance() > 0.85f, "$seed $style light background too dark")
            }
        }
    }
    @Test fun `card surface levels inherit the active hue in both themes`() {
        val dark = androidx.compose.material3.darkColorScheme(
            background = Color(0xFF101010), primaryContainer = Color(0xFF005055))
        val light = androidx.compose.material3.lightColorScheme(
            background = Color.White, primaryContainer = Color(0xFF99DDDD))
        for ((original, isDark) in listOf(dark to true, light to false)) {
            val scheme = tonalSurfaceScheme(original, isDark)
            val cards = listOf(scheme.surfaceContainerLow, scheme.surfaceContainer,
                scheme.surfaceContainerHigh, scheme.surfaceContainerHighest)
            assertTrue(cards.distinct().size == cards.size, "Surface levels should remain distinct")
            for (card in cards) {
                assertTrue(card.green > card.red && card.blue > card.red, "Cards should retain the teal palette")
                assertTrue(contrast(scheme.onSurface, card) >= 4.5f)
                assertTrue(contrast(scheme.onSurfaceVariant, card) >= 4.5f)
            }
        }
    }

}
