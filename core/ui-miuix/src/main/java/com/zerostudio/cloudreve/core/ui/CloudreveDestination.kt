package com.zerostudio.cloudreve.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class CloudreveDestination(
    @param:StringRes val titleRes: Int,
    @param:StringRes val labelRes: Int,
    val icon: @Composable () -> ImageVector,
) {
    Files(R.string.destination_title_files, R.string.destination_label_files, { CloudreveIcons.Files }),
    Preview(R.string.destination_title_preview, R.string.destination_label_preview, { CloudreveIcons.Preview }),
    Share(R.string.destination_title_share, R.string.destination_label_share, { CloudreveIcons.Share }),
    Settings(R.string.destination_title_settings, R.string.destination_label_settings, { CloudreveIcons.Settings }),
}
