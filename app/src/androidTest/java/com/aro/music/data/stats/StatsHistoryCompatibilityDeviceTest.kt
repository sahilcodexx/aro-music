package com.aro.music.data.stats

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsHistoryCompatibilityDeviceTest {
    @Test fun oldReleaseHistoryAndStreamingSnapshotsSurviveRecreation() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "stats-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getFilesDir(): File = directory }
        try {
            val now = System.currentTimeMillis()
            File(directory, "playback_history.json").writeText("""[{"a":"old-stream","b":${now - 10000},"c":5000,"d":${now - 15000},"e":${now - 10000}}]""")
            val repository = PlaybackStatsRepository(context)
            assertEquals("old-stream", repository.exportEventsForBackup().single().songId)
            repository.rememberTrack("new-stream", "Test track", "Test artist", "Test album", null, "Pop")
            repository.recordPlayback("new-stream", 5000, now)
            val recreated = PlaybackStatsRepository(context)
            val rows = recreated.exportEventsForBackup()
            assertEquals(2, rows.size)
            assertEquals(5000, rows.first().durationMs)
            assertEquals("Test track", rows.last().metadata!!.title)
            val summary = recreated.loadSummary(StatsTimeRange.ALL, emptyList(), now)
            assertEquals("Test track", summary.topSongs.single().title)
            assertEquals("Test artist", summary.topArtists.first { it.artist == "Test artist" }.artist)
            assertEquals(10000, summary.totalDurationMs)
        } finally { directory.deleteRecursively() }
    }
}
