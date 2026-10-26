package com.aro.music.presentation.components

import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class PlayerArtworkMotionTest {
    @Test fun `motion falls back until image and endpoints exist and stops at rest`() {
        var fraction = 0.5f
        val motion = PlayerArtworkMotion { fraction }
        assertFalse(motion.active())
        motion.mini = Rect(0f, 0f, 44f, 44f)
        motion.full = Rect(0f, 0f, 300f, 300f)
        motion.origin = mockk { every { isAttached } returns true }
        assertFalse(motion.active())
        motion.imageReady = true
        assertTrue(motion.active())
        fraction = 1f
        assertFalse(motion.active())
        fraction = 0f
        assertFalse(motion.active())
    }

    @Test fun `artwork uses the measured endpoints and reverses along the same path`() {
        val mini = Rect(10f, 700f, 54f, 744f)
        val cover = Rect(30f, 100f, 330f, 400f)
        assertEquals(mini, artworkMotionRect(mini, cover, -1f))
        assertEquals(cover, artworkMotionRect(mini, cover, 2f))
        assertEquals(Rect(20f, 400f, 192f, 572f), artworkMotionRect(mini, cover, 0.5f))
        assertEquals(artworkMotionRect(mini, cover, 0.25f), artworkMotionRect(cover, mini, 0.75f))
    }
}
