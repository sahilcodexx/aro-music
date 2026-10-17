package com.aro.music.data.appearance

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.aro.music.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class AppIcon(val alias: String, @StringRes val label: Int, @DrawableRes val artwork: Int) {
    WHITE("WhiteIcon", R.string.app_icon_white, R.drawable.aro_icon_white),
    WHITE_BLACK("WhiteBlackIcon", R.string.app_icon_white_black, R.drawable.aro_icon_white_black),
    GREEN("GreenIcon", R.string.app_icon_green, R.drawable.aro_icon_green),
    DARK_GREEN("DarkGreenIcon", R.string.app_icon_dark_green, R.drawable.aro_icon_dark_green),
    PINK("PinkIcon", R.string.app_icon_pink, R.drawable.aro_icon_pink),
    BLUE("BlueIcon", R.string.app_icon_blue, R.drawable.aro_icon_blue),
    BLUE_SHADE("BlueShadeIcon", R.string.app_icon_blue_shade, R.drawable.aro_icon_blue_shade),
    BLACK("BlackIcon", R.string.app_icon_black, R.drawable.aro_icon_black)
}

/** Android persists alias states across app restarts and upgrades; no separate preference can drift. */
@Singleton
class AppIconManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val packageManager = context.packageManager
    private val mutex = Mutex()
    private val _selected = MutableStateFlow(currentIcon())
    val selected = _selected.asStateFlow()

    private fun component(icon: AppIcon) = ComponentName(context.packageName, "${context.packageName}.launcher.${icon.alias}")

    fun currentIcon(): AppIcon {
        val enabled = AppIcon.entries.filter { icon ->
            when (packageManager.getComponentEnabledSetting(component(icon))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.WHITE
                else -> false
            }
        }
        return enabled.firstOrNull { it != AppIcon.WHITE } ?: AppIcon.WHITE
    }

    suspend fun select(icon: AppIcon) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val before = currentIcon()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.setComponentEnabledSettings(AppIcon.entries.map { candidate ->
                        PackageManager.ComponentEnabledSetting(component(candidate),
                            if (candidate == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP)
                    })
                } else {
                    // Enable the destination first so the launcher never temporarily loses Aro.
                    setEnabled(icon, true)
                    AppIcon.entries.filter { it != icon }.forEach { setEnabled(it, false) }
                }
                _selected.value = currentIcon()
            } catch (error: Exception) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    runCatching {
                        setEnabled(before, true)
                        AppIcon.entries.filter { it != before }.forEach { setEnabled(it, false) }
                    }
                }
                _selected.value = currentIcon()
                throw error
            }
        }
    }

    private fun setEnabled(icon: AppIcon, enabled: Boolean) {
        packageManager.setComponentEnabledSetting(component(icon),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP)
    }
}
