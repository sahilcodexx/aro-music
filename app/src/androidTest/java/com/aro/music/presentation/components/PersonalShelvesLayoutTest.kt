package com.aro.music.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aro.music.data.innertube.HomeCatalogItem
import com.aro.music.data.innertube.HomeCatalogSection
import com.aro.music.data.model.Song
import com.aro.music.data.model.Playlist
import com.aro.music.data.playlist.PlaylistImportRepository
import com.aro.music.presentation.viewmodel.PlaylistImportState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalShelvesLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadingShelfPreservesTheFollowingSectionsPosition() {
        val ready = mutableStateOf(false)
        val section = HomeCatalogSection("Speed dial", "Quick picks", listOf(HomeCatalogItem.Track(
            Song.emptySong().copy(title = "A song with a title that takes two lines", artist = "Artist"))))
        compose.setContent {
            MaterialTheme {
                Column {
                    if (ready.value) HomeCatalogShelf(section) {}
                    else HomeSongShelfPlaceholder("Speed dial", "Quick picks", loading = true, emptyMessage = "")
                    Text("Daily Mix", Modifier.testTag("following_shelf"))
                }
            }
        }
        val before = compose.onNodeWithTag("following_shelf").fetchSemanticsNode().positionInRoot.y
        compose.runOnIdle { ready.value = true }
        val after = compose.onNodeWithTag("following_shelf").fetchSemanticsNode().positionInRoot.y
        assertEquals("Daily Mix must stay in the same position", before, after, 1f)
    }

    @Test fun importDialogAcceptsLinksAndOpensTheSavedCopy() {
        val state = mutableStateOf(PlaylistImportState())
        var submitted: String? = null
        var opened: String? = null
        compose.setContent {
            MaterialTheme {
                PlaylistImportDialog(true, state.value, onImport = { submitted = it }, onDismiss = {},
                    onOpenPlaylist = { opened = it }, onConnectSpotify = {})
            }
        }
        compose.onNodeWithText("Import").assertIsNotEnabled()
        val link = "https://www.youtube.com/playlist?list=PLabcdefghijklmnopqrstuv"
        compose.onNodeWithTag("playlist_import_link").performTextInput(link)
        compose.onNodeWithText("Import").performScrollTo().performClick()
        assertEquals(link, submitted)
        compose.runOnIdle { state.value = PlaylistImportState(result = PlaylistImportRepository.Imported(Playlist("import:youtube:test", "Imported mix", listOf("1", "2")))) }
        compose.onNodeWithText("Open playlist").performClick()
        assertEquals("import:youtube:test", opened)
    }
}
