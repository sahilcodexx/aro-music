package com.aro.music.presentation.viewmodel

import com.aro.music.data.model.Song
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HomePersonalizationTest {
    private fun song(id: String) = Song.emptySong().copy(id = id)
    @Test fun `automatic dial avoids recently played even when favorites and quick picks overlap`() {
        val result = homeSpeedDial(emptyList(), setOf("recent"), listOf(song("recent"), song("favorite")),
            listOf(song("frequent"), song("favorite")), listOf(song("recent"), song("discover")))
        assertEquals(listOf("favorite", "frequent", "discover"), result.map { it.id })
    }
    @Test fun `explicit pins can overlap history and have priority`() {
        val result = homeSpeedDial(listOf(song("recent")), setOf("recent"), listOf(song("favorite")), emptyList(), (1..20).map { song("$it") })
        assertEquals("recent", result.first().id)
        assertEquals(12, result.size)
        assertEquals(12, result.map { it.id }.distinct().size)
    }
}
