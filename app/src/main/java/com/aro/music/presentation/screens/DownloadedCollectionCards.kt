@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.aro.music.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aro.music.R
import com.aro.music.data.download.DownloadCollection
import com.aro.music.data.download.ALL_DOWNLOADED_SONGS
import com.aro.music.data.download.DownloadCollectionKind
import com.aro.music.data.model.Song
import com.aro.music.presentation.components.SmartImage

@Composable
internal fun DownloadedCollectionCards(collections: List<DownloadCollection>, records: Map<String, String>,
    songs: Map<String, Song>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.audio_download_collections), style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "all-songs") {
                Card(onClick = { onOpen(ALL_DOWNLOADED_SONGS) }, modifier = Modifier.width(172.dp), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                Icon(androidx.compose.ui.res.painterResource(R.drawable.aro_download_24), contentDescription = null,
                                    modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Text(stringResource(R.string.audio_download_all_songs), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.audio_download_saved_count, songs.size), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(collections.asReversed(), key = { it.id }) { collection ->
                Card(onClick = { onOpen(collection.id) }, modifier = Modifier.width(172.dp), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmartImage(model = collection.artwork, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)))
                        Text(collection.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(if (collection.kind == DownloadCollectionKind.ALBUM) R.string.audio_download_album else R.string.audio_download_playlist),
                            style = MaterialTheme.typography.labelMedium)
                        Text(stringResource(R.string.audio_download_collection_count, collection.localIds(records, songs.keys).size, collection.songIds.size),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
