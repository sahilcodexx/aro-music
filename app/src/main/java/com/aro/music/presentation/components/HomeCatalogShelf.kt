package com.aro.music.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.size.Size
import com.aro.music.data.innertube.HomeCatalogItem
import com.aro.music.data.innertube.HomeCatalogSection

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeCatalogShelf(section: HomeCatalogSection, onMoreClick: (() -> Unit)? = null, onLongClick: ((HomeCatalogItem) -> Unit)? = null, pinnedIds: Set<String> = emptySet(), onClick: (HomeCatalogItem) -> Unit) {
    val width = 144.dp
    val pixels = with(LocalDensity.current) { width.roundToPx() }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(section.title + if (section.browseId != null && onMoreClick != null) "  →" else "",
                style = MaterialTheme.typography.headlineSmall,
                modifier = if (section.browseId != null && onMoreClick != null) Modifier.clickable(onClick = onMoreClick) else Modifier)
            section.label?.takeIf { it.isNotBlank() && !it.equals(section.title, ignoreCase = true) }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(section.items, key = { it.id }, contentType = { it::class.simpleName }) { item ->
                Column(Modifier.width(width).combinedClickable(role = Role.Button, onClickLabel = item.title, onClick = { onClick(item) }, onLongClick = onLongClick?.let { { it(item) } })) {
                    SmartImage(
                        model = item.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.size(width),
                        shape = if (item is HomeCatalogItem.ArtistItem) CircleShape else RoundedCornerShape(16.dp),
                        targetSize = Size(pixels, pixels),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text((if (item is HomeCatalogItem.Track && item.song.id in pinnedIds) "★ " else "") + item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.subtitle.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
