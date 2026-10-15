package com.aro.music.presentation.sharing

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aro.music.data.model.Song
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongShareCardDeviceTest {
    @Test fun pngIsReadableAndSharedWithTemporaryReadPermission() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val song = Song.emptySong().copy(title = "A long song title ".repeat(20), artist = "Artist")
        val bitmap = SongShareCard.render(song, null, SongShareCard.Colors(Color.DKGRAY, Color.WHITE, Color.CYAN, Color.BLACK))
        assertEquals(1080, bitmap.width); assertEquals(1440, bitmap.height)
        val file = SongShareCard.writePng(context, bitmap)
        try {
            val intent = SongShareCard.intent(context, file)
            assertEquals("image/png", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
            assertEquals("content", uri.scheme)
            val decoded = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            assertNotNull(decoded); assertEquals(1080, decoded.width); assertEquals(1440, decoded.height)
            decoded.recycle()
        } finally { file.delete(); bitmap.recycle() }
    }

    @Test fun cardIncludesCenterCroppedArtwork() {
        val artwork = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        val bitmap = SongShareCard.render(Song.emptySong().copy(title = "Song", artist = "Artist"), artwork, SongShareCard.Colors(Color.BLACK, Color.WHITE, Color.BLUE, Color.YELLOW))
        try { assertEquals(Color.MAGENTA, bitmap.getPixel(540, 540)); assertEquals(Color.BLACK, bitmap.getPixel(0, 0)) }
        finally { artwork.recycle(); bitmap.recycle() }
    }
    @Test fun artworkStylesKeepOuterTextLegible() {
        val artwork = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).apply {
            val canvas = android.graphics.Canvas(this)
            val paint = android.graphics.Paint().apply {
                shader = android.graphics.LinearGradient(0f, 0f, 160f, 160f, Color.rgb(20, 160, 150), Color.rgb(240, 170, 65), android.graphics.Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, 160f, 160f, paint)
        }
        try {
            val fallback = SongShareCard.Colors(Color.BLACK, Color.WHITE, Color.CYAN, Color.BLACK)
            val palettes = SongShareCard.Style.entries.map { SongShareCard.colorsFor(artwork, fallback, it) }
            assertEquals(3, palettes.map { it.background }.distinct().size)
            palettes.forEach { assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(it.foreground, it.background) >= 4.5) }
            if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("captureShareCard") == "true") {
                val context = ApplicationProvider.getApplicationContext<android.content.Context>()
                val card = SongShareCard.render(Song.emptySong().copy(title = "A little more rhythm", artist = "Aro Sessions"), artwork, palettes.first(), androidx.core.content.res.ResourcesCompat.getFont(context, com.aro.music.R.font.gflex_variable), SongShareCard.brandIcon(context))
                try { java.io.File(context.cacheDir, "share-card-design.png").outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                finally { card.recycle() }
            }
        } finally { artwork.recycle() }
    }

}
