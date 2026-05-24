package com.zerostudio.cloudreve.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Image

object CloudreveIcons {
    val Files: ImageVector
        @Composable get() = MiuixIcons.Regular.Folder

    val Preview: ImageVector
        @Composable get() = MiuixIcons.Regular.Image

    val Share: ImageVector
        @Composable get() = rememberVector("share") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(18f, 16.1f)
                curveTo(17.2f, 16.1f, 16.5f, 16.4f, 16f, 16.9f)
                lineTo(8.9f, 12.8f)
                curveTo(9f, 12.5f, 9f, 12.3f, 9f, 12f)
                curveTo(9f, 11.7f, 9f, 11.5f, 8.9f, 11.2f)
                lineTo(16f, 7.1f)
                curveTo(16.5f, 7.6f, 17.2f, 7.9f, 18f, 7.9f)
                curveTo(19.7f, 7.9f, 21f, 6.6f, 21f, 4.9f)
                curveTo(21f, 3.3f, 19.7f, 2f, 18f, 2f)
                curveTo(16.3f, 2f, 15f, 3.3f, 15f, 4.9f)
                curveTo(15f, 5.2f, 15f, 5.4f, 15.1f, 5.7f)
                lineTo(8f, 9.8f)
                curveTo(7.5f, 9.3f, 6.8f, 9f, 6f, 9f)
                curveTo(4.3f, 9f, 3f, 10.3f, 3f, 12f)
                curveTo(3f, 13.7f, 4.3f, 15f, 6f, 15f)
                curveTo(6.8f, 15f, 7.5f, 14.7f, 8f, 14.2f)
                lineTo(15.1f, 18.3f)
                curveTo(15f, 18.6f, 15f, 18.8f, 15f, 19.1f)
                curveTo(15f, 20.7f, 16.3f, 22f, 18f, 22f)
                curveTo(19.7f, 22f, 21f, 20.7f, 21f, 19.1f)
                curveTo(21f, 17.4f, 19.7f, 16.1f, 18f, 16.1f)
                close()
            }
        }

    val Settings: ImageVector
        @Composable get() = rememberVector("settings") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19.4f, 13.5f)
                curveTo(19.5f, 13f, 19.6f, 12.5f, 19.6f, 12f)
                curveTo(19.6f, 11.5f, 19.5f, 11f, 19.4f, 10.5f)
                lineTo(21.2f, 9.1f)
                lineTo(19.2f, 5.7f)
                lineTo(17.1f, 6.6f)
                curveTo(16.3f, 5.9f, 15.4f, 5.4f, 14.4f, 5.1f)
                lineTo(14.1f, 3f)
                horizontalLineTo(10.1f)
                lineTo(9.8f, 5.1f)
                curveTo(8.8f, 5.4f, 7.9f, 5.9f, 7.1f, 6.6f)
                lineTo(5f, 5.7f)
                lineTo(3f, 9.1f)
                lineTo(4.8f, 10.5f)
                curveTo(4.7f, 11f, 4.6f, 11.5f, 4.6f, 12f)
                curveTo(4.6f, 12.5f, 4.7f, 13f, 4.8f, 13.5f)
                lineTo(3f, 14.9f)
                lineTo(5f, 18.3f)
                lineTo(7.1f, 17.4f)
                curveTo(7.9f, 18.1f, 8.8f, 18.6f, 9.8f, 18.9f)
                lineTo(10.1f, 21f)
                horizontalLineTo(14.1f)
                lineTo(14.4f, 18.9f)
                curveTo(15.4f, 18.6f, 16.3f, 18.1f, 17.1f, 17.4f)
                lineTo(19.2f, 18.3f)
                lineTo(21.2f, 14.9f)
                close()
                moveTo(12.1f, 15.5f)
                curveTo(10.2f, 15.5f, 8.6f, 13.9f, 8.6f, 12f)
                curveTo(8.6f, 10.1f, 10.2f, 8.5f, 12.1f, 8.5f)
                curveTo(14f, 8.5f, 15.6f, 10.1f, 15.6f, 12f)
                curveTo(15.6f, 13.9f, 14f, 15.5f, 12.1f, 15.5f)
                close()
            }
        }
}

@Composable
private fun rememberVector(name: String, builder: ImageVector.Builder.() -> Unit): ImageVector =
    remember(name) {
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(builder).build()
    }
