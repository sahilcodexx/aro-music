@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.aro.music.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aro.music.R
import com.aro.music.data.download.ALL_DOWNLOADED_SONGS
import com.aro.music.data.download.downloadedSongIds
import com.aro.music.presentation.components.MiniPlayerHeight
import com.aro.music.presentation.components.resolveNavBarOccupiedHeight
import com.aro.music.presentation.components.subcomps.EnhancedSongListItem
import com.aro.music.presentation.viewmodel.AudioDownloadsViewModel
import com.aro.music.presentation.viewmodel.PlayerViewModel

@Composable
fun DownloadedCollectionScreen(collectionId: String, playerViewModel: PlayerViewModel, onBack: () -> Unit,
    viewModel: AudioDownloadsViewModel = hiltViewModel()) {
    val downloads by viewModel.songs.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val state by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val compact by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottom = resolveNavBarOccupiedHeight(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), compact)
    val context = LocalContext.current
    androidx.lifecycle.compose.LifecycleResumeEffect(viewModel) { viewModel.refresh(); onPauseOrDispose {} }
    LaunchedEffect(viewModel) { viewModel.message.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    val collection = collections.firstOrNull { it.id == collectionId }
    val songs = remember(collectionId, downloads, collections, records) {
        val byId = downloads.associateBy { it.id }
        downloadedSongIds(collectionId, collections, records, byId.keys).mapNotNull(byId::get)
    }
    val title = if (collectionId == ALL_DOWNLOADED_SONGS) stringResource(R.string.audio_download_all_songs)
        else collection?.title ?: stringResource(R.string.audio_downloads)
    val subtitle = collection?.let { stringResource(R.string.audio_download_collection_count, songs.size, it.songIds.size) }
        ?: stringResource(R.string.audio_download_saved_count, songs.size)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        LazyColumn(contentPadding = PaddingValues(bottom = bottom + MiniPlayerHeight + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "cover") {
                PlaylistArtworkHeader(name = title, subtitle = subtitle, artwork = collection?.artwork ?: songs.firstOrNull()?.albumArtUriString,
                    headerHeight = 320.dp, collapseFraction = 0f, onBack = onBack, onSort = null, onOptions = null,
                    onShuffle = { playerViewModel.playSongsShuffled(songs, title, startAtZero = true) },
                    shuffleEnabled = songs.isNotEmpty(), sortLabel = "", optionsLabel = "",
                    shuffleLabel = stringResource(R.string.shortcut_shuffle_short), onImageState = {})
            }
            item(key = "actions") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { songs.firstOrNull()?.let { playerViewModel.playSongs(songs, it, title)
                        if (state.isShuffleEnabled) playerViewModel.toggleShuffle()
                    } },
                        enabled = songs.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(62.dp), shape = RoundedCornerShape(26.dp)) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.presentation_batch_b_play_it))
                    }
                    if (collection != null && songs.size < collection.songIds.size) {
                        FilledTonalButton(onClick = { viewModel.retryCollection(collection) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.audio_download_missing))
                        }
                    }
                }
            }
            if (songs.isEmpty()) item { Text(stringResource(R.string.audio_download_collection_empty), Modifier.padding(24.dp)) }
            items(songs, key = { it.id }) { song ->
                EnhancedSongListItem(song = song, isCurrentSong = state.currentSong?.id == song.id, isPlaying = state.isPlaying,
                    showAlbumArt = true, showMoreOptionsButton = false, modifier = Modifier.padding(horizontal = 12.dp),
                    onClick = { playerViewModel.showAndPlaySong(song, songs, title) }, onMoreOptionsClick = {})
            }
        }
    }
}
