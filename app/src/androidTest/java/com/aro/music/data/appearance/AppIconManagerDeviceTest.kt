package com.aro.music.data.appearance

import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIconManagerDeviceTest {
    @Test
    fun switchingKeepsExactlyOneLauncherAndPreservesTheActivity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AppIconManager(context)
        val original = manager.currentIcon()
        val main = ComponentName(context.packageName, "com.aro.music.MainActivity")
        val activityState = context.packageManager.getComponentEnabledSetting(main)
        try {
            for (icon in AppIcon.entries) {
                manager.select(icon)
                // A fresh reader must recover the persisted choice without a ViewModel preference.
                assertEquals(icon, AppIconManager(context).currentIcon())
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(context.packageName)
                @Suppress("DEPRECATION")
                val launchers = context.packageManager.queryIntentActivities(intent, 0)
                assertEquals(1, launchers.size)
                assertEquals("${context.packageName}.launcher.${icon.alias}", launchers.single().activityInfo.name)
                assertEquals(activityState, context.packageManager.getComponentEnabledSetting(main))
                assertTrue(context.packageManager.getActivityInfo(main, 0).enabled)
            }
        } finally {
            manager.select(original)
        }
    }
}
