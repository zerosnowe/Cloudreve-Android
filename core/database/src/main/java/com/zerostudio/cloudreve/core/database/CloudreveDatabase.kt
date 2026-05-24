package com.zerostudio.cloudreve.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zerostudio.cloudreve.core.database.dao.FileDao
import com.zerostudio.cloudreve.core.database.dao.TransferDao
import com.zerostudio.cloudreve.core.database.entity.FileEntity
import com.zerostudio.cloudreve.core.database.entity.TransferEntity

@Database(
    entities = [
        FileEntity::class,
        TransferEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class CloudreveDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun transferDao(): TransferDao
}
