package com.aro.music.presentation.sharing

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import com.aro.music.R
import com.aro.music.data.model.Song
import com.aro.music.presentation.components.ShimmerBox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongShareSheet(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val fallback = remember(scheme) { SongShareCard.Colors(scheme.secondaryContainer.toArgb(), scheme.onSecondaryContainer.toArgb(), scheme.primary.toArgb(), scheme.onPrimary.toArgb()) }
    var artwork by remember(song.id) { mutableStateOf<Bitmap?>(null) }
    var artworkReady by remember(song.id) { mutableStateOf(false) }
    var style by remember(song.id) { mutableStateOf(SongShareCard.Style.ARTWORK) }
    var bitmap by remember(song.id) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(song.id) { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(song.id, song.albumArtUriString) {
        artwork = withContext(Dispatchers.IO) {
            try {
                song.albumArtUriString?.let { uri ->
                    ImageLoader(context).let { loader ->
                        try { loader.execute(ImageRequest.Builder(context).data(uri).size(904).allowHardware(false).build()).drawable?.toBitmap() }
                        finally { loader.shutdown() }
                    }
                }
            } catch (error: Exception) { if (error is CancellationException) throw error; null }
        }
        artworkReady = true
    }
    LaunchedEffect(song.id, artworkReady, style, fallback) {
        if (!artworkReady) return@LaunchedEffect
        bitmap = null; failed = false
        try {
            bitmap = withContext(Dispatchers.IO) {
                val colors = SongShareCard.colorsFor(artwork, fallback, style)
                val font = ResourcesCompat.getFont(context, R.font.gflex_variable)
                SongShareCard.render(song, artwork, colors, font, SongShareCard.brandIcon(context))
            }
        } catch (error: Exception) { if (error is CancellationException) throw error; failed = true }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SongShareSheetContent(bitmap, failed, sharing, style, { style = it }, onShare = {
            val ready = bitmap
            if (ready != null) scope.launch {
                sharing = true
                try {
                    val file = withContext(Dispatchers.IO) { SongShareCard.writePng(context, ready) }
                    context.startActivity(Intent.createChooser(SongShareCard.intent(context, file), context.getString(R.string.share_song_action)))
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Toast.makeText(context, R.string.share_song_failed, Toast.LENGTH_LONG).show()
                } finally { sharing = false }
            }
        }, onDismiss = onDismiss)
    }
}

/** Preview and controls have separate measured space; the exported bitmap is always fitted whole. */
@Composable
internal fun SongShareSheetContent(
    bitmap: Bitmap?, failed: Boolean, sharing: Boolean, style: SongShareCard.Style,
    onStyle: (SongShareCard.Style) -> Unit, onShare: () -> Unit, onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val compact = configuration.screenHeightDp < 500
    val height = (configuration.screenHeightDp.dp * 0.85f).coerceAtMost(780.dp)
    val preview: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.testTag("song_share_preview"), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap.asImageBitmap(), stringResource(R.string.share_song_image), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else if (!failed) ShimmerBox(Modifier.fillMaxSize())
            else Text(stringResource(R.string.share_song_failed))
        }
    }
    val actions: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SongShareCard.Style.entries.forEach { choice ->
                val label = when (choice) {
                    SongShareCard.Style.ARTWORK -> R.string.share_style_artwork
                    SongShareCard.Style.SOFT -> R.string.share_style_soft
                    SongShareCard.Style.DARK -> R.string.share_style_dark
                }
                FilterChip(selected = style == choice, onClick = { onStyle(choice) }, label = { Text(stringResource(label)) })
            }
        }
        Button(onClick = onShare, enabled = bitmap != null && !sharing, modifier = Modifier.fillMaxWidth().testTag("song_share_action")) {
            Text(stringResource(if (sharing) R.string.share_song_preparing else R.string.share_song_action))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_song_close)) }
    }
    if (compact) {
        Row(Modifier.fillMaxWidth().height(height).navigationBarsPadding().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            preview(Modifier.weight(1f).fillMaxHeight().padding(bottom = 12.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.share_song_title), style = MaterialTheme.typography.titleLarge)
                actions()
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().height(height).navigationBarsPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.share_song_title), style = MaterialTheme.typography.titleLarge)
            preview(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp))
            actions()
            Spacer(Modifier.height(4.dp))
        }
    }
}
