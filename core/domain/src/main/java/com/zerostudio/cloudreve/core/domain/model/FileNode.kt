package com.zerostudio.cloudreve.core.domain.model

data class FileNode(
    val id: String,
    val name: String,
    val uri: CloudreveUri,
    val parentUri: CloudreveUri,
    val type: FileType,
    val size: Long,
    val mimeType: String?,
    val updatedAtEpochMillis: Long,
    val isFavorite: Boolean = false,
    val isOffline: Boolean = false,
    val thumbnailUrl: String? = null,
)

data class FileDetails(
    val id: String,
    val name: String,
    val uri: CloudreveUri,
    val type: FileType,
    val size: Long,
    val mimeType: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val owned: Boolean?,
    val storagePolicyName: String?,
    val storageUsed: Long?,
    val metadata: Map<String, String>,
)

enum class FileType {
    Folder,
    Image,
    Video,
    Audio,
    Pdf,
    Office,
    Code,
    Archive,
    Unknown,
}
