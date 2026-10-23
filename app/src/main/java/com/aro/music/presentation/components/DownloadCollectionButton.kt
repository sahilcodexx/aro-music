package com.aro.music.presentation.components

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aro.music.R
import com.aro.music.data.innertube.InnerTubeIds
import com.aro.music.data.model.Song
import com.aro.music.presentation.viewmodel.AudioDownloadsViewModel

@Composable
fun DownloadCollectionButton(songs: List<Song>, collection: com.aro.music.data.download.DownloadCollection, enabled: Boolean = true, modifier: Modifier = Modifier,
    viewModel: AudioDownloadsViewModel = hiltViewModel()) {
    val queueing by viewModel.queueing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.message.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }
    val supported = remember(songs) { songs.any { InnerTubeIds.videoIdFromContentUri(it.contentUriString) != null } }
    FilledTonalButton(onClick = { viewModel.downloadAll(songs, collection) }, enabled = enabled && supported && !queueing, modifier = modifier) {
        if (queueing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        else Icon(painterResource(R.drawable.aro_download_24), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(if (queueing) R.string.audio_download_queueing else R.string.audio_download_all))
    }
}
