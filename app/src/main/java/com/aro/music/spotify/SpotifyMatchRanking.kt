package com.aro.music.spotify

/** Meld's studio-version preference, without penalizing an intentionally live seed. */
internal object SpotifyMatchRanking {
    private val markers = Regex("\\b(live|en vivo|en directo|ao vivo|karaoke|cover|instrumental|sped up|spedup|slowed|nightcore|8d|music video|official video|lyric video)\\b")
    fun variantPenalty(spotifyTitle: String, candidateTitle: String): Double {
        val wanted = markers.findAll(spotifyTitle.lowercase()).map { it.value }.toSet()
        val extra = markers.findAll(candidateTitle.lowercase()).map { it.value }.toSet() - wanted
        return (extra.size * 0.15).coerceAtMost(0.30)
    }
}
