package com.aro.music.presentation.sharing

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyricCardSelectionDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectionEnforcesLimitsAllowsDeselectAndKeepsSongOrder() {
        val lines = List(7) { "Line ${it + 1}" }
        var previewed: List<String>? = null
        compose.setContent {
            var selected by remember { mutableStateOf(emptySet<Int>()) }
            MaterialTheme {
                LyricCardSelectionContent(lines, selected,
                    onToggle = { selected = if (it in selected) selected - it else selected + it },
                    onPreview = { previewed = selectedLyricLines(lines, selected) }, onDismiss = {})
            }
        }
        compose.onNodeWithTag("lyric_card_preview").assertIsNotEnabled()
        (5 downTo 0).forEach { compose.onNodeWithTag("lyric_line_$it").performScrollTo().performClick() }
        compose.onNodeWithTag("lyric_line_6").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("lyric_card_preview").performClick()
        compose.runOnIdle { assertEquals(lines.take(6), previewed) }
        compose.onNodeWithTag("lyric_line_0").performScrollTo().performClick()
        compose.onNodeWithTag("lyric_line_6").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("lyric_card_preview").performClick()
        compose.runOnIdle { assertEquals(lines.drop(1), previewed) }
    }
}
