package com.aro.music.presentation.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.aro.music.R
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.flowOf

@Composable
fun ApkDownloadPrompt(workId: UUID?, onRetry: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { WorkManager.getInstance(context) }
    val preferences = remember { context.getSharedPreferences("apk-download", Context.MODE_PRIVATE) }
    var dismissed by remember { mutableStateOf(preferences.getString("dismissed", "")) }
    val work by remember(workId) { workId?.let { manager.getWorkInfoByIdFlow(it) } ?: flowOf(null) }.collectAsStateWithLifecycle(null)
    val current = work ?: return
    if (current.state == WorkInfo.State.CANCELLED) return
    val token = "${current.id}:${if (current.state.isFinished) current.state.name else "active"}"
    if (dismissed == token) return
    val dismiss = { dismissed = token; preferences.edit().putString("dismissed", token).apply(); Unit }
    when (current.state) {
        WorkInfo.State.SUCCEEDED -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.update_download_ready)) },
            text = { Text(stringResource(R.string.update_download_verified)) },
            confirmButton = { TextButton(onClick = {
                try {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                    } else {
                        val sha = current.outputData.getString("sha").orEmpty()
                        check(sha.matches(Regex("[0-9a-f]{64}")))
                        val file = File(context.cacheDir, "apk-updates/$sha/update.apk")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        dismiss()
                    }
                } catch (_: Exception) { Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show() }
            }) { Text(stringResource(R.string.update_download_install)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.update_later)) } }
        )
        WorkInfo.State.FAILED -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.update_download_failed_title)) },
            text = { Text(stringResource(R.string.update_download_retry_body)) },
            confirmButton = { TextButton(onClick = { dismiss(); onRetry() }) { Text(stringResource(R.string.action_retry_catalog)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.update_later)) } }
        )
        else -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.update_downloading)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val done = current.progress.getLong("done", 0)
                val total = current.progress.getLong("total", 0)
                if (total > 0) {
                    LinearProgressIndicator(progress = { (done.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.update_download_progress, done / 1_048_576f, total / 1_048_576f))
                } else LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.update_download_background))
            } },
            confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.update_download_background_action)) } },
            dismissButton = { TextButton(onClick = { manager.cancelWorkById(current.id); dismiss() }) { Text(stringResource(R.string.update_download_cancel)) } }
        )
    }
}
