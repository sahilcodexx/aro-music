package com.aro.music.presentation.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.aro.music.data.model.Song
import java.io.File

/** One bitmap is used for preview and PNG export, so the share matches the preview. */
object SongShareCard {
    data class Colors(val background: Int, val foreground: Int, val accent: Int, val onAccent: Int)

    enum class Style { ARTWORK, SOFT, DARK }

    fun colorsFor(artwork: Bitmap?, fallback: Colors, style: Style): Colors {
        val palette = artwork?.let {
            androidx.palette.graphics.Palette.from(it).resizeBitmapArea(128 * 128).maximumColorCount(12).generate()
        }
        val seed = palette?.let { it.getVibrantColor(it.getDominantColor(fallback.accent)) } ?: fallback.accent
        val background = when (style) {
            Style.ARTWORK -> androidx.core.graphics.ColorUtils.blendARGB(seed, android.graphics.Color.BLACK, 0.10f)
            Style.SOFT -> androidx.core.graphics.ColorUtils.blendARGB(seed, android.graphics.Color.WHITE, 0.72f)
            Style.DARK -> androidx.core.graphics.ColorUtils.blendARGB(seed, android.graphics.Color.BLACK, 0.86f)
        }
        val foreground = if (androidx.core.graphics.ColorUtils.calculateLuminance(background) > 0.179)
            android.graphics.Color.BLACK else android.graphics.Color.WHITE
        return Colors(background, foreground, seed, android.graphics.Color.WHITE)
    }

    fun render(song: Song, artwork: Bitmap?, colors: Colors, font: Typeface? = null, brandIcon: Bitmap? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 1440, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val black = android.graphics.Color.BLACK
        val white = android.graphics.Color.WHITE
        fun blend(a: Int, b: Int, amount: Float) = androidx.core.graphics.ColorUtils.blendARGB(a, b, amount)
        paint.shader = android.graphics.LinearGradient(0f, 0f, 1080f, 1440f,
            colors.background, blend(colors.background, colors.accent, 0.25f), android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1080f, 1440f, paint)
        paint.shader = android.graphics.RadialGradient(1000f, 40f, 650f,
            intArrayOf(androidx.core.graphics.ColorUtils.setAlphaComponent(colors.foreground, 38), android.graphics.Color.TRANSPARENT),
            null, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1080f, 1440f, paint)
        paint.shader = null

        fun paragraph(value: String, size: Float, width: Int, lines: Int, bold: Boolean, color: Int): StaticLayout {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; textSize = size
                typeface = Typeface.create(font ?: Typeface.create("sans-serif-rounded", Typeface.NORMAL), if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            return StaticLayout.Builder.obtain(value, 0, value.length, textPaint, width)
                .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false)
                .setLineSpacing(4f, 1f).build()
        }
        fun drawText(layout: StaticLayout, x: Float, y: Float) {
            canvas.save(); canvas.translate(x, y); layout.draw(canvas); canvas.restore()
        }
        drawText(paragraph("NOW PLAYING", 28f, 880, 1, true, colors.foreground), 100f, 76f)
        val title = paragraph(song.title, 58f, 792, 2, true, white)
        val artist = paragraph(song.displayArtist, 32f, 792, 1, false, blend(white, colors.accent, 0.20f))
        val titleY = 1040f
        val artistY = titleY + title.height + 12f
        val brandY = artistY + artist.height + 28f
        val panelBottom = brandY + 40f + 32f

        // A quiet tonal surface keeps every cover and title legible on vibrant backgrounds.
        val panel = RectF(96f, 152f, 984f, panelBottom)
        paint.color = blend(colors.accent, black, 0.86f)
        paint.setShadowLayer(28f, 0f, 16f, androidx.core.graphics.ColorUtils.setAlphaComponent(black, 65))
        canvas.drawRoundRect(panel, 48f, 48f, paint)
        paint.clearShadowLayer()
        val cover = RectF(128f, 184f, 952f, 1008f)
        val save = canvas.save()
        canvas.clipPath(android.graphics.Path().apply { addRoundRect(cover, 28f, 28f, android.graphics.Path.Direction.CW) })
        if (artwork != null && artwork.width > 0 && artwork.height > 0) {
            val size = minOf(artwork.width, artwork.height)
            val left = (artwork.width - size) / 2
            val top = (artwork.height - size) / 2
            canvas.drawBitmap(artwork, android.graphics.Rect(left, top, left + size, top + size), cover, paint)
        } else {
            paint.shader = android.graphics.LinearGradient(128f, 184f, 952f, 1008f,
                colors.accent, blend(colors.accent, black, 0.55f), android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(cover, paint); paint.shader = null
            paint.color = colors.onAccent; paint.textSize = 260f; paint.textAlign = Paint.Align.CENTER
            canvas.drawText("♪", 540f, 690f, paint); paint.textAlign = Paint.Align.LEFT
        }
        canvas.restoreToCount(save)
        drawText(title, 144f, titleY)
        drawText(artist, 144f, artistY)
        // One compact lockup inside the card; no separate footer or tagline.
        if (brandIcon != null) {
            paint.color = white
            canvas.drawBitmap(brandIcon, null, RectF(144f, brandY, 184f, brandY + 40f), paint)
        }
        drawText(paragraph("Aro", 30f, 160, 1, true, white), if (brandIcon != null) 196f else 144f, brandY + 2f)
        return bitmap
    }

    fun brandIcon(context: Context): Bitmap {
        val drawable = checkNotNull(androidx.appcompat.content.res.AppCompatResources.getDrawable(context, com.aro.music.R.drawable.aro_base_monochrome)).mutate()
        androidx.core.graphics.drawable.DrawableCompat.setTint(drawable, android.graphics.Color.WHITE)
        return drawable.toBitmap(64, 64)
    }

    fun writePng(context: Context, bitmap: Bitmap): File {
        val directory = File(context.cacheDir, "song-share").apply { mkdirs() }
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        val file = File.createTempFile("aro-song-", ".png", directory)
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return file
    }

    fun intent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Aro song image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
