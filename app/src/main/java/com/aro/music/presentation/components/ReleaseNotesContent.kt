package com.aro.music.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.net.URI

internal sealed interface ReleaseNoteBlock {
    data class Heading(val text: String, val level: Int) : ReleaseNoteBlock
    data class Paragraph(val text: String) : ReleaseNoteBlock
    data class Bullet(val text: String) : ReleaseNoteBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : ReleaseNoteBlock
    data class Code(val text: String) : ReleaseNoteBlock
}

/** The release-note subset: headings, paragraphs, lists, fenced code and GitHub tables. */
internal fun parseReleaseNotes(markdown: String): List<ReleaseNoteBlock> {
    val lines = markdown.replace("\r\n", "\n").lines()
    val result = mutableListOf<ReleaseNoteBlock>()
    fun cells(line: String) = line.trim().trim('|').split(Regex("(?<!\\\\)\\|")).map { it.trim().replace("\\|", "|") }
    fun separator(line: String) = cells(line).all { it.matches(Regex(":?-{3,}:?")) }
    var index = 0
    while (index < lines.size) {
        val line = lines[index].trim()
        when {
            line.isBlank() || line.matches(Regex("[-*_]{3,}")) -> index++
            line.startsWith("```") -> {
                val body = mutableListOf<String>(); index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) body += lines[index++]
                result += ReleaseNoteBlock.Code(body.joinToString("\n")); if (index < lines.size) index++
            }
            index + 1 < lines.size && '|' in line && separator(lines[index + 1]) -> {
                val headers = cells(line); index += 2
                val rows = mutableListOf<List<String>>()
                while (index < lines.size && '|' in lines[index] && lines[index].isNotBlank()) rows += cells(lines[index++])
                result += ReleaseNoteBlock.Table(headers, rows)
            }
            line.matches(Regex("#{1,6}\\s+.*")) -> {
                result += ReleaseNoteBlock.Heading(line.substringAfter(' '), line.takeWhile { it == '#' }.length); index++
            }
            line.matches(Regex("(?:[-*+] |[0-9]+[.)] ).*")) -> {
                result += ReleaseNoteBlock.Bullet(line.replaceFirst(Regex("^(?:[-*+] |[0-9]+[.)] )"), "")); index++
            }
            else -> { result += ReleaseNoteBlock.Paragraph(line); index++ }
        }
    }
    return result
}

internal fun releaseNoteText(text: String): AnnotatedString = buildAnnotatedString {
    val tokens = Regex("\\[([^]\\n]+)]\\(([^)\\s]+)\\)|\\*\\*(.+?)\\*\\*|`([^`]+)`")
    var end = 0
    for (token in tokens.findAll(text)) {
        append(text.substring(end, token.range.first))
        when {
            token.groups[1] != null -> {
                val label = token.groupValues[1].removeSurrounding("**")
                val url = token.groupValues[2]
                val safe = runCatching { URI(url).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null } }.getOrDefault(false)
                if (safe) withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)))) { append(label) }
                else append(label)
            }
            token.groups[3] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.groupValues[3]) }
            else -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(token.groupValues[4]) }
        }
        end = token.range.last + 1
    }
    append(text.substring(end))
}

@Composable
internal fun ReleaseNotesContent(markdown: String) {
    val blocks = remember(markdown) { parseReleaseNotes(markdown) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ReleaseNoteBlock.Heading -> Text(releaseNoteText(block.text), style = if (block.level <= 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
                is ReleaseNoteBlock.Paragraph -> Text(releaseNoteText(block.text), style = MaterialTheme.typography.bodyMedium)
                is ReleaseNoteBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•"); Text(releaseNoteText(block.text), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
                is ReleaseNoteBlock.Code -> Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(block.text, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
                is ReleaseNoteBlock.Table -> block.rows.forEach { row ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            block.headers.forEachIndexed { index, header ->
                                row.getOrNull(index)?.let { value ->
                                    Text(releaseNoteText(header), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(releaseNoteText(value), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
