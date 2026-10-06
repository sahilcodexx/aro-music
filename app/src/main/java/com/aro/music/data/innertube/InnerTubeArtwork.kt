package com.aro.music.data.innertube

import java.net.URI

object InnerTubeArtwork {
    private val dimensions = Regex("=w\\d+-h\\d+")
    private val square = Regex("=s\\d+")

    /** Only Google's resizable artwork hosts accept these dimension suffixes. */
    fun highQuality(url: String?): String? {
        val value = url?.takeIf { it.isNotBlank() } ?: return null
        val host = runCatching { URI(value).host }.getOrNull().orEmpty()
        if (host != "lh3.googleusercontent.com" && host != "yt3.googleusercontent.com") return value
        return when {
            dimensions.containsMatchIn(value) -> value.replace(dimensions, "=w1080-h1080")
            square.containsMatchIn(value) -> value.replace(square, "=s1080")
            else -> value
        }
    }

    fun forSong(url: String?, contentUri: String): String? = highQuality(url)
        ?: InnerTubeIds.videoIdFromContentUri(contentUri)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
}
