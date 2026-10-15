package com.aro.music.data.update

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Manual public-network probe; intentionally excluded from account-independent CI. */
@RunWith(AndroidJUnit4::class)
class GitHubRangeDownloadDeviceTest {
    @Test fun publishedGitHubApkDownloadsAndPassesWorkerSignatureVerification() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sha = "1e520bffc2143e9d71fcc8217c8c518e67b22cef273d83008d7fd460e630b238"
        val directory = File(context.cacheDir, "apk-updates/$sha")
        val preferences = context.getSharedPreferences("apk-download", Context.MODE_PRIVATE)
        val manager = WorkManager.getInstance(context)
        val resumedBytes = directory.listFiles()?.filter { it.name.startsWith("part-") }?.sumOf { it.length() } ?: 0L
        val start = SystemClock.elapsedRealtime()
        try {
            val release = AppRelease("v1.0.1", AppVersion.parse("1.0.1")!!,
                "https://github.com/sahilcodexx/aro-music/releases/download/v1.0.1/Aro-1.0.1.apk", "", sha)
            val id = ApkDownloadWorker.enqueue(context, release)
            val result = withTimeout(600_000) { manager.getWorkInfoByIdFlow(id).first { it?.state?.isFinished == true }!! }
            assertEquals(WorkInfo.State.SUCCEEDED, result.state)
            assertEquals(sha, result.outputData.getString("sha"))
            assertEquals(142182179L, File(directory, "update.apk").length())
            Log.i("AroDownloadProbe", "Verified 142182179-byte APK; resumed $resumedBytes bytes; elapsed ${SystemClock.elapsedRealtime() - start} ms")
            Unit
        } finally {
            manager.cancelUniqueWork("apk-update").result.get()
            preferences.edit().clear().commit()
            directory.deleteRecursively()
        }
    }
}
