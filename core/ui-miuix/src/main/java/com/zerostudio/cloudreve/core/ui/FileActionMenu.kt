package com.zerostudio.cloudreve.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun FileOverflowMenu(
    show: Boolean,
    onDismiss: () -> Unit,
    onOpenWith: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    canOpenWith: Boolean = true,
    canRename: Boolean = true,
    canCopy: Boolean = true,
) {
    OverlayListPopup(
        show = show,
        popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
        alignment = PopupPositionProvider.Align.End,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
    ) {
        ListPopupColumn {
            Column(
                modifier = Modifier.padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FileMenuItem(
                    icon = MiuixIcons.Regular.File,
                    label = stringResource(R.string.file_action_open_with),
                    enabled = canOpenWith,
                ) {
                    onDismiss()
                    onOpenWith()
                }
                FileMenuItem(
                    icon = MiuixIcons.Regular.Download,
                    label = stringResource(R.string.file_action_download),
                ) {
                    onDismiss()
                    onDownload()
                }
                FileMenuItem(
                    icon = MiuixIcons.Regular.Share,
                    label = stringResource(R.string.file_action_share),
                ) {
                    onDismiss()
                    onShare()
                }
                FileMenuItem(
                    icon = MiuixIcons.Regular.Rename,
                    label = stringResource(R.string.file_action_rename),
                    enabled = canRename,
                ) {
                    onDismiss()
                    onRename()
                }
                FileMenuItem(
                    icon = MiuixIcons.Regular.Copy,
                    label = stringResource(R.string.file_action_copy),
                    enabled = canCopy,
                ) {
                    onDismiss()
                    onCopy()
                }
            }
        }
    }
}

@Composable
fun RenameFileBottomSheet(
    show: Boolean,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(show, initialValue) { mutableStateOf(initialValue) }
    val trimmed = value.trim()
    val canSave = trimmed.isNotEmpty() && trimmed != initialValue

    WindowBottomSheet(
        show = show,
        title = stringResource(R.string.file_rename_title),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.file_rename_label),
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(android.R.string.cancel),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
                Text(
                    text = stringResource(R.string.common_save),
                    color = if (canSave) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .clickable(enabled = canSave) { onConfirm(trimmed) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun FileMenuItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint = MiuixTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = tint,
        )
    }
}
