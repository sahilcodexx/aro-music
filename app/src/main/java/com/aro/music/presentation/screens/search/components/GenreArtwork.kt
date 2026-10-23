package com.aro.music.presentation.screens.search.components

import java.util.Locale

internal val genrePhotos = mapOf(
    "bengali" to "studio", "bollywood" to "singer", "devotional" to "piano",
    "english" to "guitar", "gujarati" to "dance", "hindi" to "stage",
    "kannada" to "live", "lofi" to "vinyl", "malayalam" to "keys",
    "marathi" to "microphone", "pop" to "headphones", "punjabi" to "dj",
    "tamil" to "concert", "telugu" to "performance",
)

internal fun genrePhoto(name: String): String {
    val normalized = name.trim().lowercase(Locale.ROOT)
    return genrePhotos[normalized] ?: genrePhotos.values.elementAt(normalized.hashCode().and(Int.MAX_VALUE) % genrePhotos.size)
}
