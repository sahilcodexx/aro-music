package com.aro.music.presentation.sharing

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aro.music.data.model.Song
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SongShareSheetLayoutDeviceTest {
    @get:Rule val compose = createComposeRule()

    private fun preview(size: DpSize) {
        val bitmap = SongShareCard.render(Song.emptySong().copy(title = "A long title that needs two lines", artist = "Test artist"), null,
            SongShareCard.Colors(Color.BLUE, Color.WHITE, Color.CYAN, Color.BLACK))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
                val configuration = Configuration(LocalConfiguration.current).apply {
                    screenWidthDp = size.width.value.toInt()
                    screenHeightDp = size.height.value.toInt()
                }
                CompositionLocalProvider(LocalConfiguration provides configuration) {
                    MaterialTheme {
                        SongShareSheetContent(bitmap, false, false, SongShareCard.Style.ARTWORK, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Share what’s playing").assertIsDisplayed()
        compose.onNodeWithTag("song_share_preview").assertIsDisplayed()
        compose.onNodeWithTag("song_share_action").assertIsDisplayed()
    }

    @Test fun narrowPortraitKeepsPreviewAboveShareControls() {
        preview(DpSize(320.dp, 640.dp))
        val image = compose.onNodeWithTag("song_share_preview").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("song_share_action").fetchSemanticsNode().boundsInRoot
        assertTrue("Preview $image must stay above Share image $action", image.bottom <= action.top)
    }

    @Test fun landscapeKeepsPreviewBesideShareControls() {
        preview(DpSize(640.dp, 360.dp))
        val image = compose.onNodeWithTag("song_share_preview").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("song_share_action").fetchSemanticsNode().boundsInRoot
        assertTrue("Preview $image must sit beside landscape action $action", image.right <= action.left)
    }
}
