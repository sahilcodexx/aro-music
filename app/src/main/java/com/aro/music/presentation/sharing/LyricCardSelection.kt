package com.aro.music.presentation.sharing

import com.aro.music.data.model.Lyrics
import com.aro.music.utils.LyricsUtils

internal const val LYRIC_CARD_MAX_LINES = 6
internal const val LYRIC_CARD_MAX_CHARACTERS = 300

internal fun lyricCardLines(lyrics: Lyrics?): List<String> {
    fun clean(lines: List<String>) = lines.flatMap { it.lines() }
        .map { LyricsUtils.stripLrcTimestamps(it).trim() }.filter { it.isNotEmpty() }
    val synced = clean(lyrics?.synced.orEmpty().map { it.line })
    return synced.ifEmpty { clean(lyrics?.plain.orEmpty()) }
}

internal fun isValidLyricExcerpt(lines: List<String>): Boolean =
    lines.isNotEmpty() && lines.size <= LYRIC_CARD_MAX_LINES &&
        lines.all { it.isNotBlank() && '\n' !in it && '\r' !in it } &&
        lines.joinToString("\n").length <= LYRIC_CARD_MAX_CHARACTERS

internal fun selectedLyricLines(lines: List<String>, selected: Set<Int>): List<String> =
    lines.filterIndexed { index, _ -> index in selected }
