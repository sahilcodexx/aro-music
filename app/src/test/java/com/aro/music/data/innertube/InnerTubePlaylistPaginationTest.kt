package com.aro.music.data.innertube

import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InnerTubePlaylistPaginationTest {
    private fun page(ids: List<String>, token: String? = null) = buildJsonObject {
        putJsonArray("contents") { ids.forEach { id -> add(buildJsonObject {
            putJsonObject("musicTwoRowItemRenderer") {
                putJsonObject("title") { putJsonArray("runs") { add(buildJsonObject { put("text", id) }) } }
                putJsonObject("navigationEndpoint") { putJsonObject("watchEndpoint") { put("videoId", id) } }
            }
        }) } }
        if (token != null) putJsonArray("continuations") { add(buildJsonObject { putJsonObject("nextContinuationData") { put("continuation", token) } }) }
    }
    @Test fun `playlist imports follow every page preserve order and deduplicate boundary songs`() = runBlocking {
        val engine = mockk<InnerTubeEngine>()
        coEvery { engine.browse("VLPLtest", null, null) } returns page(listOf("first", "second"), "next")
        coEvery { engine.browse("VLPLtest", null, "next") } returns page(listOf("second", "third"))
        val progress = mutableListOf<Int>()
        val result = InnerTubeRepository(engine, mockk(relaxed = true)).getRemotePlaylist("VLPLtest", Int.MAX_VALUE) { progress += it }
        assertEquals(listOf("first", "second", "third"), result!!.songs.map { it.title })
        assertEquals(listOf(2, 3), progress)
        coVerify(exactly = 1) { engine.browse("VLPLtest", null, "next") }
    }
    @Test fun `failed continuation does not return an incomplete saved copy`() = runBlocking {
        val engine = mockk<InnerTubeEngine>()
        coEvery { engine.browse("VLPLtest", null, null) } returns page(listOf("first"), "next")
        coEvery { engine.browse("VLPLtest", null, "next") } returns null
        val repository = InnerTubeRepository(engine, mockk(relaxed = true))
        assertThrows(java.io.IOException::class.java) { runBlocking { repository.getRemotePlaylist("VLPLtest", Int.MAX_VALUE) } }
        Unit
    }
}
