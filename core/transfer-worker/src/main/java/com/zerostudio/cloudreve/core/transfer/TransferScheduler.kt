package com.zerostudio.cloudreve.core.transfer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.zerostudio.cloudreve.core.database.dao.TransferDao
import com.zerostudio.cloudreve.core.database.entity.TransferEntity
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.model.FileNode
import com.zerostudio.cloudreve.core.domain.model.TransferDirection
import com.zerostudio.cloudreve.core.domain.model.TransferStatus
import com.zerostudio.cloudreve.core.domain.model.TransferTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class TransferScheduler(
    context: Context,
    private val transferDao: TransferDao,
) {
    private val context = context.applicationContext
    private val workManager: WorkManager by lazy(LazyThreadSafetyMode.NONE) {
        WorkManager.getInstance(this.context)
    }
    private val networkConstraints: Constraints by lazy(LazyThreadSafetyMode.NONE) {
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    suspend fun enqueueUpload(sourceUri: String, target: CloudreveUri): UUID = withContext(Dispatchers.IO) {
        val taskId = UUID.randomUUID()
        val metadata = sourceUri.toContentMetadata()
        transferDao.upsert(
            TransferEntity(
                id = taskId.toString(),
                name = metadata.name,
                direction = TransferDirection.Upload.name,
                status = TransferStatus.Queued.name,
                progress = 0f,
                transferredBytes = 0L,
                totalBytes = metadata.size,
                sourceUri = sourceUri,
                targetUri = target.value,
                isDirectory = false,
            ),
        )
        val data = Data.Builder()
            .putString(UploadWorker.KEY_TASK_ID, taskId.toString())
            .putString(UploadWorker.KEY_SOURCE_URI, sourceUri)
            .putString(UploadWorker.KEY_TARGET_URI, target.value)
            .putString(UploadWorker.KEY_DISPLAY_NAME, metadata.name)
            .putLong(UploadWorker.KEY_TOTAL_BYTES, metadata.size)
            .putBoolean(UploadWorker.KEY_IS_DIRECTORY, false)
            .build()
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setId(taskId)
            .setInputData(data)
            .setConstraints(networkConstraints)
            .build()
        workManager.enqueue(request)
        taskId
    }

    suspend fun enqueueDirectoryUpload(sourceUri: String, target: CloudreveUri): UUID = withContext(Dispatchers.IO) {
        val taskId = UUID.randomUUID()
        val metadata = sourceUri.toTreeMetadata()
        transferDao.upsert(
            TransferEntity(
                id = taskId.toString(),
                name = metadata.name,
                direction = TransferDirection.Upload.name,
                status = TransferStatus.Queued.name,
                progress = 0f,
                transferredBytes = 0L,
                totalBytes = 0L,
                sourceUri = sourceUri,
                targetUri = target.value,
                isDirectory = true,
            ),
        )
        val data = Data.Builder()
            .putString(UploadWorker.KEY_TASK_ID, taskId.toString())
            .putString(UploadWorker.KEY_SOURCE_URI, sourceUri)
            .putString(UploadWorker.KEY_TARGET_URI, target.value)
            .putString(UploadWorker.KEY_DISPLAY_NAME, metadata.name)
            .putLong(UploadWorker.KEY_TOTAL_BYTES, 0L)
            .putBoolean(UploadWorker.KEY_IS_DIRECTORY, true)
            .build()
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setId(taskId)
            .setInputData(data)
            .setConstraints(networkConstraints)
            .build()
        workManager.enqueue(request)
        taskId
    }

    suspend fun enqueueDownload(file: FileNode): UUID = withContext(Dispatchers.IO) {
        val taskId = UUID.randomUUID()
        transferDao.upsert(
            TransferEntity(
                id = taskId.toString(),
                name = file.name,
                direction = TransferDirection.Download.name,
                status = TransferStatus.Queued.name,
                progress = 0f,
                transferredBytes = 0L,
                totalBytes = file.size.coerceAtLeast(0L),
                sourceUri = "",
                targetUri = file.uri.value,
                isDirectory = false,
            ),
        )
        val data = Data.Builder()
            .putString(DownloadWorker.KEY_TASK_ID, taskId.toString())
            .putString(DownloadWorker.KEY_FILE_URI, file.uri.value)
            .putString(DownloadWorker.KEY_DISPLAY_NAME, file.name)
            .putLong(DownloadWorker.KEY_TOTAL_BYTES, file.size.coerceAtLeast(0L))
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setId(taskId)
            .setInputData(data)
            .setConstraints(networkConstraints)
            .build()
        workManager.enqueue(request)
        taskId
    }

    suspend fun deleteTransfers(ids: Collection<String>) = withContext(Dispatchers.IO) {
        val distinctIds = ids.mapNotNull { it.takeIf(String::isNotBlank) }.distinct()
        if (distinctIds.isEmpty()) return@withContext
        distinctIds.forEach(::cancelWorkByTransferId)
        transferDao.deleteByIds(distinctIds)
    }

    suspend fun retryUploads(tasks: Collection<TransferTask>): List<UUID> = withContext(Dispatchers.IO) {
        val uploadTasks = tasks
            .filter { it.direction == TransferDirection.Upload }
            .filter { it.sourceUri.isNotBlank() }
        if (uploadTasks.isEmpty()) return@withContext emptyList()

        uploadTasks.map { it.id }.distinct().forEach(::cancelWorkByTransferId)
        transferDao.deleteByIds(uploadTasks.map { it.id }.distinct())

        val retried = mutableListOf<UUID>()
        for (task in uploadTasks) {
            val target = CloudreveUri.parse(task.targetUri.ifBlank { CloudreveUri.Root.value })
            val retryId = if (task.isDirectory) {
                enqueueDirectoryUpload(task.sourceUri, target)
            } else {
                enqueueUpload(task.sourceUri, target)
            }
            retried += retryId
        }
        retried
    }

    private fun String.toContentMetadata(): TransferMetadata {
        val uri = Uri.parse(this)
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                return TransferMetadata(
                    name = name.orEmpty().ifBlank { uri.lastPathSegment.orEmpty().substringAfterLast('/') },
                    size = size.takeIf { it > 0L } ?: uri.contentLengthOrZero(),
                )
            }
        }
        return TransferMetadata(
            name = uri.lastPathSegment.orEmpty().substringAfterLast('/').ifBlank { "upload" },
            size = uri.contentLengthOrZero(),
        )
    }

    private fun String.toTreeMetadata(): TransferMetadata {
        val uri = Uri.parse(this)
        val rawName = runCatching {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            context.contentResolver.query(documentUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex) else null
                } else {
                    null
                }
            }
        }.getOrNull().orEmpty().ifBlank {
            uri.lastPathSegment.orEmpty()
                .substringAfterLast(':')
                .substringAfterLast('/')
        }
        return TransferMetadata(name = rawName.ifBlank { "folder" }, size = 0L)
    }

    private fun Uri.contentLengthOrZero(): Long {
        runCatching {
            context.contentResolver.openAssetFileDescriptor(this, "r")?.use { descriptor ->
                if (descriptor.length > 0L) return descriptor.length
            }
        }
        runCatching {
            context.contentResolver.openFileDescriptor(this, "r")?.use { descriptor ->
                if (descriptor.statSize > 0L) return descriptor.statSize
            }
        }
        return runCatching {
            context.contentResolver.openInputStream(this)?.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                }
                total
            } ?: 0L
        }.getOrDefault(0L).coerceAtLeast(0L)
    }

    private fun cancelWorkByTransferId(id: String) {
        val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return
        runCatching {
            workManager.cancelWorkById(uuid).result.get()
        }
    }
}

private data class TransferMetadata(
    val name: String,
    val size: Long,
)
