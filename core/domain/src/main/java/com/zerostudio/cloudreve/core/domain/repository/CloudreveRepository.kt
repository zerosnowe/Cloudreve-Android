package com.zerostudio.cloudreve.core.domain.repository

import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.model.FileDetails
import com.zerostudio.cloudreve.core.domain.model.FileNode
import com.zerostudio.cloudreve.core.domain.model.ShareLink
import com.zerostudio.cloudreve.core.domain.model.SignInCommand
import com.zerostudio.cloudreve.core.domain.model.TransferTask
import kotlinx.coroutines.flow.Flow

interface CloudreveRepository {
    fun activeSession(): Flow<Boolean>
    suspend fun signIn(command: SignInCommand)
    suspend fun signOut()
    fun listFiles(uri: CloudreveUri): Flow<List<FileNode>>
    fun observeCachedImages(): Flow<List<FileNode>>
    suspend fun refreshFiles(uri: CloudreveUri)
    suspend fun rename(uri: CloudreveUri, newName: String)
    suspend fun createFolder(parentUri: CloudreveUri, name: String)
    suspend fun createFile(parentUri: CloudreveUri, name: String)
    suspend fun delete(uris: List<CloudreveUri>)
    suspend fun move(uris: List<CloudreveUri>, target: CloudreveUri)
    suspend fun copy(uris: List<CloudreveUri>, target: CloudreveUri)
    suspend fun createShare(uris: List<CloudreveUri>): ShareLink
    suspend fun createDownloadUrl(uri: CloudreveUri): String
    suspend fun createThumbnailUrl(uri: CloudreveUri): String
    suspend fun getFileDetails(uri: CloudreveUri): FileDetails
    suspend fun setFavorite(uri: CloudreveUri, favorite: Boolean)
    fun transferTasks(): Flow<List<TransferTask>>
}
