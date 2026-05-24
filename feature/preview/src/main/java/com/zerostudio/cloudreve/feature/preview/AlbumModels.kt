package com.zerostudio.cloudreve.feature.preview

import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.model.FileNode
import com.zerostudio.cloudreve.core.domain.model.FileType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class AlbumMediaItem(
    val id: String,
    val name: String,
    val uri: CloudreveUri,
    val parentUri: CloudreveUri,
    val size: Long,
    val mimeType: String?,
    val updatedAtEpochMillis: Long,
    val thumbnailUrl: String? = null,
) {
    fun toFileNode(resolvedThumbnailUrl: String? = thumbnailUrl): FileNode =
        FileNode(
            id = id,
            name = name,
            uri = uri,
            parentUri = parentUri,
            type = FileType.Image,
            size = size,
            mimeType = mimeType,
            updatedAtEpochMillis = updatedAtEpochMillis,
            thumbnailUrl = resolvedThumbnailUrl,
        )
}

data class AlbumScanSnapshot(
    val images: List<AlbumMediaItem>,
    val scannedFolders: Int,
    val complete: Boolean,
)

data class AlbumUiState(
    val query: String = "",
    val allImages: List<AlbumMediaItem> = emptyList(),
    val gridItems: List<AlbumGridItem> = emptyList(),
    val thumbnailUrls: Map<CloudreveUri, String> = emptyMap(),
    val scannedFolders: Int = 0,
    val isScanning: Boolean = false,
    val hasCachedImages: Boolean = false,
    val infoEvent: AlbumInfoEvent? = null,
    val errorMessage: String? = null,
)

data class AlbumInfoEvent(
    val id: Long,
    val type: AlbumInfoEventType,
)

enum class AlbumInfoEventType {
    SyncStarted,
    SyncCompleted,
}

sealed interface AlbumGridItem {
    val key: String

    data class Header(
        override val key: String,
        val title: String,
    ) : AlbumGridItem

    data class Photo(
        override val key: String,
        val media: AlbumMediaItem,
        val thumbnailUrl: String?,
    ) : AlbumGridItem
}

internal fun buildAlbumGridItems(
    images: List<AlbumMediaItem>,
    query: String,
    thumbnailUrls: Map<CloudreveUri, String>,
): List<AlbumGridItem> {
    val trimmedQuery = query.trim()
    val filtered = images.asSequence()
        .filter { item ->
            trimmedQuery.isBlank() ||
                item.name.contains(trimmedQuery, ignoreCase = true) ||
                item.parentUri.value.contains(trimmedQuery, ignoreCase = true)
        }
        .sortedByDescending { it.updatedAtEpochMillis }
        .toList()

    return filtered.map { item ->
        AlbumGridItem.Photo(
            key = item.uri.value,
            media = item,
            thumbnailUrl = thumbnailUrls[item.uri] ?: item.thumbnailUrl,
        )
    }
}

internal fun AlbumMediaItem.albumDateTitle(): String =
    DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(updatedAtEpochMillis))

internal fun List<AlbumGridItem>.currentPhotoDateTitle(firstVisibleItemIndex: Int): String {
    if (isEmpty()) return ""
    val start = firstVisibleItemIndex.coerceIn(0, lastIndex)
    for (index in start downTo 0) {
        val photo = this[index] as? AlbumGridItem.Photo
        if (photo != null) return photo.media.albumDateTitle()
    }
    return filterIsInstance<AlbumGridItem.Photo>().firstOrNull()?.media?.albumDateTitle().orEmpty()
}
