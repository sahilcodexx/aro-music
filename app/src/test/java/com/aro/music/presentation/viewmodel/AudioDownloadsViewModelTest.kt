package com.aro.music.presentation.viewmodel

import android.content.Context
import com.aro.music.R
import com.aro.music.data.download.AudioDownloads
import com.aro.music.data.model.Song
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AudioDownloadsViewModelTest {
    @Test fun `collection download deduplicates skips local and saved tracks and continues after failure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val downloads = mockk<AudioDownloads> {
                every { songs } returns flowOf(emptyList())
                every { records } returns flowOf(emptyMap())
                every { work } returns flowOf(emptyList())
            }
            val context = mockk<Context>(relaxed = true)
            val collections = mockk<com.aro.music.data.download.DownloadedCollections>(relaxed = true) {
                every { this@mockk.collections } returns flowOf(emptyList())
            }
            val vm = AudioDownloadsViewModel(downloads, mockk(), context, this, collections)
            fun song(id: String) = Song.emptySong().copy(id = id, contentUriString = "innertube://$id")
            val first = song("first")
            coEvery { downloads.enqueue(first) } returns true
            coEvery { downloads.enqueue(match { it.id == "saved" }) } returns false
            coEvery { downloads.enqueue(match { it.id == "broken" }) } throws java.io.IOException()
            coEvery { downloads.enqueue(match { it.id == "last" }) } returns true
            val collection = com.aro.music.data.download.DownloadCollection("album:test", "Test", com.aro.music.data.download.DownloadCollectionKind.ALBUM, listOf("first", "saved", "broken", "last", "local"))
            vm.downloadAll(listOf(first, first, song("saved"), song("broken"), song("last"), song("local").copy(contentUriString = "content://media/1")), collection)
            assertTrue(vm.queueing.value)
            vm.downloadAll(listOf(first)) // Repeated taps cannot queue the batch twice.
            androidx.lifecycle.ViewModelStore().apply { put("downloads", vm); clear() }
            advanceUntilIdle()
            assertFalse(vm.queueing.value)
            coVerify(exactly = 1) { collections.save(collection); downloads.enqueue(first) }
            coVerify(exactly = 0) { downloads.enqueue(match { it.id == "local" }) }
            verify { context.getString(R.string.audio_download_batch_result, 2, 2, 1) }
        } finally { Dispatchers.resetMain() }
    }
}
