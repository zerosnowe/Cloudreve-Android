package com.zerostudio.cloudreve.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey val id: String,
    val name: String,
    val direction: String,
    val status: String,
    val progress: Float,
    val transferredBytes: Long,
    val totalBytes: Long,
    val sourceUri: String,
    val targetUri: String,
    val isDirectory: Boolean,
)
