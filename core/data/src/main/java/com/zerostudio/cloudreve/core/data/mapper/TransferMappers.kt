package com.zerostudio.cloudreve.core.data.mapper

import com.zerostudio.cloudreve.core.database.entity.TransferEntity
import com.zerostudio.cloudreve.core.domain.model.TransferDirection
import com.zerostudio.cloudreve.core.domain.model.TransferStatus
import com.zerostudio.cloudreve.core.domain.model.TransferTask

fun TransferEntity.toDomain(): TransferTask = TransferTask(
    id = id,
    name = name,
    direction = runCatching { TransferDirection.valueOf(direction) }.getOrDefault(TransferDirection.Upload),
    status = runCatching { TransferStatus.valueOf(status) }.getOrDefault(TransferStatus.Queued),
    progress = progress,
    transferredBytes = transferredBytes,
    totalBytes = totalBytes,
    sourceUri = sourceUri,
    targetUri = targetUri,
    isDirectory = isDirectory,
)
