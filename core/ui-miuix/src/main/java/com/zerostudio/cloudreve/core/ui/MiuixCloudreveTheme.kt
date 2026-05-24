package com.zerostudio.cloudreve.core.ui

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@Composable
fun MiuixCloudreveTheme(content: @Composable () -> Unit) {
    MiuixTheme(
        controller = ThemeController(
            colorSchemeMode = ColorSchemeMode.MonetSystem,
            colorSpec = ThemeColorSpec.Spec2025,
            paletteStyle = ThemePaletteStyle.TonalSpot,
        ),
    ) {
        CloudreveImmersiveSystemBars()
        content()
    }
}
