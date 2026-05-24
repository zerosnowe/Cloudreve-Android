package com.zerostudio.cloudreve.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zerostudio.cloudreve.core.database.entity.TransferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY status ASC, name COLLATE NOCASE ASC")
    fun observeTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TransferEntity?

    @Upsert
    suspend fun upsert(task: TransferEntity)

    @Query("DELETE FROM transfers WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
