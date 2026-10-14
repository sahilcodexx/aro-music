package com.aro.music.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aro.music.data.update.AppRelease
import com.aro.music.data.update.AppVersion
import com.aro.music.data.update.AroProject
import com.aro.music.data.update.UpdateDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateDialogDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun availableUpdateDownloadsTheSelectedApkInsteadOfTheReleasePage() {
        val url = "${AroProject.URL}/releases/download/v2.0.0/Aro-arm64.apk"
        var download: String? = null
        compose.setContent { MaterialTheme { AppUpdateDialog(UpdateDialog.Available(AppRelease("v2.0.0",AppVersion.parse("2.0.0")!!,url,"New features")),"1.0.0",{}, {}, { download = it }) } }
        compose.onNodeWithText("Aro 2.0.0 is available").assertIsDisplayed()
        compose.onNodeWithText("Download APK").performClick()
        compose.runOnIdle { assertEquals(url,download) }
    }
    @Test fun currentBuildDoesNotOfferADowngradeDownload() {
        compose.setContent { MaterialTheme { AppUpdateDialog(UpdateDialog.Current,"1.0.0-rc.4",{}, {}, { error("No update available") }) } }
        compose.onNodeWithText("You’re up to date").assertIsDisplayed()
        compose.onNodeWithText("Download APK").assertDoesNotExist()
    }
    @Test fun failedCheckOffersRetryWithoutAnApkDownload() {
        var retried = false
        compose.setContent { MaterialTheme { AppUpdateDialog(UpdateDialog.Failed,"1.0.0",{}, { retried = true }, { error("No verified asset") }) } }
        compose.onNodeWithText("Download APK").assertDoesNotExist()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertTrue(retried) }
    }
}
