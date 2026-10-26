package com.aro.music.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aro.music.data.preferences.HomeLayout
import com.aro.music.data.preferences.HomeSection
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditHomeSheetDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun hiddenSectionIsOnlyAppliedWhenSaved() {
        var saved: HomeLayout? = null
        compose.setContent { MaterialTheme { EditHomeSheet(HomeLayout(), {}, { saved = it }) } }
        compose.onNodeWithContentDescription("Discover").performScrollTo().performClick()
        assertNull(saved)
        compose.onNodeWithText("Save layout").performClick()
        assertEquals(setOf(HomeSection.DISCOVER), saved?.hidden)
    }
    @Test fun resetRestoresOrderAndVisibility() {
        var saved: HomeLayout? = null
        compose.setContent { MaterialTheme { EditHomeSheet(HomeLayout(HomeSection.entries.reversed(), setOf(HomeSection.DISCOVER)), {}, { saved = it }) } }
        compose.onNodeWithText("Reset layout").performClick()
        compose.onNodeWithText("Save layout").performClick()
        assertEquals(HomeLayout(), saved)
    }
}
