package com.aro.music.presentation.screens

import com.aro.music.presentation.navigation.navigateSafely
import com.aro.music.presentation.navigation.navigateSafelyReplacing

import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import com.aro.music.presentation.components.CollapsibleCommonTopBar
import com.aro.music.utils.shapes.RoundedStarShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.size.Size
import com.aro.music.R
import com.aro.music.data.model.Song
import com.aro.music.presentation.components.MiniPlayerHeight
import com.aro.music.presentation.components.PlaylistBottomSheet
import com.aro.music.presentation.components.QueuePlaylistSongItem
import com.aro.music.presentation.components.SongPickerBottomSheet
import com.aro.music.presentation.components.ExpressiveScrollBar
import com.aro.music.presentation.components.SmartImage
import com.aro.music.presentation.components.SongInfoBottomSheet
import com.aro.music.presentation.components.resolveNavBarOccupiedHeight
import com.aro.music.presentation.navigation.Screen
import com.aro.music.presentation.viewmodel.PlayerViewModel
import com.aro.music.presentation.viewmodel.PlaylistViewModel
import com.aro.music.presentation.viewmodel.PlaylistViewModel.Companion.FOLDER_PLAYLIST_PREFIX
import com.aro.music.presentation.utils.LocalAppHapticsConfig
import com.aro.music.presentation.utils.performAppCompatHapticFeedback
import com.aro.music.ui.theme.GoogleSansRounded
import com.aro.music.ui.theme.LocalAroDarkTheme
import coil.compose.AsyncImagePainter
import com.aro.music.presentation.viewmodel.PlaylistSongsOrderMode
import com.aro.music.utils.formatSongCount
import com.aro.music.utils.formatTotalDuration
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.aro.music.presentation.components.LibrarySortBottomSheet
import com.aro.music.data.model.SortOption
import com.aro.music.data.model.PlaylistShapeType
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onDeletePlayListClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val playerStableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fallbackPlaylistName = stringResource(R.string.shortcut_playlist_short)
    val sortSongsLabel = stringResource(R.string.presentation_batch_b_sort_songs)
    val moreOptionsLabel = stringResource(R.string.presentation_batch_b_more_options)
    val playItLabel = stringResource(R.string.presentation_batch_b_play_it)
    val shuffleLabel = stringResource(R.string.shortcut_shuffle_short)
    val addSongsCd = stringResource(R.string.presentation_batch_b_add_songs)
    val addLabel = stringResource(R.string.presentation_batch_b_add)
    val removeLabel = stringResource(R.string.cd_remove)
    val removeSongsCd = stringResource(R.string.presentation_batch_b_remove_songs)
    val reorderLabel = stringResource(R.string.presentation_batch_b_reorder)
    val reorderSongsCd = stringResource(R.string.presentation_batch_b_reorder_songs)
    val reorderSongCd = stringResource(R.string.presentation_batch_b_reorder_song)
    val playlistEmptyTitle = stringResource(R.string.presentation_batch_b_playlist_empty_title)
    val playlistEmptyFolder = stringResource(R.string.presentation_batch_b_playlist_empty_folder_body)
    val playlistEmptyAddHint = stringResource(R.string.presentation_batch_b_playlist_empty_add_hint)
    val playlistOptionsTitle = stringResource(R.string.presentation_batch_b_playlist_options_title)
    val editPlaylistLabel = stringResource(R.string.presentation_batch_b_edit_playlist)
    val deletePlaylistLabel = stringResource(R.string.presentation_batch_b_delete_playlist)
    val setDefaultTransitionLabel = stringResource(R.string.presentation_batch_b_set_default_transition)
    val exportPlaylistLabel = stringResource(R.string.presentation_batch_b_export_playlist)
    val deletePlaylistConfirmTitle = stringResource(R.string.presentation_batch_b_delete_playlist_confirm_title)
    val deletePlaylistConfirmBody = stringResource(R.string.presentation_batch_b_delete_playlist_confirm_body)
    val sortSheetTitle = stringResource(R.string.presentation_batch_b_sort_songs)
    val toastAddedToQueue = stringResource(R.string.toast_added_to_queue)
    val toastPlayingNext = stringResource(R.string.toast_playing_next)
    val currentPlaylist = uiState.currentPlaylistDetails
    val isFolderPlaylist = currentPlaylist?.id?.startsWith(FOLDER_PLAYLIST_PREFIX) == true
    val songsInPlaylist = uiState.currentPlaylistSongs
    val firstSongArt = remember(currentPlaylist?.songIds, songsInPlaylist) {
        val firstId = currentPlaylist?.songIds?.firstOrNull()
        (songsInPlaylist.find { it.id == firstId } ?: songsInPlaylist.firstOrNull())
            ?.albumArtUriString?.takeIf(String::isNotBlank)
    }
    val explicitCover = currentPlaylist?.coverImageUri?.takeIf(String::isNotBlank)
    var explicitCoverFailed by remember(explicitCover) { mutableStateOf(false) }
    val headerArt = if (explicitCoverFailed) firstSongArt else explicitCover ?: firstSongArt
    val baseScheme = MaterialTheme.colorScheme
    val isDarkTheme = LocalAroDarkTheme.current
    val headerSchemeFlow = remember(headerArt) {
        headerArt?.let { playerViewModel.themeStateHolder.getAlbumColorSchemeFlow(it, eager = false) }
    }
    val headerSchemePair = headerSchemeFlow?.collectAsStateWithLifecycle()?.value
    val headerScheme = headerSchemePair?.let { if (isDarkTheme) it.dark else it.light } ?: baseScheme
    val artworkErrorMessage = stringResource(R.string.playlist_artwork_failed)
    LaunchedEffect(uiState.artworkUpdateFailed) {
        if (uiState.artworkUpdateFailed) {
            android.widget.Toast.makeText(context, artworkErrorMessage, android.widget.Toast.LENGTH_LONG).show()
            playlistViewModel.clearArtworkUpdateError()
        }
    }

    LaunchedEffect(playlistId) {
        playlistViewModel.loadPlaylistDetails(playlistId)
    }

    var showAddSongsSheet by remember { mutableStateOf(false) }

    var isReorderModeEnabled by remember { mutableStateOf(false) }
    var isRemoveModeEnabled by remember { mutableStateOf(false) }
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showPlaylistOptionsSheet by remember { mutableStateOf(false) }
    var showEditPlaylistDialog by remember { mutableStateOf(false) }
    var showArtworkOptions by remember { mutableStateOf(false) }
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { playlistViewModel.updatePlaylistArtwork(playlistId, it) }
    }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val m3uExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        uri?.let {
            currentPlaylist?.let { playlist ->
                playlistViewModel.exportM3u(playlist, it, context)
            }
        }
    }

    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle() // Reintroducir favoriteIds aquí
    val stableOnMoreOptionsClick: (Song) -> Unit = remember {
        { song ->
            playerViewModel.selectSongForInfo(song)
            showSongInfoBottomSheet = true
        }
    }
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    var localReorderableSongs by remember(songsInPlaylist) { mutableStateOf(songsInPlaylist) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
    var lastMovedTo by remember { mutableStateOf<Int?>(null) }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            // The first lazy item is the artwork/actions header, not a song.
            val fromSong = from.index - 1
            val toSong = to.index - 1
            if (fromSong in localReorderableSongs.indices && toSong in localReorderableSongs.indices) {
                localReorderableSongs = localReorderableSongs.toMutableList().apply {
                    add(toSong, removeAt(fromSong))
                }
                if (lastMovedFrom == null) lastMovedFrom = fromSong
                lastMovedTo = toSong
            }
        }
    )

    LaunchedEffect(reorderableState.isAnyItemDragging, isFolderPlaylist) {
        if (!isFolderPlaylist && !reorderableState.isAnyItemDragging && lastMovedFrom != null && lastMovedTo != null) {
            currentPlaylist?.let {
                playlistViewModel.reorderSongsInPlaylist(it.id, lastMovedFrom!!, lastMovedTo!!)
            }
            lastMovedFrom = null
            lastMovedTo = null
        } else if (isFolderPlaylist && !reorderableState.isAnyItemDragging) {
            lastMovedFrom = null
            lastMovedTo = null
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val collapsedHeaderHeight = 64.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minHeaderPx = with(density) { collapsedHeaderHeight.toPx() }
    val maxHeaderPx = with(density) { 300.dp.toPx() }.coerceAtLeast(minHeaderPx)
    var headerHeightPx by remember(minHeaderPx, maxHeaderPx) { mutableFloatStateOf(maxHeaderPx) }
    val headerCollapse = if (maxHeaderPx > minHeaderPx)
        1f - ((headerHeightPx - minHeaderPx) / (maxHeaderPx - minHeaderPx)).coerceIn(0f, 1f) else 1f
    val headerScrollConnection = remember(listState, minHeaderPx, maxHeaderPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (available.y > 0f && !atTop) return Offset.Zero
                val oldHeight = headerHeightPx
                headerHeightPx = (oldHeight + available.y).coerceIn(minHeaderPx, maxHeaderPx)
                return Offset(0f, headerHeightPx - oldHeight)
            }
        }
    }

    // Match Artist details: the scrollbar appears only after the artwork collapses.
    val showPlaylistScrollBar = headerCollapse > 0.95f &&
        (listState.canScrollForward || listState.canScrollBackward)

    MaterialTheme(colorScheme = headerScheme) {

    Scaffold(
        modifier = Modifier
            .nestedScroll(headerScrollConnection),
        topBar = {
            PlaylistArtworkHeader(
                name = currentPlaylist?.name ?: fallbackPlaylistName,
                subtitle = currentPlaylist?.let {
                    stringResource(R.string.presentation_batch_f_status_bullet_step,
                        formatSongCount(it.remoteSongCount ?: songsInPlaylist.size), formatTotalDuration(songsInPlaylist))
                }.orEmpty(),
                artwork = headerArt,
                headerHeight = with(density) { headerHeightPx.toDp() },
                collapseFraction = headerCollapse,
                onBack = onBackClick,
                onSort = { playerViewModel.showSortingSheet() },
                onOptions = if (isFolderPlaylist) null else ({ showPlaylistOptionsSheet = true }),
                onShuffle = {
                    currentPlaylist?.let { playlist ->
                        if (localReorderableSongs.isNotEmpty()) playerViewModel.playSongsShuffled(
                            songsToPlay = localReorderableSongs, queueName = playlist.name,
                            playlistId = playlist.id, startAtZero = true)
                    }
                },
                shuffleEnabled = localReorderableSongs.isNotEmpty(),
                sortLabel = sortSongsLabel,
                optionsLabel = moreOptionsLabel,
                shuffleLabel = shuffleLabel,
                onImageState = { state ->
                    if (state is AsyncImagePainter.State.Success) headerArt?.let {
                        playerViewModel.themeStateHolder.ensureAlbumColorScheme(it)
                    }
                    if (state is AsyncImagePainter.State.Error && explicitCover != null && !explicitCoverFailed)
                        explicitCoverFailed = true
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading && currentPlaylist == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()), Alignment.Center
            ) { com.aro.music.presentation.components.SongRowsSkeleton() }
        } else if (uiState.playlistNotFound) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()), Alignment.Center
            ) { Text(stringResource(id = R.string.playlist_not_found)) }
        } else if (currentPlaylist == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()), Alignment.Center
            ) { com.aro.music.presentation.components.SongRowsSkeleton() }
        } else {
            Box(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        end = if (showPlaylistScrollBar) 24.dp else 0.dp,
                        bottom = bottomBarHeightDp + (if (playerStableState.currentSong != null) MiniPlayerHeight else 0.dp) + 16.dp
                    )
                ) {
                    item(key = "playlist_artwork_header", contentType = "playlist_header") {
                        Column {

                            val actionButtonsHeight = 42.dp
                            val playbackControlBottomPadding = if (isFolderPlaylist) 8.dp else 6.dp
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(62.dp)
                                    .padding(horizontal = 20.dp)
                                    .padding(bottom = playbackControlBottomPadding),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (localReorderableSongs.isNotEmpty()) {
                                            playerViewModel.playSongs(
                                                localReorderableSongs,
                                                localReorderableSongs.first(),
                                                currentPlaylist.name
                                            )
                                            if (playerStableState.isShuffleEnabled) playerViewModel.toggleShuffle()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(76.dp),
                                    enabled = localReorderableSongs.isNotEmpty(),
                                    shape = RoundedCornerShape(26.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = stringResource(R.string.cd_play),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text(playItLabel)
                                }

                            }

                            if (!isFolderPlaylist) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, end = 20.dp, bottom = 8.dp, top = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val reorderCornerRadius by animateDpAsState(
                                        targetValue = if (isReorderModeEnabled) 24.dp else 12.dp,
                                        label = "reorderCornerRadius"
                                    )
                                    val reorderButtonColor by animateColorAsState(
                                        targetValue = if (isReorderModeEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        label = "reorderButtonColor"
                                    )
                                    val reorderIconColor by animateColorAsState(
                                        targetValue = if (isReorderModeEnabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface,
                                        label = "reorderIconColor"
                                    )

                                    val removeCornerRadius by animateDpAsState(
                                        targetValue = if (isRemoveModeEnabled) 24.dp else 12.dp,
                                        label = "removeCornerRadius"
                                    )
                                    val removeButtonColor by animateColorAsState(
                                        targetValue = if (isRemoveModeEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        label = "removeButtonColor"
                                    )
                                    val removeIconColor by animateColorAsState(
                                        targetValue = if (isRemoveModeEnabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface,
                                        label = "removeIconColor"
                                    )

                                    Button(
                                        onClick = { showAddSongsSheet = true },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        ),
                                        modifier = Modifier
                                            .weight(0.75f)
                                            .height(actionButtonsHeight)
                                            .animateContentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = addSongsCd,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = addLabel,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }

                                    Button(
                                        onClick = { isRemoveModeEnabled = !isRemoveModeEnabled },
                                        shape = RoundedCornerShape(removeCornerRadius),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = removeButtonColor,
                                            contentColor = removeIconColor
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(actionButtonsHeight)
                                            .animateContentSize()
                                            .clip(RoundedCornerShape(removeCornerRadius))
                                    ) {
                                        Icon(
                                            modifier = Modifier.size(18.dp),
                                            imageVector = Icons.Default.RemoveCircleOutline,
                                            contentDescription = removeSongsCd,
                                            tint = removeIconColor
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            modifier = Modifier.padding(end = 4.dp),
                                            text = removeLabel,
                                            color = removeIconColor,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }

                                    Button(
                                        onClick = { isReorderModeEnabled = !isReorderModeEnabled },
                                        shape = RoundedCornerShape(reorderCornerRadius),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = reorderButtonColor,
                                            contentColor = reorderIconColor
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(actionButtonsHeight)
                                            .animateContentSize()
                                            .clip(RoundedCornerShape(reorderCornerRadius))
                                    ) {
                                        Icon(
                                            modifier = Modifier.size(22.dp),
                                            painter = painterResource(R.drawable.drag_order_icon),
                                            contentDescription = reorderSongsCd,
                                            tint = reorderIconColor
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            modifier = Modifier.padding(end = 4.dp),
                                            text = reorderLabel,
                                            color = reorderIconColor,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (localReorderableSongs.isEmpty()) {
                        item(key = "playlist_empty") {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (uiState.isLoading) com.aro.music.presentation.components.SongRowsSkeleton()
                                else {
                                    Icon(Icons.Filled.MusicOff, null, Modifier.size(48.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text(playlistEmptyTitle, style = MaterialTheme.typography.titleMedium)
                                    Text(if (isFolderPlaylist) playlistEmptyFolder else playlistEmptyAddHint,
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    itemsIndexed(
                        localReorderableSongs,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> "playlist_song" }) { _, song ->
                        ReorderableItem(
                            state = reorderableState,
                            key = song.id,
                        ) { isDragging ->
                            val scale by animateFloatAsState(
                                if (isDragging) 1.05f else 1f,
                                label = "scale"
                            )

                            QueuePlaylistSongItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 0.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    },
                                onClick = {
                                    playerViewModel.playSongs(
                                        localReorderableSongs,
                                        song,
                                        currentPlaylist.name,
                                        currentPlaylist.id
                                    )
                                },
                                song = song,
                                isCurrentSong = playerStableState.currentSong?.id == song.id,
                                isPlaying = playerStableState.isPlaying,
                                isDragging = isDragging,
                                onRemoveClick = {
                                    if (!isFolderPlaylist) {
                                        currentPlaylist.let {
                                            playlistViewModel.removeSongFromPlaylist(it.id, song.id)
                                        }
                                    }
                                },
                                isFromPlaylist = true,
                                isReorderModeEnabled = isReorderModeEnabled,
                                isDragHandleVisible = isReorderModeEnabled,
                                isRemoveButtonVisible = isRemoveModeEnabled,
                                onMoreOptionsClick = stableOnMoreOptionsClick,
                                dragHandle = {
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier
                                            .draggableHandle(
                                                onDragStarted = {
                                                    performAppCompatHapticFeedback(
                                                        view,
                                                        appHapticsConfig,
                                                        HapticFeedbackConstantsCompat.GESTURE_START
                                                    )
                                                },
                                                onDragStopped = {
                                                    performAppCompatHapticFeedback(
                                                        view,
                                                        appHapticsConfig,
                                                        HapticFeedbackConstantsCompat.GESTURE_END
                                                    )
                                                }
                                            )
                                            .size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragIndicator,
                                            contentDescription = reorderSongCd,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        }
                    }
                    if (uiState.isLoading || uiState.isUpdatingArtwork) {
                        item(key = "playlist_loading", contentType = "loading") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp))
                            }
                        }
                    }
                }
                if (showPlaylistScrollBar) {
                    ExpressiveScrollBar(
                        listState = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(
                                top = 12.dp,
                                bottom = bottomBarHeightDp +
                                    (if (playerStableState.currentSong != null) MiniPlayerHeight else 0.dp) + 16.dp
                            )
                    )
                }
            }
        }
    }

    }

    if (showAddSongsSheet && currentPlaylist != null && !isFolderPlaylist) {
        SongPickerBottomSheet(
            initiallySelectedSongIds = currentPlaylist.songIds.toSet(),
            onDismiss = { showAddSongsSheet = false },
            onConfirm = { selectedIds ->
                playlistViewModel.addSongsToPlaylist(currentPlaylist.id, selectedIds.toList())
                showAddSongsSheet = false
            }
        )
    }
    if (showPlaylistOptionsSheet && !isFolderPlaylist) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showPlaylistOptionsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 4.dp,
//            dragHandle = {
//                SheetDefaults.DragHandle(
//                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                )
//            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = playlistOptionsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    currentPlaylist?.name?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                PlaylistActionItem(
                    icon = painterResource(R.drawable.rounded_edit_24),
                    label = editPlaylistLabel,
                    onClick = {
                        showPlaylistOptionsSheet = false
                        showEditPlaylistDialog = true
                    }
                )
                PlaylistActionItem(
                    icon = painterResource(R.drawable.rounded_imagesmode_24),
                    label = stringResource(R.string.playlist_change_artwork),
                    onClick = {
                        showPlaylistOptionsSheet = false
                        showArtworkOptions = true
                    }
                )
                PlaylistActionItem(
                    icon = painterResource(R.drawable.rounded_delete_24),
                    label = deletePlaylistLabel,
                    onClick = {
                        showPlaylistOptionsSheet = false
                        showDeleteConfirmation = true
                    }
                )
                PlaylistActionItem(
                    icon = painterResource(R.drawable.outline_graph_1_24),
                    label = setDefaultTransitionLabel,
                    onClick = {
                        showPlaylistOptionsSheet = false
                        navController.navigateSafely(Screen.EditTransition.createRoute(playlistId))
                    }
                )
                PlaylistActionItem(
                    icon = painterResource(R.drawable.rounded_attach_file_24),
                    label = exportPlaylistLabel,
                    onClick = {
                        showPlaylistOptionsSheet = false
                        m3uExportLauncher.launch("${currentPlaylist?.name ?: fallbackPlaylistName}.m3u")
                    }
                )
            }
        }
    }

    if (showArtworkOptions && currentPlaylist != null && !isFolderPlaylist) {
        AlertDialog(
            onDismissRequest = { showArtworkOptions = false },
            title = { Text(stringResource(R.string.playlist_change_artwork)) },
            text = { Text(stringResource(R.string.playlist_artwork_help)) },
            confirmButton = {
                TextButton(enabled = !uiState.isUpdatingArtwork, onClick = {
                    showArtworkOptions = false
                    artworkPicker.launch("image/*")
                }) { Text(stringResource(R.string.playlist_choose_image)) }
            },
            dismissButton = {
                TextButton(enabled = !uiState.isUpdatingArtwork, onClick = {
                    showArtworkOptions = false
                    playlistViewModel.updatePlaylistArtwork(playlistId, null)
                }) { Text(stringResource(R.string.playlist_use_first_song_art)) }
            }
        )
    }

    if (showEditPlaylistDialog && currentPlaylist != null) {
        val initialShapeType = try {
            currentPlaylist.coverShapeType?.let { PlaylistShapeType.valueOf(it) } ?: PlaylistShapeType.Circle
        } catch (e: Exception) {
            PlaylistShapeType.Circle
        }

        EditPlaylistDialog(
            visible = showEditPlaylistDialog,
            currentName = currentPlaylist.name,
            currentImageUri = currentPlaylist.coverImageUri,
            currentColor = currentPlaylist.coverColorArgb,
            currentIconName = currentPlaylist.coverIconName,
            currentShapeType = initialShapeType,
            currentShapeDetail1 = currentPlaylist.coverShapeDetail1,
            currentShapeDetail2 = currentPlaylist.coverShapeDetail2,
            currentShapeDetail3 = currentPlaylist.coverShapeDetail3,
            currentShapeDetail4 = currentPlaylist.coverShapeDetail4,
            onDismiss = { showEditPlaylistDialog = false },
            onSave = { name, imageUri, color, icon, scale, panX, panY, shapeType, d1, d2, d3, d4 ->
                playlistViewModel.updatePlaylistParameters(
                    playlistId = currentPlaylist.id,
                    name = name,
                    coverImageUri = imageUri,
                    coverColor = color,
                    coverIcon = icon,
                    cropScale = scale,
                    cropPanX = panX,
                    cropPanY = panY,
                    coverShapeType = shapeType,
                    coverShapeDetail1 = d1,
                    coverShapeDetail2 = d2,
                    coverShapeDetail3 = d3,
                    coverShapeDetail4 = d4
                )
                showEditPlaylistDialog = false
            }
        )
    }
    if (showDeleteConfirmation && currentPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(deletePlaylistConfirmTitle) },
            text = {
                Text(deletePlaylistConfirmBody)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistViewModel.deletePlaylist(currentPlaylist.id)
                        onDeletePlayListClick()
                        showDeleteConfirmation = false
                    }
                ) {
                    Text(stringResource(R.string.delete_action), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteIds) {
            derivedStateOf {
                currentSong?.let {
                    favoriteIds.contains(
                        it.id
                    )
                }
            }
        }.value ?: false

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                isFavorite = isFavorite,
                onToggleFavorite = {
                    // Directly use PlayerViewModel's method to toggle, which should handle UserPreferencesRepository
                    playerViewModel.toggleFavoriteSpecificSong(currentSong) // Assumes such a method exists or will be added to PlayerViewModel
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong) // Assumes such a method exists or will be added
                    showSongInfoBottomSheet = false
                    playerViewModel.sendToast(toastAddedToQueue)
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                    showSongInfoBottomSheet = false
                    playerViewModel.sendToast(toastPlayingNext)
                },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true;
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafelyReplacing(
                        route = Screen.AlbumDetail.createRoute(currentSong.albumId),
                        patternToPop = Screen.AlbumDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(currentSong.artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtistById = { artistId ->
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {
                    currentSong.genre?.let {
                        navController.navigateSafelyReplacing(
                            route = Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")),
                            patternToPop = Screen.GenreDetail.route
                        )
                    }
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        currentSong,
                        newTitle,
                        newArtist,
                        newAlbum,
                        newGenre,
                        newLyrics,
                        newTrackNumber,
                        newDiscNumber,
                        replayGainTrackGainDb,
                        replayGainAlbumGainDb,
                        coverArtUpdate
                    )
                },
                removeFromListTrigger = {
                    playlistViewModel.removeSongFromPlaylist(playlistId, currentSong.id)
                }
            )
            if (showPlaylistBottomSheet) {
                val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    songs = listOf(currentSong),
                    onDismiss = {
                        showPlaylistBottomSheet = false
                    },
                    currentPlaylistId = playlistId,
                    bottomBarHeight = bottomBarHeightDp,
                    playerViewModel = playerViewModel,
                )
            }
        }
    }

    val isSortSheetVisible by playerViewModel.isSortingSheetVisible.collectAsStateWithLifecycle()

    if (isSortSheetVisible) {
        // Check if playlist is in Manual mode (which corresponds to Default Order)
        val isManualMode = uiState.playlistSongsOrderMode is PlaylistSongsOrderMode.Manual
        val rawOption = uiState.currentPlaylistSongsSortOption
        // If in Manual mode, show SongDefaultOrder as selected; otherwise use the stored sort option
        val currentSortOption = if (isManualMode) {
            SortOption.SongDefaultOrder
        } else if (currentPlaylist != null) {
            rawOption
        } else {
            SortOption.SongTitleAZ
        }

        // Build options list inline to avoid potential static initialization issues
        val songSortOptions = listOf(
            SortOption.SongDefaultOrder,
            SortOption.SongTitleAZ,
            SortOption.SongTitleZA,
            SortOption.SongArtist,
            SortOption.SongArtistDesc,
            SortOption.SongAlbum,
            SortOption.SongAlbumDesc,
            SortOption.SongDateAdded,
            SortOption.SongDateAddedAsc,
            SortOption.SongDuration,
            SortOption.SongDurationAsc
        )

        LibrarySortBottomSheet(
            title = sortSheetTitle,
            options = songSortOptions,
            selectedOption = currentSortOption,
            onDismiss = { playerViewModel.hideSortingSheet() },
            onOptionSelected = { option ->
                 playlistViewModel.sortPlaylistSongs(option)
                 playerViewModel.hideSortingSheet()
                 // Auto-scroll to first item after sorting (delay to allow list to update)
                 scope.launch {
                     kotlinx.coroutines.delay(100)
                     listState.animateScrollToItem(0)
                 }
            },
            onDirectionToggle = { option ->
                playlistViewModel.sortPlaylistSongs(option)
                scope.launch {
                    kotlinx.coroutines.delay(100)
                    listState.animateScrollToItem(0)
                }
            },
            showViewToggle = false
        )
    }
}


@Composable
private fun PlaylistActionItem(
    icon: Painter,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// SongPickerBottomSheet moved to com.aro.music.presentation.components
fun RenamePlaylistDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var newName by remember { mutableStateOf(TextFieldValue(currentName)) }
    val renameTitle = stringResource(R.string.presentation_batch_b_rename_playlist_dialog_title)
    val newNameLabel = stringResource(R.string.presentation_batch_b_new_name)
    val renameAction = stringResource(R.string.action_rename)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(renameTitle) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(newNameLabel) },
                shape = CircleShape,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (newName.text.isNotBlank()) onRename(newName.text) },
                enabled = newName.text.isNotBlank() && newName.text != currentName
            ) { Text(renameAction, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistArtworkHeader(
    name: String,
    subtitle: String,
    artwork: String?,
    headerHeight: Dp,
    collapseFraction: Float,
    onBack: () -> Unit,
    onSort: () -> Unit,
    onOptions: (() -> Unit)?,
    onShuffle: () -> Unit,
    shuffleEnabled: Boolean,
    sortLabel: String,
    optionsLabel: String,
    shuffleLabel: String,
    onImageState: (AsyncImagePainter.State) -> Unit
) {
    val surface = MaterialTheme.colorScheme.surface
    val artworkAlpha = 1f - (collapseFraction * 2f).coerceIn(0f, 1f)
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
    val statusTint = if (LocalAroDarkTheme.current) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f)
    com.aro.music.ui.theme.AroStatusBarStyle(color = statusTint.compositeOver(surface))
    Box(Modifier.fillMaxWidth().height(headerHeight).clipToBounds()) {
        if (artworkAlpha > 0.01f) {
            SmartImage(model = artwork, contentDescription = stringResource(R.string.playlist_cover_description, name),
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                targetSize = Size(960, 640), alpha = artworkAlpha, onState = onImageState)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color.Transparent, surface.copy(alpha = 0.30f * artworkAlpha),
                surface.copy(alpha = 0.90f * artworkAlpha), surface
            ))))
        }
        Box(Modifier.fillMaxWidth().height(80.dp).background(
            Brush.verticalGradient(listOf(statusTint, Color.Transparent))))
        CollapsibleCommonTopBar(
            title = name, subtitle = subtitle, collapseFraction = collapseFraction,
            headerHeight = headerHeight, onBackClick = onBack,
            containerColor = surface.copy(alpha = solidAlpha),
            collapsedTitleStartPadding = 68.dp, expandedTitleStartPadding = 24.dp,
            collapsedTitleEndPadding = 124.dp, expandedTitleEndPadding = 136.dp,
            containerHeightRange = 112.dp to 56.dp,
            titleStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold),
            titleScaleRange = 1f to 1f, titleFontSizeRange = 30.sp to 18.sp,
            maxLines = if (collapseFraction < 0.5f) 2 else 1,
            collapsedSubtitleMaxLines = 1, expandedSubtitleMaxLines = 2,
            contentColor = MaterialTheme.colorScheme.onSurface,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            fadeSubtitleOnCollapse = false, syncStatusBarWithContainer = false,
            actions = {
                FilledTonalIconButton(onClick = onSort) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, sortLabel)
                }
                if (onOptions != null) FilledTonalIconButton(onClick = onOptions, modifier = Modifier.padding(end = 12.dp)) {
                    Icon(Icons.Filled.MoreVert, optionsLabel)
                }
            }
        )
        if (shuffleEnabled && artworkAlpha > 0.01f) {
            LargeExtendedFloatingActionButton(
                onClick = onShuffle,
                shape = RoundedStarShape(sides = 8, curve = 0.05, rotation = 0f),
                modifier = Modifier.align(BiasAlignment(1f, 1f - 2f * collapseFraction))
                    .statusBarsPadding().padding(end = 16.dp)
                    .graphicsLayer { scaleX = artworkAlpha; scaleY = artworkAlpha; alpha = artworkAlpha }
            ) { Icon(Icons.Rounded.Shuffle, shuffleLabel) }
        }
    }
}
