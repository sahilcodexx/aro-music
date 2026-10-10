package com.aro.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.aro.music.data.preferences.AlbumArtPaletteStyle
import org.junit.Assert.assertTrue
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.junit.Test

@RunWith(AndroidJUnit4::class)
class ArtworkThemeContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }

    @Test fun `artwork backgrounds remain subtle and text and tabs remain readable`() {
        val seeds = listOf(Color(0xFFCF70B5), Color(0xFF1D97CF), Color(0xFFDEC247), Color(0xFF6BAD75), Color.White, Color.Black)
        for (seed in seeds) for (style in AlbumArtPaletteStyle.entries) {
            val pair = generateColorSchemeFromSeed(seed, style)
            for (dark in listOf(true, false)) {
                val original = if (dark) pair.dark else pair.light
                val scheme = subtleArtworkScheme(original, dark)
                assertTrue("$seed $style background $dark", contrast(scheme.onBackground, scheme.background) >= 4.5f)
                assertTrue("$seed $style surface $dark", contrast(scheme.onSurface, scheme.surface) >= 4.5f)
                assertTrue("$seed $style nav labels $dark", contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
                assertTrue("$seed $style selected nav icon $dark", contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
                if (dark) assertTrue("$seed $style background too bright", scheme.background.luminance() < 0.05f)
                else assertTrue("$seed $style light background too dark", scheme.background.luminance() > 0.85f)
            }
        }
    }
}
