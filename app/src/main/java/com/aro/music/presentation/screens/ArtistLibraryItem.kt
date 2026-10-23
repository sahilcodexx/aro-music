package com.aro.music.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.size.Size
import com.aro.music.R
import com.aro.music.data.model.Artist
import com.aro.music.presentation.components.SmartImage
import com.aro.music.presentation.components.ShimmerBox
import com.aro.music.utils.formatSongCount

/** Shares image loading, metadata and sizing between the artist list and cards. */
@Composable
internal fun ArtistLibraryItem(
    artist: Artist,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    isCard: Boolean,
    fallbackArtwork: String? = null
) {
    Card(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isCard) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        if (isCard) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ArtistThumbnail(artist, fallbackArtwork, isLoading, Modifier.fillMaxWidth().aspectRatio(1f))
                ArtistLabels(artist, isLoading)
            }
        } else {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtistThumbnail(artist, fallbackArtwork, isLoading, Modifier.size(60.dp))
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) { ArtistLabels(artist, isLoading) }
            }
        }
    }
}

@Composable
private fun ArtistLabels(artist: Artist, isLoading: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isLoading) {
            ShimmerBox(Modifier.fillMaxWidth(0.8f).height(20.dp).clip(RoundedCornerShape(4.dp)))
            ShimmerBox(Modifier.fillMaxWidth(0.45f).height(16.dp).clip(RoundedCornerShape(4.dp)))
        } else {
            Text(artist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (artist.songCount > 0) formatSongCount(artist.songCount) else androidx.compose.ui.res.stringResource(R.string.catalog_artist_label), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ArtistThumbnail(artist: Artist, fallbackArtwork: String?, isLoading: Boolean, modifier: Modifier) {
    if (isLoading) {
        ShimmerBox(modifier.clip(CircleShape))
        return
    }
    var useFallback by remember(artist.effectiveImageUrl, fallbackArtwork) { mutableStateOf(false) }
    val model = if (useFallback) fallbackArtwork else artist.effectiveImageUrl ?: fallbackArtwork
    var loading by remember(model) { mutableStateOf(model != null) }
    Box(modifier.clip(CircleShape)) {
        SmartImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            targetSize = Size(320, 320),
            placeholderResId = R.drawable.rounded_artist_24,
            errorResId = R.drawable.rounded_artist_24,
            onState = { state ->
                loading = state is AsyncImagePainter.State.Loading
                if (state is AsyncImagePainter.State.Error && !useFallback &&
                    !fallbackArtwork.isNullOrBlank() && model != fallbackArtwork) useFallback = true
            }
        )
        if (loading) ShimmerBox(Modifier.fillMaxSize().clip(CircleShape))
    }
}
