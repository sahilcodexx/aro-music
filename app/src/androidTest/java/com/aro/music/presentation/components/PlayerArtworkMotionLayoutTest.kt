package com.aro.music.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.size.Size
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerArtworkMotionLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fullCoverNeverIncreasesCollapsedPlayerHeight() {
        compose.setContent {
            MaterialTheme {
                val motion = remember { PlayerArtworkMotion { 0f }.apply { full = Rect(0f, 0f, 400f, 400f) } }
                Box(Modifier.width(300.dp).testTag("mini_container")) {
                    Box(Modifier.fillMaxWidth().height(64.dp))
                    PlayerArtworkMotionOverlay(motion, null, Size(400, 400))
                }
            }
        }
        compose.onNodeWithTag("mini_container").assertHeightIsEqualTo(64.dp)
    }
}
