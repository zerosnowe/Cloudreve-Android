package com.zerostudio.cloudreve.core.transfer

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zerostudio.cloudreve.core.database.dao.TransferDao
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.model.TransferDirection
import com.zerostudio.cloudreve.core.domain.model.TransferStatus
import com.zerostudio.cloudreve.core.domain.repository.CloudreveRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getString(KEY_TASK_ID) ?: id.toString()
        val fileUri = inputData.getString(KEY_FILE_URI)?.takeIf(String::isNotBlank)
            ?: return@withContext Result.failure()
        val displayName = inputData.getString(KEY_DISPLAY_NAME).orEmpty().ifBlank { "download" }
        val totalBytes = inputData.getLong(KEY_TOTAL_BYTES, 0L).coerceAtLeast(0L)
        val dependencies = dependencies()

        runCatching {
            updateTransfer(
                dao = dependencies.transferDao,
                taskId = taskId,
                name = displayName,
                status = TransferStatus.Running,
                transferredBytes = 0L,
                totalBytes = totalBytes,
            )
            val downloadUrl = dependencies.repository.createDownloadUrl(CloudreveUri.parse(fileUri))
            enqueueSystemDownload(downloadUrl = downloadUrl, displayName = displayName)
            updateTransfer(
                dao = dependencies.transferDao,
                taskId = taskId,
                name = displayName,
                status = TransferStatus.Succeeded,
                transferredBytes = totalBytes,
                totalBytes = totalBytes,
            )
            Result.success()
        }.getOrElse { error ->
            Log.e(TAG, "Download task $taskId failed", error)
            updateTransfer(
                dao = dependencies.transferDao,
                taskId = taskId,
                name = displayName,
                status = TransferStatus.Failed,
                transferredBytes = 0L,
                totalBytes = totalBytes,
            )
            Result.failure()
        }
    }

    private fun enqueueSystemDownload(
        downloadUrl: String,
        displayName: String,
    ) {
        val manager = applicationContext.getSystemService(DownloadManager::class.java)
            ?: error("DownloadManager is unavailable")
        val targetName = resolveTargetFileName(displayName)
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle(displayName)
            .setDescription(displayName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, targetName)
        manager.enqueue(request)
    }

    private suspend fun updateTransfer(
        dao: TransferDao,
        taskId: String,
        name: String,
        status: TransferStatus,
        transferredBytes: Long,
        totalBytes: Long,
    ) {
        val current = dao.findById(taskId) ?: return
        dao.upsert(
            current.copy(
                name = name,
                direction = TransferDirection.Download.name,
                status = status.name,
                progress = if (totalBytes > 0L) {
                    (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                } else if (status == TransferStatus.Succeeded) {
                    1f
                } else {
                    0f
                },
                transferredBytes = transferredBytes.coerceAtLeast(0L),
                totalBytes = totalBytes.coerceAtLeast(0L),
            ),
        )
    }

    private fun dependencies(): DownloadWorkerDependencies {
        val koin = GlobalContext.get()
        return DownloadWorkerDependencies(
            repository = koin.get(),
            transferDao = koin.get(),
        )
    }

    private fun resolveTargetFileName(displayName: String): String {
        val cleanName = displayName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "download" }
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .apply { mkdirs() }
        val baseName = cleanName.substringBeforeLast('.', cleanName)
        val extension = cleanName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        var candidate = File(downloadsDir, cleanName)
        if (!candidate.exists()) return cleanName
        var index = 1
        while (candidate.exists()) {
            candidate = File(downloadsDir, "${baseName}_$index$extension")
            index++
        }
        return candidate.name
    }

    companion object {
        private const val TAG = "CloudreveDownload"
        const val KEY_TASK_ID = "task_id"
        const val KEY_FILE_URI = "file_uri"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_TOTAL_BYTES = "total_bytes"
    }
}

private data class DownloadWorkerDependencies(
    val repository: CloudreveRepository,
    val transferDao: TransferDao,
)
