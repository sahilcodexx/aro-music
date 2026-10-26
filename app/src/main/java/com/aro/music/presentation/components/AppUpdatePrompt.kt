package com.aro.music.presentation.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import com.aro.music.data.update.ApkDownloadWorker
import java.util.UUID
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aro.music.BuildConfig
import com.aro.music.R
import com.aro.music.data.update.UpdateDialog
import com.aro.music.presentation.viewmodel.AppUpdateViewModel

@Composable
fun AppUpdatePrompt(viewModel: AppUpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var workId by remember { mutableStateOf(context.getSharedPreferences("apk-download", android.content.Context.MODE_PRIVATE).getString("work", null)?.let { runCatching { UUID.fromString(it) }.getOrNull() }) }
    ApkDownloadPrompt(workId, onRetry = viewModel::checkNow)
    LaunchedEffect(Unit) { viewModel.checkAutomatically() }
    AppUpdateDialog(state.dialog, BuildConfig.VERSION_NAME, viewModel::dismiss, viewModel::checkNow) { url ->
        val release = (state.dialog as? UpdateDialog.Available)?.release
        if (release?.sha256 != null) {
            scope.launch {
                try {
                    workId = ApkDownloadWorker.enqueue(context, release)
                    viewModel.dismiss()
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show() }
            }
        } else try {
            // This asset URL starts the browser's normal APK download, not a release-page visit.
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org")).addCategory(Intent.CATEGORY_BROWSABLE)
            val browser = context.packageManager.resolveActivity(browserIntent, 0)?.activityInfo?.packageName?.takeUnless { it == "android" }
            val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            browser?.let(downloadIntent::setPackage)
            context.startActivity(downloadIntent)
            viewModel.dismiss()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun AppUpdateDialog(
    dialog: UpdateDialog?,
    currentVersion: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDownload: (String) -> Unit
) {
    when (dialog) {
        is UpdateDialog.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_available_title, dialog.release.tag.removePrefix("v"))) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (dialog.release.notes.isBlank()) Text(stringResource(R.string.update_available_body))
                    else ReleaseNotesContent(dialog.release.notes)
                }
            },
            confirmButton = { TextButton(onClick = { onDownload(dialog.release.apkUrl) }) { Text(stringResource(R.string.update_download_apk)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } }
        )
        UpdateDialog.Current -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_current_title)) },
            text = { Text(stringResource(R.string.update_current_body, currentVersion)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok_update)) } }
        )
        UpdateDialog.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_failed_title)) },
            text = { Text(stringResource(R.string.update_failed_body)) },
            confirmButton = { TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry_catalog)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } }
        )
        null -> Unit
    }
}
