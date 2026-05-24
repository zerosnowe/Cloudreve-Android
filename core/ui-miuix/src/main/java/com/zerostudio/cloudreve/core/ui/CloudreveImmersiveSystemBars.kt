package com.zerostudio.cloudreve.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

fun Activity.enableCloudreveImmersiveSystemBars() {
    window.configureCloudreveImmersiveSystemBars(resources.configuration.isNightMode)
}

@Composable
fun CloudreveImmersiveSystemBars(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        view.context.findActivity()
            ?.window
            ?.configureCloudreveImmersiveSystemBars(isDarkTheme)
    }
}

@Suppress("DEPRECATION")
private fun Window.configureCloudreveImmersiveSystemBars(isDarkTheme: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(this, false)

    clearFlags(
        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
    )
    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

    statusBarColor = Color.TRANSPARENT
    navigationBarColor = Color.TRANSPARENT
    navigationBarDividerColor = Color.TRANSPARENT

    decorView.systemUiVisibility =
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        attributes = attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isStatusBarContrastEnforced = false
        isNavigationBarContrastEnforced = false
    }

    WindowInsetsControllerCompat(this, decorView).apply {
        isAppearanceLightStatusBars = !isDarkTheme
        isAppearanceLightNavigationBars = !isDarkTheme
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

private val Configuration.isNightMode: Boolean
    get() = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
