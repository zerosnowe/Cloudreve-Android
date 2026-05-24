package com.zerostudio.cloudreve.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.zerostudio.cloudreve.core.domain.model.FileNode
import com.zerostudio.cloudreve.core.domain.model.FileType
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val FileIconBoxSize = 40.dp
private val FileImageIconSize = 36.dp
private const val ApkMimeType = "application/vnd.android.package-archive"

@Composable
fun FileRow(
    file: FileNode,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelected: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailUrl: String? = null,
    thumbnailCacheGeneration: Int = 0,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        onLongPress = onLongPress,
        cornerRadius = 8.dp,
        colors = CardDefaults.defaultColors(
            color = Color.Transparent,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(visible = selectionActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Checkbox(
                            state = if (selected) ToggleableState.On else ToggleableState.Off,
                            onClick = onToggleSelected,
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
            FileTypeVisual(
                file = file,
                thumbnailUrl = thumbnailUrl,
                thumbnailCacheGeneration = thumbnailCacheGeneration,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = file.subtitleText(),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MiuixTheme.textStyles.body2,
                )
            }
        }
    }
}

@Composable
fun FileTypeVisual(
    file: FileNode,
    thumbnailUrl: String? = null,
    thumbnailCacheGeneration: Int = 0,
    modifier: Modifier = Modifier,
    boxSize: Dp = FileIconBoxSize,
    imageSize: Dp = FileImageIconSize,
) {
    Box(
        modifier = modifier.size(boxSize),
        contentAlignment = Alignment.Center,
    ) {
        val thumbnailModel = remember(thumbnailUrl, thumbnailCacheGeneration) {
            thumbnailUrl?.takeIf { file.supportsThumbnail }?.let { url ->
                if ('#' in url) {
                    "$url&thumbgen=$thumbnailCacheGeneration"
                } else {
                    "$url#thumbgen=$thumbnailCacheGeneration"
                }
            }
        }
        if (thumbnailModel != null) {
            SubcomposeAsyncImage(
                model = thumbnailModel,
                contentDescription = file.name,
                modifier = Modifier
                    .size(boxSize)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer),
                contentScale = ContentScale.Crop,
                loading = {
                    FileTypeFallbackIcon(file = file, imageSize = imageSize)
                },
                error = {
                    FileTypeFallbackIcon(file = file, imageSize = imageSize)
                },
            )
        } else {
            FileTypeFallbackIcon(file = file, imageSize = imageSize)
        }
    }
}

@Composable
private fun FileTypeFallbackIcon(
    file: FileNode,
    imageSize: Dp,
) {
    if (file.type == FileType.Folder) {
        Image(
            painter = painterResource(R.drawable.cloudreve_folder_icon),
            contentDescription = stringResource(R.string.file_type_folder),
            modifier = Modifier.size(imageSize),
        )
    } else if (file.isApkFile) {
        Image(
            painter = painterResource(R.drawable.apk_icon),
            contentDescription = stringResource(R.string.file_type_apk),
            modifier = Modifier.size(imageSize),
        )
    } else if (file.type == FileType.Archive) {
        Image(
            painter = painterResource(R.drawable.zip_icon),
            contentDescription = stringResource(R.string.file_type_archive),
            modifier = Modifier.size(imageSize),
        )
    } else if (file.type == FileType.Audio) {
        Icon(
            imageVector = MiuixIcons.Regular.Music,
            contentDescription = stringResource(R.string.file_type_audio),
            modifier = Modifier.size(imageSize),
            tint = MiuixTheme.colorScheme.primary,
        )
    } else {
        Text(
            text = file.type.symbol,
            style = MiuixTheme.textStyles.title4,
        )
    }
}

private val FileType.symbol: String
    get() = when (this) {
        FileType.Folder -> ""
        FileType.Image -> "[I]"
        FileType.Video -> "[V]"
        FileType.Audio -> "[A]"
        FileType.Pdf -> "PDF"
        FileType.Office -> "DOC"
        FileType.Code -> "{ }"
        FileType.Archive -> "ZIP"
        FileType.Unknown -> "[F]"
    }

@Composable
private fun FileNode.subtitleText(): String =
    if (type == FileType.Folder) {
        stringResource(R.string.file_type_folder)
    } else {
        formatCloudreveFileSize(size)
    }

private val FileNode.isApkFile: Boolean
    get() = name.endsWith(".apk", ignoreCase = true) || mimeType.equals(ApkMimeType, ignoreCase = true)

private val FileNode.supportsThumbnail: Boolean
    get() = type == FileType.Image || type == FileType.Video || type == FileType.Audio

fun formatCloudreveFileSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    if (safeBytes == 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var value = safeBytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "$safeBytes ${units[unitIndex]}"
    } else {
        String.format(Locale.US, "%.2f %s", value, units[unitIndex])
    }
}

fun formatCloudreveBytesWithRaw(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    return "${formatCloudreveFileSize(safeBytes)} ($safeBytes ${if (safeBytes == 1L) "byte" else "bytes"})"
}

fun cloudreveUriDisplayName(uri: String): String {
    val lastSegment = uri.substringAfterLast('/').ifBlank { uri }
    return runCatching {
        URLDecoder.decode(lastSegment, StandardCharsets.UTF_8.name())
    }.getOrDefault(lastSegment)
}
