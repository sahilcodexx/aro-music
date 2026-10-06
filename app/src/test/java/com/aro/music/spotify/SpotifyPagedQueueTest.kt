package com.aro.music.spotify

import androidx.media3.common.MediaItem
import com.aro.music.data.queue.SpotifyPagedQueue
import com.aro.music.spotify.models.SpotifyTrack
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpotifyPagedQueueTest {
    private class Queue(mapper: SpotifyYouTubeMapper, private val pages: List<List<SpotifyTrack>>) : SpotifyPagedQueue(0, mapper, null) {
        override val logTag = "Test"
        override val providedTracks: List<SpotifyTrack>? = null
        val offsets = mutableListOf<Int>()
        override suspend fun fetchPage(offset: Int, limit: Int): PageResult {
            offsets.add(offset)
            val tracks = pages.getOrNull(offsets.size - 1).orEmpty()
            return PageResult(tracks, 100, tracks.size)
        }
    }

    @Test fun `empty API page stops pagination even if total reports more`() = runTest {
        val queue = Queue(mockk(), listOf(emptyList()))
        assertTrue(queue.getInitialStatus().items.isEmpty())
        assertFalse(queue.hasNextPage())
        assertTrue(queue.nextPage().isEmpty())
        assertEquals(listOf(0), queue.offsets)
    }

    @Test fun `failed selected track never starts a neighboring song`() = runTest {
        val mapper = mockk<SpotifyYouTubeMapper>()
        coEvery { mapper.resolveToMediaItem(match { it.id == "selected" }) } returns null
        coEvery { mapper.resolveToMediaItem(match { it.id == "neighbor" }) } returns mockk<MediaItem>()
        val queue = Queue(mapper, listOf(listOf(SpotifyTrack(id = "selected"), SpotifyTrack(id = "neighbor"))))
        assertTrue(queue.getInitialStatus().items.isEmpty())
        assertFalse(queue.hasNextPage())
    }
    @Test fun `unmatchable batch does not hide playable later tracks`() = runTest {
        val mapper = mockk<SpotifyYouTubeMapper>()
        val seed = mockk<MediaItem>()
        val later = mockk<MediaItem>()
        coEvery { mapper.resolveToMediaItem(any()) } returns null
        coEvery { mapper.resolveToMediaItem(match { it.id == "0" }) } returns seed
        coEvery { mapper.resolveToMediaItem(match { it.id == "23" }) } returns later
        val queue = Queue(mapper, listOf((0..23).map { SpotifyTrack(id = it.toString()) }))
        assertEquals(listOf(seed), queue.getInitialStatus().items)
        assertEquals(listOf(later), queue.nextPage())
        assertTrue(queue.nextPage().isEmpty())
        assertFalse(queue.hasNextPage())
    }

}
