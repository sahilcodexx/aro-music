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
        fun line(index: Int): SemanticsNodeInteraction {
            compose.onNodeWithTag("lyric_card_lines").performScrollToIndex(index)
            return compose.onNodeWithTag("lyric_line_$index").assertIsDisplayed()
        }
        (5 downTo 0).forEach { line(it).performClick().assertIsOn() }
        line(6).assertIsNotEnabled()
        line(0).performClick().assertIsOff()
        line(6).assertIsEnabled().performClick().assertIsOn()
        compose.onNodeWithTag("lyric_card_preview").performClick()
        compose.runOnIdle { assertEquals(lines.drop(1), previewed) }
    }
}
