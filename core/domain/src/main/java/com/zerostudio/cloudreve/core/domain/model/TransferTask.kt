package com.zerostudio.cloudreve.core.domain.model

data class TransferTask(
    val id: String,
    val name: String,
    val direction: TransferDirection,
    val status: TransferStatus,
    val progress: Float,
    val transferredBytes: Long,
    val totalBytes: Long,
    val sourceUri: String,
    val targetUri: String,
    val isDirectory: Boolean,
)

enum class TransferDirection {
    Upload,
    Download,
}

enum class TransferStatus {
    Queued,
    Running,
    Paused,
    Succeeded,
    Failed,
    Canceled,
}
