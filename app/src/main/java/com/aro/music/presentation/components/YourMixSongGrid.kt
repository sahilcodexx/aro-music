package com.aro.music.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aro.music.data.model.Song
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

private const val YourMixGridColumns = 3
private const val YourMixGridRows = 2
private const val YourMixGridItemsLimit = 24
private val YourMixGridSpacing = 10.dp
private val YourMixGridPadding = 14.dp
private val YourMixGridArtCorner = 12.dp
private val YourMixGridTextGap = 6.dp
private val YourMixGridPeek = 20.dp

/**
 * Horizontal song grid for the home "Your Mix" section.
 *
 * Lays songs out in a fixed 3x2 viewport that scrolls sideways, where each
 * scroll column holds [YourMixGridRows] songs. Mirrors the layout approach of
 * [RecentlyPlayedSection] (plain scroll container + fixed rows rather than a
 * nested lazy grid) so it stays safe inside the home LazyColumn.
 *
 * Sizing is derived from the incoming max width so the grid adapts to the
 * device instead of relying on hard-coded card widths.
 */
@Composable
fun YourMixSongGrid(
    songs: ImmutableList<Song>,
    modifier: Modifier = Modifier,
    onSongClick: (Song) -> Unit
) {
    val visibleSongs = remember(songs) {
        songs.take(YourMixGridItemsLimit).toImmutableList()
    }
    if (visibleSongs.isEmpty()) return

    val scrollState = rememberScrollState()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Width that fits YourMixGridColumns cards, minus a peek so the next
        // column is partially visible and the section reads as scrollable.
        val cardWidth = (
            (maxWidth - (YourMixGridPadding * 2)) -
                (YourMixGridSpacing * (YourMixGridColumns - 1)) -
                YourMixGridPeek
            ) / YourMixGridColumns

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = YourMixGridPadding),
            horizontalArrangement = Arrangement.spacedBy(YourMixGridSpacing)
        ) {
            visibleSongs
                .chunked(YourMixGridRows)
                .forEach { columnSongs ->
                    Column(verticalArrangement = Arrangement.spacedBy(YourMixGridSpacing)) {
                        columnSongs.forEach { song ->
                            YourMixSongGridCard(
                                song = song,
                                cardWidth = cardWidth,
                                onClick = { onSongClick(song) }
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun YourMixSongGridCard(
    song: Song,
    cardWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(cardWidth)
            .clickable(onClick = onClick)
    ) {
        SmartImage(
            model = song.albumArtUriString,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            shape = RoundedCornerShape(YourMixGridArtCorner),
            targetSize = SmartImageListTargetSize,
            modifier = Modifier.size(cardWidth)
        )
        Spacer(modifier = Modifier.height(YourMixGridTextGap))
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = song.displayArtist,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}