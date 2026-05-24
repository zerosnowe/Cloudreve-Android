package com.zerostudio.cloudreve.feature.preview

import com.zerostudio.cloudreve.core.common.DispatchersProvider
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.model.FileNode
import com.zerostudio.cloudreve.core.domain.model.FileType
import com.zerostudio.cloudreve.core.domain.repository.CloudreveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

class CloudreveAlbumRepository(
    private val repository: CloudreveRepository,
    private val dispatchers: DispatchersProvider,
) {
    fun cachedImages(): Flow<List<AlbumMediaItem>> =
        repository.observeCachedImages()
            .map { files ->
                files.map { it.toAlbumMediaItem() }
                    .sortedByDescending { it.updatedAtEpochMillis }
            }
            .flowOn(dispatchers.default)

    fun scanImages(root: CloudreveUri = CloudreveUri.Root): Flow<AlbumScanSnapshot> =
        flow {
            val folders = ArrayDeque<CloudreveUri>()
            val visitedFolders = linkedSetOf<String>()
            val images = linkedMapOf<CloudreveUri, AlbumMediaItem>()
            folders += root

            var scannedFolders = 0
            var pendingImageCount = 0

            while (folders.isNotEmpty()) {
                val currentFolder = CloudreveUri.parse(folders.removeFirst().value)
                if (!visitedFolders.add(currentFolder.value)) continue

                val children = loadFolderChildren(currentFolder)
                scannedFolders += 1

                children.forEach { child ->
                    when (child.type) {
                        FileType.Folder -> folders += child.uri
                        FileType.Image -> {
                            images[child.uri] = child.toAlbumMediaItem()
                            pendingImageCount += 1
                        }

                        else -> Unit
                    }
                }

                // Huge Cloudreve libraries must not wait for the full recursive scan.
                // Emit paged batches from Dispatchers.IO so Compose only receives small,
                // incremental list diffs while the folder walk continues in the background.
                if (pendingImageCount >= IMAGE_BATCH_SIZE || scannedFolders % FOLDER_BATCH_SIZE == 0) {
                    emit(
                        AlbumScanSnapshot(
                            images = images.values.toList(),
                            scannedFolders = scannedFolders,
                            complete = false,
                        ),
                    )
                    pendingImageCount = 0
                }
            }

            emit(
                AlbumScanSnapshot(
                    images = images.values.toList(),
                    scannedFolders = scannedFolders,
                    complete = true,
                ),
            )
        }.flowOn(dispatchers.io)

    suspend fun createThumbnailUrl(uri: CloudreveUri): String =
        repository.createThumbnailUrl(uri)

    private suspend fun loadFolderChildren(folder: CloudreveUri): List<FileNode> {
        val refreshFailure = runCatching {
            repository.refreshFiles(folder)
        }.exceptionOrNull()
        val cachedChildren = withTimeoutOrNull(LIST_FILES_TIMEOUT_MILLIS) {
            repository.listFiles(folder).first()
        }.orEmpty()
        if (cachedChildren.isEmpty() && refreshFailure != null) throw refreshFailure
        return cachedChildren
    }

    private fun FileNode.toAlbumMediaItem(): AlbumMediaItem =
        AlbumMediaItem(
            id = id,
            name = name,
            uri = uri,
            parentUri = parentUri,
            size = size,
            mimeType = mimeType,
            updatedAtEpochMillis = updatedAtEpochMillis,
            thumbnailUrl = thumbnailUrl,
        )

    private companion object {
        const val IMAGE_BATCH_SIZE = 48
        const val FOLDER_BATCH_SIZE = 8
        const val LIST_FILES_TIMEOUT_MILLIS = 4_000L
    }
}
