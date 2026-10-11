package com.aro.music.data.playlist

import java.net.URI
import java.net.URLDecoder

/** Parses identity only; arbitrary URLs are never fetched. */
data class PlaylistLink(val provider: Provider, val id: String) {
    enum class Provider { YOUTUBE, SPOTIFY }
    val localId get() = "import:${provider.name.lowercase()}:$id"

    companion object {
        fun parse(input: String): PlaylistLink? = runCatching {
            val text = input.trim()
            if (text.startsWith("spotify:playlist:")) {
                return@runCatching text.removePrefix("spotify:playlist:").takeIf { it.matches(Regex("[A-Za-z0-9]{22}")) }
                    ?.let { PlaylistLink(Provider.SPOTIFY, it) }
            }
            val uri = URI(if (text.startsWith("https://", true) || text.startsWith("http://", true)) text else "https://$text")
            if (uri.scheme.lowercase() !in listOf("http", "https") || uri.userInfo != null) return@runCatching null
            val host = uri.host?.lowercase() ?: return@runCatching null
            if (host in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "www.youtu.be")) {
                val id = uri.rawQuery?.split('&')?.firstNotNullOfOrNull { part ->
                    val pair = part.split('=', limit = 2)
                    if (pair.size == 2 && pair[0] == "list") URLDecoder.decode(pair[1], "UTF-8") else null
                }?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{10,256}")) } ?: return@runCatching null
                PlaylistLink(Provider.YOUTUBE, id)
            } else if (host == "open.spotify.com") {
                val segments = uri.path.trim('/').split('/').filter { it.isNotBlank() }
                val index = if (segments.firstOrNull()?.startsWith("intl-") == true) 1 else 0
                if (segments.size != index + 2 || segments[index] != "playlist") return@runCatching null
                segments[index + 1].takeIf { it.matches(Regex("[A-Za-z0-9]{22}")) }?.let { PlaylistLink(Provider.SPOTIFY, it) }
            } else null
        }.getOrNull()
    }
}
