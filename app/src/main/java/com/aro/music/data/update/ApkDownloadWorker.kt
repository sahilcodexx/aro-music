package com.aro.music.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.aro.music.MainActivity
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*

class ApkDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val url = inputData.getString("url") ?: return Result.failure()
        val sha = inputData.getString("sha") ?: return Result.failure()
        if (!AppReleaseParser.trustedApkUrl(url) || !sha.matches(Regex("[0-9a-f]{64}"))) return Result.failure()
        return try {
            setForeground(foreground())
            val lastProgress = AtomicLong(0)
            val file = RangeApkDownload().download(url, sha, File(applicationContext.cacheDir, "apk-updates/$sha")) { done, total ->
                val now = System.currentTimeMillis()
                val previous = lastProgress.get()
                if (now - previous > 500 && lastProgress.compareAndSet(previous, now)) {
                    setProgressAsync(workDataOf("done" to done, "total" to total))
                }
            }
            withContext(Dispatchers.IO) {
                val pm = applicationContext.packageManager
                val info = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                    ?: error("Invalid APK")
                check(info.packageName == "com.aro.music")
                val installed = pm.getPackageInfo("com.aro.music", PackageManager.GET_SIGNING_CERTIFICATES)
                fun signatures(signing: android.content.pm.SigningInfo?): Set<String> = signing?.apkContentsSigners?.map {
                    MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
                }?.toSet().orEmpty()
                val expected = signatures(installed.signingInfo)
                check(expected.isNotEmpty() && expected == signatures(info.signingInfo))
            }
            Result.success(workDataOf("sha" to sha))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IOException) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
        catch (_: Exception) { Result.failure() }
    }

    private fun foreground(): ForegroundInfo {
        val context = applicationContext
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("app-updates", context.getString(com.aro.music.R.string.update_settings_title), NotificationManager.IMPORTANCE_LOW))
        val launch = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, "app-updates")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(com.aro.music.R.string.update_downloading))
            .setContentText(context.getString(com.aro.music.R.string.update_download_background))
            .setContentIntent(launch).setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(com.aro.music.R.string.update_download_cancel), WorkManager.getInstance(context).createCancelPendingIntent(id))
            .build()
        return ForegroundInfo(10101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        suspend fun enqueue(context: Context, release: AppRelease): UUID = withContext(Dispatchers.IO) {
            val manager = WorkManager.getInstance(context)
            val active = manager.getWorkInfosForUniqueWork("apk-update").get().firstOrNull { !it.state.isFinished }
            if (active != null) return@withContext active.id
            val request = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
                .setInputData(workDataOf("url" to release.apkUrl, "sha" to checkNotNull(release.sha256)))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            manager.enqueueUniqueWork("apk-update", ExistingWorkPolicy.KEEP, request).result.get()
            context.getSharedPreferences("apk-download", Context.MODE_PRIVATE).edit().putString("work", request.id.toString()).apply()
            request.id
        }
    }
}
