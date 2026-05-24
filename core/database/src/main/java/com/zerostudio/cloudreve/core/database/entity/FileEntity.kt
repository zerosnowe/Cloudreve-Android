package com.zerostudio.cloudreve.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "files",
    primaryKeys = ["instanceId", "uri"],
    indices = [
        Index(value = ["instanceId", "parentUri"]),
        Index(value = ["instanceId", "parentUri", "type", "name"]),
        Index(value = ["instanceId", "type", "updatedAtEpochMillis"]),
    ],
)
data class FileEntity(
    val instanceId: String,
    val uri: String,
    val id: String,
    val name: String,
    val parentUri: String,
    val type: String,
    val size: Long,
    val mimeType: String?,
    val updatedAtEpochMillis: Long,
    val isFavorite: Boolean,
    val isOffline: Boolean,
    val thumbnailUrl: String? = null,
)
