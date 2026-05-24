package com.zerostudio.cloudreve.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.zerostudio.cloudreve.core.database.entity.FileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query(
        """
        SELECT * FROM files
        WHERE instanceId = :instanceId AND parentUri = :parentUri
        ORDER BY type ASC, name COLLATE NOCASE ASC
        """,
    )
    fun observeChildren(instanceId: String, parentUri: String): Flow<List<FileEntity>>

    @Query(
        """
        SELECT * FROM files
        WHERE instanceId = :instanceId AND type = 'Image'
        ORDER BY updatedAtEpochMillis DESC, name COLLATE NOCASE ASC
        """,
    )
    fun observeImages(instanceId: String): Flow<List<FileEntity>>

    @Query(
        """
        SELECT * FROM files
        WHERE instanceId = :instanceId AND parentUri = :parentUri
        """,
    )
    suspend fun getChildren(instanceId: String, parentUri: String): List<FileEntity>

    @Query("DELETE FROM files WHERE instanceId = :instanceId AND parentUri = :parentUri")
    suspend fun deleteChildren(instanceId: String, parentUri: String)

    @Query("DELETE FROM files WHERE instanceId = :instanceId AND uri IN (:uris)")
    suspend fun deleteByUris(instanceId: String, uris: List<String>)

    @Query("DELETE FROM files WHERE instanceId = :instanceId")
    suspend fun deleteInstance(instanceId: String)

    @Query("UPDATE files SET isFavorite = :favorite WHERE instanceId = :instanceId AND uri = :uri")
    suspend fun setFavorite(instanceId: String, uri: String, favorite: Boolean)

    @Query("UPDATE files SET thumbnailUrl = :thumbnailUrl WHERE instanceId = :instanceId AND uri = :uri")
    suspend fun updateThumbnailUrl(instanceId: String, uri: String, thumbnailUrl: String?)

    @Upsert
    suspend fun upsertAll(files: List<FileEntity>)

    @Transaction
    suspend fun replaceChildren(instanceId: String, parentUri: String, files: List<FileEntity>) {
        val existingChildren = getChildren(instanceId, parentUri)
        val existingByUri = existingChildren.associateBy(FileEntity::uri)
        val mergedFiles = files.map { file ->
            val existing = existingByUri[file.uri]
            file.copy(
                isFavorite = file.isFavorite || existing?.isFavorite == true,
                isOffline = file.isOffline || existing?.isOffline == true,
                thumbnailUrl = file.thumbnailUrl ?: existing?.thumbnailUrl,
            )
        }
        val filesToUpsert = mergedFiles.filter { file ->
            existingByUri[file.uri] != file
        }
        val nextUris = mergedFiles.mapTo(hashSetOf(), FileEntity::uri)
        val staleUris = existingByUri.keys - nextUris

        if (filesToUpsert.isNotEmpty()) {
            upsertAll(filesToUpsert)
        }
        if (staleUris.isNotEmpty()) {
            deleteByUris(instanceId, staleUris.toList())
        } else if (mergedFiles.isEmpty() && existingChildren.isNotEmpty()) {
            deleteChildren(instanceId, parentUri)
        }
    }
}
