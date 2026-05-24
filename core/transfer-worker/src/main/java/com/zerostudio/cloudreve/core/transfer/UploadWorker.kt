package com.zerostudio.cloudreve.core.transfer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zerostudio.cloudreve.core.database.dao.TransferDao
import com.zerostudio.cloudreve.core.database.entity.TransferEntity
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.model.TransferDirection
import com.zerostudio.cloudreve.core.domain.model.TransferStatus
import com.zerostudio.cloudreve.core.domain.repository.CloudreveRepository
import com.zerostudio.cloudreve.core.domain.repository.CredentialStore
import com.zerostudio.cloudreve.core.network.CloudreveApiFactory
import com.zerostudio.cloudreve.core.network.api.CloudreveFileApi
import com.zerostudio.cloudreve.core.network.api.CloudreveUploadCallbackApi
import com.zerostudio.cloudreve.core.network.api.CloudreveUserApi
import com.zerostudio.cloudreve.core.network.dto.CreateFileRequest
import com.zerostudio.cloudreve.core.network.dto.DeleteUploadSessionRequest
import com.zerostudio.cloudreve.core.network.dto.UploadSessionRequest
import com.zerostudio.cloudreve.core.network.dto.UploadSessionResponse
import com.zerostudio.cloudreve.core.network.dto.UploadStoragePolicyDto
import com.zerostudio.cloudreve.core.network.dto.unwrap
import com.zerostudio.cloudreve.core.network.dto.unwrapNullable
import com.zerostudio.cloudreve.core.network.dto.unwrapUnit
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.context.GlobalContext

class UploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val contentResolver = appContext.contentResolver

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getString(KEY_TASK_ID) ?: id.toString()
        val sourceUri = inputData.getString(KEY_SOURCE_URI)?.let(Uri::parse) ?: return@withContext Result.failure()
        val targetUri = inputData.getString(KEY_TARGET_URI)?.let(CloudreveUri::parse) ?: return@withContext Result.failure()
        val displayName = inputData.getString(KEY_DISPLAY_NAME).orEmpty().ifBlank { "upload" }
        val initialTotalBytes = inputData.getLong(KEY_TOTAL_BYTES, 0L).coerceAtLeast(0L)
        val directory = inputData.getBoolean(KEY_IS_DIRECTORY, false)
        val dependencies = dependencies()

        runCatching {
            updateTransfer(
                dao = dependencies.transferDao,
                taskId = taskId,
                name = displayName,
                status = TransferStatus.Running,
                transferredBytes = 0L,
                totalBytes = initialTotalBytes,
            )

            val baseUrl = dependencies.credentialStore.loadSession()?.instance?.baseUrl
                ?: error("Missing Cloudreve session")
            val fileApi = dependencies.apiFactory.fileApi(baseUrl)
            val userApi = dependencies.apiFactory.userApi(baseUrl)
            val callbackApi = dependencies.apiFactory.uploadCallbackApi(baseUrl)
            val uploadClient = dependencies.client.directUploadClient()

            val completedBytes = if (directory) {
                uploadDirectory(
                    fileApi = fileApi,
                    userApi = userApi,
                    callbackApi = callbackApi,
                    uploadClient = uploadClient,
                    baseUrl = baseUrl,
                    rootTreeUri = sourceUri,
                    destinationParent = targetUri,
                    taskId = taskId,
                    taskName = displayName,
                    transferDao = dependencies.transferDao,
                )
            } else {
                val mime = contentResolver.getType(sourceUri)
                val policyId = selectUploadPolicyId(userApi, fileApi, targetUri, initialTotalBytes)
                uploadSingleFile(
                    fileApi = fileApi,
                    callbackApi = callbackApi,
                    uploadClient = uploadClient,
                    policyId = policyId,
                    descriptor = UploadFileDescriptor(
                        sourceUri = sourceUri,
                        remoteUri = targetUri.child(displayName),
                        fileSize = initialTotalBytes,
                        mimeType = mime,
                        lastModified = sourceUri.lastModifiedOrNull(),
                        displayName = displayName,
                    ),
                    taskId = taskId,
                    taskName = displayName,
                    transferDao = dependencies.transferDao,
                    uploadedBefore = 0L,
                    totalBytes = initialTotalBytes,
                )
                initialTotalBytes
            }

            updateTransfer(
                dao = dependencies.transferDao,
                taskId = taskId,
                name = displayName,
                status = TransferStatus.Succeeded,
                transferredBytes = completedBytes,
                totalBytes = completedBytes,
            )
            runCatching {
                dependencies.repository.refreshFiles(targetUri)
            }
            Result.success()
        }.getOrElse { error ->
            Log.e(TAG, "Upload task $taskId failed", error)
            val canRetry = runAttemptCount < MAX_UPLOAD_RETRY_COUNT - 1 && error.shouldRetryUpload()
            val currentProgress = dependencies.transferDao.findById(taskId)
            updateTransfer(
                dao = dependencies.transferDao,
                taskId = taskId,
                name = displayName,
                status = if (canRetry) TransferStatus.Queued else TransferStatus.Failed,
                transferredBytes = currentProgress?.transferredBytes ?: 0L,
                totalBytes = currentProgress?.totalBytes ?: initialTotalBytes,
            )
            if (canRetry) Result.retry() else Result.failure()
        }
    }

    private suspend fun uploadDirectory(
        fileApi: CloudreveFileApi,
        userApi: CloudreveUserApi,
        callbackApi: CloudreveUploadCallbackApi,
        uploadClient: OkHttpClient,
        baseUrl: String,
        rootTreeUri: Uri,
        destinationParent: CloudreveUri,
        taskId: String,
        taskName: String,
        transferDao: TransferDao,
    ): Long {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(rootTreeUri)
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, rootDocumentId)
        val remoteRoot = destinationParent.child(taskName)
        val entries = scanDocumentTree(rootTreeUri, rootDocumentUri, remoteRoot)
        val totalBytes = entries.filterNot { it.directory }.sumOf { it.size }.coerceAtLeast(0L)
        val policyId = selectUploadPolicyId(userApi, fileApi, destinationParent, totalBytes)
        var uploadedBytes = 0L

        updateTransfer(transferDao, taskId, taskName, TransferStatus.Running, 0L, totalBytes)

        entries.asSequence()
            .filter { it.directory }
            .forEach { entry ->
                fileApi.create(
                    CreateFileRequest(
                        uri = entry.targetUri.value,
                        type = CLOUDREVE_CREATE_TYPE_FOLDER,
                        errOnConflict = false,
                    ),
                ).unwrapNullable()
            }

        entries.asSequence()
            .filterNot { it.directory }
            .forEach { entry ->
                runBlocking {
                    uploadSingleFile(
                        fileApi = fileApi,
                        callbackApi = callbackApi,
                        uploadClient = uploadClient,
                        policyId = policyId,
                        descriptor = UploadFileDescriptor(
                            sourceUri = entry.sourceUri,
                            remoteUri = entry.targetUri,
                            fileSize = entry.size,
                            mimeType = entry.mime,
                            lastModified = entry.sourceUri.lastModifiedOrNull(),
                            displayName = entry.targetUri.value.substringAfterLast('/'),
                        ),
                        taskId = taskId,
                        taskName = taskName,
                        transferDao = transferDao,
                        uploadedBefore = uploadedBytes,
                        totalBytes = totalBytes,
                    )
                }
                uploadedBytes += entry.size
                runBlocking {
                    updateTransfer(
                        dao = transferDao,
                        taskId = taskId,
                        name = taskName,
                        status = TransferStatus.Running,
                        transferredBytes = uploadedBytes,
                        totalBytes = totalBytes,
                    )
                }
            }

        return totalBytes
    }

    private suspend fun uploadSingleFile(
        fileApi: CloudreveFileApi,
        callbackApi: CloudreveUploadCallbackApi,
        uploadClient: OkHttpClient,
        policyId: String?,
        descriptor: UploadFileDescriptor,
        taskId: String,
        taskName: String,
        transferDao: TransferDao,
        uploadedBefore: Long,
        totalBytes: Long,
    ) {
        val session = createUploadSession(fileApi, descriptor, policyId)
        try {
            val etags = uploadFileChunks(
                fileApi = fileApi,
                uploadClient = uploadClient,
                session = session,
                descriptor = descriptor,
                taskId = taskId,
                taskName = taskName,
                transferDao = transferDao,
                uploadedBefore = uploadedBefore,
                totalBytes = totalBytes.takeIf { it > 0L } ?: descriptor.fileSize,
            )
            completeUpload(
                callbackApi = callbackApi,
                uploadClient = uploadClient,
                session = session,
                etags = etags,
            )
        } catch (error: Throwable) {
            deleteUploadSessionQuietly(fileApi, session)
            throw error
        }
    }

    private suspend fun createUploadSession(
        fileApi: CloudreveFileApi,
        descriptor: UploadFileDescriptor,
        policyId: String?,
    ): UploadSessionState {
        Log.d(
            TAG,
            "Create upload session uri=${descriptor.remoteUri.value} size=${descriptor.fileSize} policy=$policyId",
        )
        val response = fileApi.createUploadSession(
            UploadSessionRequest(
                uri = descriptor.remoteUri.value,
                size = descriptor.fileSize,
                policyId = policyId,
                lastModified = descriptor.lastModified,
                mimeType = descriptor.mimeType,
            ),
        ).unwrap()
        val sessionId = response.sessionId.ifBlank { response.uploadId }
        require(sessionId.isNotBlank()) { "Cloudreve upload session is empty" }
        val state = response.toSessionState(
            sessionId = sessionId,
            targetUri = descriptor.remoteUri,
            fileSize = descriptor.fileSize,
        )
        Log.d(
            TAG,
            "Upload session ready for ${descriptor.remoteUri.value}: policy=${state.policyType.value} relay=${state.relay} chunks=${state.chunkCount}",
        )
        return state
    }

    private suspend fun selectUploadPolicyId(
        userApi: CloudreveUserApi,
        fileApi: CloudreveFileApi,
        targetParent: CloudreveUri,
        fileSize: Long,
    ): String? {
        val policies = runCatching {
            userApi.listStoragePolicies().unwrap()
                .filter { it.id.isNotBlank() }
        }.onFailure { error ->
            Log.w(TAG, "Upload policy list unavailable, fallback to folder info", error)
        }.getOrNull().orEmpty()
        val policy = policies.firstOrNull { policy ->
            val maxSize = policy.maxSize ?: 0L
            maxSize <= 0L || fileSize <= maxSize
        } ?: policies.firstOrNull()

        if (policy != null) {
            Log.d(
                TAG,
                "Upload policy selected id=${policy.id} type=${policy.typeValue()} relay=${policy.relay} maxSize=${policy.maxSize ?: 0L}",
            )
            return policy.id
        }

        val listPolicy = runCatching {
            fileApi.listFiles(targetParent.value, pageSize = 1)
                .unwrap()
                .storagePolicy
        }.onFailure { error ->
            Log.w(TAG, "Unable to resolve upload policy from file list ${targetParent.value}", error)
        }.getOrNull()

        if (listPolicy != null && listPolicy.id.isNotBlank()) {
            Log.d(
                TAG,
                "Upload policy selected from file list id=${listPolicy.id} type=${listPolicy.typeValue()} relay=${listPolicy.relay}",
            )
            return listPolicy.id
        }

        val folderPolicyId = runCatching {
            fileApi.getInfo(targetParent.value, extended = true, folderSummary = false)
                .unwrap()
                .extendedInfo
                ?.storagePolicyId()
        }.onFailure { error ->
            Log.w(TAG, "Unable to resolve upload policy from folder info ${targetParent.value}", error)
        }.getOrNull()

        if (!folderPolicyId.isNullOrBlank()) {
            Log.d(TAG, "Upload policy selected from folder info id=$folderPolicyId")
            return folderPolicyId
        }

        Log.w(TAG, "Upload policy id is unavailable; creating upload session without policy_id")
        return null
    }

    private suspend fun uploadFileChunks(
        fileApi: CloudreveFileApi,
        uploadClient: OkHttpClient,
        session: UploadSessionState,
        descriptor: UploadFileDescriptor,
        taskId: String,
        taskName: String,
        transferDao: TransferDao,
        uploadedBefore: Long,
        totalBytes: Long,
    ): Map<Int, String> {
        require(!(session.policyType == UploadPolicyType.OneDrive && descriptor.fileSize == 0L)) {
            "OneDrive does not support empty file uploads"
        }
        val etags = linkedMapOf<Int, String>()

        if (session.policyType == UploadPolicyType.Upyun) {
            val body = ContentUriRequestBody(
                context = applicationContext,
                uri = descriptor.sourceUri,
                mime = descriptor.mimeType,
                offset = 0L,
                length = descriptor.fileSize,
            ) { uploadedChunkBytes ->
                val currentUploaded = uploadedBefore + uploadedChunkBytes
                updateTransfer(
                    dao = transferDao,
                    taskId = taskId,
                    name = taskName,
                    status = TransferStatus.Running,
                    transferredBytes = currentUploaded,
                    totalBytes = totalBytes,
                )
            }
            uploadToUpyun(
                client = uploadClient,
                session = session,
                descriptor = descriptor,
                body = body,
            )
            return etags
        }

        repeat(session.chunkCount) { chunkIndex ->
            val offset = chunkIndex * session.chunkSize
            val chunkLength = minOf(session.chunkSize, (descriptor.fileSize - offset).coerceAtLeast(0L))
            val body = ContentUriRequestBody(
                context = applicationContext,
                uri = descriptor.sourceUri,
                mime = descriptor.mimeType,
                offset = offset,
                length = chunkLength,
            ) { uploadedChunkBytes ->
                val currentUploaded = uploadedBefore + offset + uploadedChunkBytes
                updateTransfer(
                    dao = transferDao,
                    taskId = taskId,
                    name = taskName,
                    status = TransferStatus.Running,
                    transferredBytes = currentUploaded,
                    totalBytes = totalBytes,
                )
            }

            when {
                session.useCloudreveChunkApi -> {
                    fileApi.uploadChunk(session.sessionId, chunkIndex, body).unwrapUnit()
                }

                session.policyType == UploadPolicyType.Remote -> {
                    uploadToSlave(
                        client = uploadClient,
                        session = session,
                        chunkIndex = chunkIndex,
                        body = body,
                    )
                }

                session.policyType == UploadPolicyType.OneDrive -> {
                    uploadToOneDrive(
                        client = uploadClient,
                        session = session,
                        body = body,
                        offset = offset,
                    )
                }

                session.policyType == UploadPolicyType.Qiniu -> {
                    val etag = uploadToQiniu(
                        client = uploadClient,
                        session = session,
                        chunkIndex = chunkIndex,
                        body = body,
                    )
                    etags[chunkIndex] = etag
                }

                session.policyType.isS3Like -> {
                    val etag = uploadToS3Like(
                        client = uploadClient,
                        session = session,
                        chunkIndex = chunkIndex,
                        body = body,
                    )
                    if (!etag.isNullOrBlank()) {
                        etags[chunkIndex] = etag
                    }
                }

                else -> {
                    fileApi.uploadChunk(session.sessionId, chunkIndex, body).unwrapUnit()
                }
            }
        }

        return etags
    }

    private suspend fun completeUpload(
        callbackApi: CloudreveUploadCallbackApi,
        uploadClient: OkHttpClient,
        session: UploadSessionState,
        etags: Map<Int, String>,
    ) {
        when (session.policyType) {
            UploadPolicyType.Local,
            UploadPolicyType.Remote,
            UploadPolicyType.Upyun,
            -> Unit

            UploadPolicyType.OneDrive -> {
                require(session.callbackSecret.isNotBlank()) { "Cloudreve OneDrive callback secret is empty" }
                callbackApi.completeOnedriveUpload(session.sessionId, session.callbackSecret).unwrapUnit()
            }

            UploadPolicyType.Oss -> {
                completeOssUpload(uploadClient, session)
            }

            UploadPolicyType.S3,
            UploadPolicyType.Ks3,
            UploadPolicyType.Cos,
            -> {
                completeMultipartUpload(uploadClient, session, etags)
                require(session.callbackSecret.isNotBlank()) { "Cloudreve S3 callback secret is empty" }
                if (session.policyType == UploadPolicyType.Cos) {
                    callbackApi.completeCosUpload(session.sessionId, session.callbackSecret).unwrapUnit()
                } else {
                    callbackApi.completeS3Upload(session.sessionId, session.callbackSecret).unwrapUnit()
                }
            }

            UploadPolicyType.Obs -> {
                completeMultipartUpload(uploadClient, session, etags)
                require(session.callbackSecret.isNotBlank()) { "Cloudreve OBS callback secret is empty" }
                callbackApi.completeObsUpload(session.sessionId, session.callbackSecret).unwrapUnit()
            }

            UploadPolicyType.Qiniu -> {
                completeQiniuUpload(uploadClient, session, etags)
            }
        }
    }

    private suspend fun deleteUploadSessionQuietly(
        fileApi: CloudreveFileApi,
        session: UploadSessionState,
    ) {
        runCatching {
            fileApi.deleteUploadSession(
                DeleteUploadSessionRequest(
                    id = session.sessionId,
                    uri = session.targetUri.value,
                ),
            ).unwrapUnit()
        }.onFailure { error ->
            Log.w(TAG, "Failed to delete upload session ${session.sessionId}", error)
        }
    }

    private fun scanDocumentTree(
        treeUri: Uri,
        documentUri: Uri,
        targetUri: CloudreveUri,
    ): List<DocumentUploadEntry> {
        val documentId = DocumentsContract.getDocumentId(documentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val entries = mutableListOf<DocumentUploadEntry>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val childId = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex).orEmpty().ifBlank { "Untitled" }
                val mime = cursor.getString(mimeIndex)
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                val childDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                val childTargetUri = targetUri.child(name)
                val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                entries += DocumentUploadEntry(
                    sourceUri = childDocumentUri,
                    targetUri = childTargetUri,
                    size = if (isDirectory) 0L else size.takeIf { it > 0L } ?: childDocumentUri.contentLengthOrZero(),
                    mime = mime,
                    directory = isDirectory,
                )
                if (isDirectory) {
                    entries += scanDocumentTree(treeUri, childDocumentUri, childTargetUri)
                }
            }
        }
        return listOf(
            DocumentUploadEntry(
                sourceUri = documentUri,
                targetUri = targetUri,
                size = 0L,
                mime = DocumentsContract.Document.MIME_TYPE_DIR,
                directory = true,
            ),
        ) + entries
    }

    private fun uploadToSlave(
        client: OkHttpClient,
        session: UploadSessionState,
        chunkIndex: Int,
        body: RequestBody,
    ) {
        val uploadUrl = session.firstUploadUrl()?.withChunkQuery(chunkIndex)
            ?: error("Cloudreve remote upload URL is empty")
        val request = Request.Builder()
            .url(uploadUrl)
            .post(body)
            .header("Content-Type", body.contentType()?.toString().orEmpty().ifBlank { OCTET_STREAM })
            .header("Content-Length", body.contentLength().toString())
            .apply {
                session.credential.takeIf(String::isNotBlank)?.let { header("Authorization", it) }
            }
            .build()
        client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Slave upload chunk failed: HTTP ${response.code}${bodyText.errorSuffix()}")
            }
            parseCloudreveLikeResponse(bodyText)?.throwIfError("Slave upload error")
        }
    }

    private fun uploadToOneDrive(
        client: OkHttpClient,
        session: UploadSessionState,
        body: RequestBody,
        offset: Long,
    ) {
        val uploadUrl = session.firstUploadUrl() ?: error("Cloudreve OneDrive upload URL is empty")
        val chunkLength = body.contentLength()
        val endInclusive = (offset + chunkLength - 1L).coerceAtLeast(offset)
        val request = Request.Builder()
            .url(uploadUrl)
            .put(body)
            .header("Content-Type", OCTET_STREAM)
            .header("Content-Length", chunkLength.toString())
            .header("Content-Range", "bytes $offset-$endInclusive/${session.fileSize}")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code !in ONE_DRIVE_SUCCESS_CODES) {
                val bodyText = response.body?.string().orEmpty()
                error("OneDrive upload chunk failed: HTTP ${response.code}${bodyText.errorSuffix()}")
            }
        }
    }

    private fun uploadToS3Like(
        client: OkHttpClient,
        session: UploadSessionState,
        chunkIndex: Int,
        body: RequestBody,
    ): String? {
        val uploadUrl = session.uploadUrlForChunk(chunkIndex)
            ?: error("Cloudreve multipart upload URL is empty for chunk $chunkIndex")
        val request = Request.Builder()
            .url(uploadUrl)
            .put(body)
            .header("Content-Type", OCTET_STREAM)
            .header("Content-Length", body.contentLength().toString())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val bodyText = response.body?.string().orEmpty()
                error("Multipart upload chunk failed: HTTP ${response.code}${bodyText.errorSuffix()}")
            }
            return response.header("ETag")?.trim('"')
        }
    }

    private fun uploadToQiniu(
        client: OkHttpClient,
        session: UploadSessionState,
        chunkIndex: Int,
        body: RequestBody,
    ): String {
        val baseUploadUrl = session.firstUploadUrl() ?: error("Cloudreve Qiniu upload URL is empty")
        val request = Request.Builder()
            .url("$baseUploadUrl/${chunkIndex + 1}")
            .put(body)
            .header("Content-Type", OCTET_STREAM)
            .header("Content-Length", body.contentLength().toString())
            .header("Authorization", "UpToken ${session.credential}")
            .build()
        client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = parseJsonString(bodyText, "error") ?: bodyText
                error("Qiniu upload chunk failed: HTTP ${response.code}${message.errorSuffix()}")
            }
            return parseJsonString(bodyText, "etag")
                ?.takeIf(String::isNotBlank)
                ?: error("Qiniu upload chunk ETag is empty")
        }
    }

    private fun uploadToUpyun(
        client: OkHttpClient,
        session: UploadSessionState,
        descriptor: UploadFileDescriptor,
        body: RequestBody,
    ) {
        val uploadUrl = session.firstUploadUrl() ?: error("Cloudreve Upyun upload URL is empty")
        val uploadPolicy = session.uploadPolicy?.takeIf { it.isNotBlank() }
            ?: error("Cloudreve Upyun upload policy is empty")
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("policy", uploadPolicy)
            .addFormDataPart("authorization", session.credential)
            .apply {
                descriptor.mimeType?.takeIf(String::isNotBlank)?.let { addFormDataPart("content-type", it) }
                addFormDataPart(
                    "file",
                    descriptor.displayName,
                    body,
                )
            }
            .build()
        val request = Request.Builder()
            .url(uploadUrl)
            .post(requestBody)
            .build()
        client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = parseJsonString(bodyText, "message") ?: bodyText
                error("Upyun upload failed: HTTP ${response.code}${message.errorSuffix()}")
            }
        }
    }

    private fun completeMultipartUpload(
        client: OkHttpClient,
        session: UploadSessionState,
        etags: Map<Int, String>,
    ) {
        require(etags.isNotEmpty()) { "Multipart upload ETags are empty" }
        val completeUrl = session.completeUrl?.takeIf(String::isNotBlank)
            ?: error("Cloudreve multipart upload complete URL is empty")
        val request = Request.Builder()
            .url(completeUrl)
            .post(buildMultipartCompletionBody(etags))
            .apply {
                if (session.policyType == UploadPolicyType.Cos) {
                    header("x-cos-forbid-overwrite", "true")
                }
            }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val bodyText = response.body?.string().orEmpty()
                error("Multipart completion failed: HTTP ${response.code}${bodyText.errorSuffix()}")
            }
        }
    }

    private fun completeOssUpload(
        client: OkHttpClient,
        session: UploadSessionState,
    ) {
        val completeUrl = session.completeUrl?.takeIf(String::isNotBlank)
            ?: error("Cloudreve OSS complete URL is empty")
        val request = Request.Builder()
            .url(completeUrl)
            .post(ByteArray(0).toRequestBody("application/octet-stream".toMediaType()))
            .header("Content-Length", "0")
            .header("x-oss-forbid-overwrite", "true")
            .header("x-oss-complete-all", "yes")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val bodyText = response.body?.string().orEmpty()
                error("OSS completion failed: HTTP ${response.code}${bodyText.errorSuffix()}")
            }
        }
    }

    private fun completeQiniuUpload(
        client: OkHttpClient,
        session: UploadSessionState,
        etags: Map<Int, String>,
    ) {
        require(etags.isNotEmpty()) { "Qiniu upload parts are empty" }
        val completeUrl = session.firstUploadUrl() ?: error("Cloudreve Qiniu complete URL is empty")
        val parts = JSONArray().apply {
            etags.toSortedMap().forEach { (chunkIndex, etag) ->
                put(
                    JSONObject()
                        .put("etag", etag)
                        .put("partNumber", chunkIndex + 1),
                )
            }
        }
        val payload = JSONObject()
            .put("parts", parts)
            .apply {
                session.mimeType?.takeIf(String::isNotBlank)?.let { put("mimeType", it) }
            }
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(completeUrl)
            .post(payload)
            .header("Authorization", "UpToken ${session.credential}")
            .build()
        client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = parseJsonString(bodyText, "error") ?: bodyText
                error("Qiniu completion failed: HTTP ${response.code}${message.errorSuffix()}")
            }
        }
    }

    private fun buildMultipartCompletionBody(etags: Map<Int, String>): RequestBody {
        val xml = buildString {
            append("<CompleteMultipartUpload>")
            etags.toSortedMap().forEach { (chunkIndex, etag) ->
                append("<Part>")
                append("<PartNumber>").append(chunkIndex + 1).append("</PartNumber>")
                append("<ETag>").append(etag.xmlEscaped()).append("</ETag>")
                append("</Part>")
            }
            append("</CompleteMultipartUpload>")
        }
        return xml.toRequestBody("application/xml".toMediaType())
    }

    private fun Uri.contentLengthOrZero(): Long {
        runCatching {
            contentResolver.query(this, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        val size = cursor.getLong(sizeIndex)
                        if (size > 0L) return size
                    }
                }
            }
        }
        runCatching {
            contentResolver.openAssetFileDescriptor(this, "r")?.use { descriptor ->
                if (descriptor.length > 0L) return descriptor.length
            }
        }
        runCatching {
            contentResolver.openFileDescriptor(this, "r")?.use { descriptor ->
                if (descriptor.statSize > 0L) return descriptor.statSize
            }
        }
        return 0L
    }

    private fun Uri.lastModifiedOrNull(): Long? {
        runCatching {
            val projection = arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            contentResolver.query(this, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (index >= 0 && !cursor.isNull(index)) {
                        return cursor.getLong(index).takeIf { it > 0L }
                    }
                }
            }
        }
        return null
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
                direction = TransferDirection.Upload.name,
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

    private fun dependencies(): UploadWorkerDependencies {
        val koin = GlobalContext.get()
        return UploadWorkerDependencies(
            apiFactory = koin.get(),
            client = koin.get(),
            credentialStore = koin.get(),
            transferDao = koin.get(),
            repository = koin.get(),
        )
    }

    companion object {
        private const val TAG = "CloudreveUpload"
        private const val MAX_UPLOAD_RETRY_COUNT = 3
        private const val CLOUDREVE_CREATE_TYPE_FOLDER = "folder"
        private const val OCTET_STREAM = "application/octet-stream"
        private val ONE_DRIVE_SUCCESS_CODES = setOf(200, 201, 202)
        const val KEY_TASK_ID = "task_id"
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_TARGET_URI = "target_uri"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_IS_DIRECTORY = "is_directory"
    }
}

private data class UploadWorkerDependencies(
    val apiFactory: CloudreveApiFactory,
    val client: OkHttpClient,
    val credentialStore: CredentialStore,
    val transferDao: TransferDao,
    val repository: CloudreveRepository,
)

private data class UploadFileDescriptor(
    val sourceUri: Uri,
    val remoteUri: CloudreveUri,
    val fileSize: Long,
    val mimeType: String?,
    val lastModified: Long?,
    val displayName: String,
)

private data class DocumentUploadEntry(
    val sourceUri: Uri,
    val targetUri: CloudreveUri,
    val size: Long,
    val mime: String?,
    val directory: Boolean,
)

private data class UploadSessionState(
    val sessionId: String,
    val targetUri: CloudreveUri,
    val fileSize: Long,
    val chunkSize: Long,
    val chunkCount: Int,
    val uploadUrls: List<String>,
    val credential: String,
    val uploadPolicy: String?,
    val mimeType: String?,
    val completeUrl: String?,
    val callbackSecret: String,
    val policyType: UploadPolicyType,
    val relay: Boolean,
) {
    val useCloudreveChunkApi: Boolean
        get() = relay || policyType == UploadPolicyType.Local || uploadUrls.isEmpty()

    fun firstUploadUrl(): String? = uploadUrls.firstOrNull()

    fun uploadUrlForChunk(chunkIndex: Int): String? = when {
        uploadUrls.isEmpty() -> null
        policyType.usesSingleUploadUrl -> uploadUrls.firstOrNull()
        else -> uploadUrls.getOrNull(chunkIndex) ?: uploadUrls.firstOrNull()
    }
}

private enum class UploadPolicyType(val value: String) {
    Local("local"),
    Remote("remote"),
    Oss("oss"),
    Qiniu("qiniu"),
    OneDrive("onedrive"),
    Cos("cos"),
    Upyun("upyun"),
    S3("s3"),
    Ks3("ks3"),
    Obs("obs");

    val isS3Like: Boolean
        get() = this == Oss || this == Cos || this == S3 || this == Ks3 || this == Obs

    val usesSingleUploadUrl: Boolean
        get() = this == Remote || this == OneDrive || this == Qiniu || this == Upyun

    companion object {
        fun from(value: String): UploadPolicyType = when (value.lowercase()) {
            "remote" -> Remote
            "oss" -> Oss
            "qiniu" -> Qiniu
            "onedrive" -> OneDrive
            "cos" -> Cos
            "upyun" -> Upyun
            "s3" -> S3
            "ks3" -> Ks3
            "obs" -> Obs
            else -> Local
        }
    }
}

private data class CloudreveLikeResponse(
    val code: Int,
    val message: String,
) {
    fun throwIfError(prefix: String) {
        if (code == 0) return
        error("$prefix ($code): ${message.ifBlank { "Cloudreve API error" }}")
    }
}

private fun UploadSessionResponse.toSessionState(
    sessionId: String,
    targetUri: CloudreveUri,
    fileSize: Long,
): UploadSessionState {
    val chunkSizeValue = chunkSize.takeIf { it > 0L } ?: fileSize.coerceAtLeast(1L)
    val normalizedUrls = uploadUrls.map { it.trim() }.filter { it.isNotBlank() }
    val policyValue = storagePolicy?.typeValue().orEmpty()
    val policyType = UploadPolicyType.from(policyValue)
    val safeSize = fileSize.coerceAtLeast(0L)
    val chunkCount = if (policyType == UploadPolicyType.Upyun) {
        1
    } else if (safeSize == 0L) {
        1
    } else {
        ((safeSize + chunkSizeValue - 1L) / chunkSizeValue).coerceAtLeast(1L).toInt()
    }
    return UploadSessionState(
        sessionId = sessionId,
        targetUri = targetUri,
        fileSize = safeSize,
        chunkSize = chunkSizeValue,
        chunkCount = chunkCount,
        uploadUrls = normalizedUrls,
        credential = credential.orEmpty(),
        uploadPolicy = uploadPolicy,
        mimeType = mimeType,
        completeUrl = completeUrl?.trim()?.takeIf(String::isNotBlank),
        callbackSecret = callbackSecret,
        policyType = policyType,
        relay = storagePolicy?.relay == true,
    )
}

private fun UploadStoragePolicyDto.typeValue(): String =
    type?.toString()?.trim('"')?.lowercase().orEmpty()

private fun com.zerostudio.cloudreve.core.network.dto.FileExtendedInfoDto.storagePolicyId(): String? =
    when (val policy = storagePolicy) {
        is JsonPrimitive -> policy.contentOrNull
        is JsonObject -> policy["id"]?.jsonPrimitive?.contentOrNull
            ?: policy["policy_id"]?.jsonPrimitive?.contentOrNull
        else -> null
    }?.takeIf { it.isNotBlank() }

private fun OkHttpClient.directUploadClient(): OkHttpClient =
    newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .build()

private class ContentUriRequestBody(
    private val context: Context,
    private val uri: Uri,
    private val mime: String?,
    private val offset: Long,
    private val length: Long,
    private val onProgress: suspend (Long) -> Unit,
) : RequestBody() {
    override fun contentType() = mime?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()

    override fun contentLength(): Long = length

    override fun writeTo(sink: BufferedSink) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.skipFully(offset)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = length
            var written = 0L
            var lastReported = -1L
            while (remaining > 0L) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read == -1) break
                sink.write(buffer, 0, read)
                written += read
                remaining -= read
                if (written == length || written - lastReported >= PROGRESS_REPORT_STEP_BYTES) {
                    lastReported = written
                    runBlocking { onProgress(written) }
                }
            }
            if (written == 0L && length == 0L) {
                runBlocking { onProgress(0L) }
            }
        } ?: error("Unable to open $uri")
    }

    companion object {
        private const val PROGRESS_REPORT_STEP_BYTES = 128L * 1024L
    }
}

private fun Throwable.shouldRetryUpload(): Boolean =
    this is IOException || cause is IOException

private fun InputStream.skipFully(bytes: Long) {
    var remaining = bytes
    while (remaining > 0L) {
        val skipped = skip(remaining)
        if (skipped <= 0L) {
            if (read() == -1) error("Unable to skip input stream")
            remaining--
        } else {
            remaining -= skipped
        }
    }
}

private fun parseCloudreveLikeResponse(body: String): CloudreveLikeResponse? {
    val trimmed = body.trim()
    if (!trimmed.startsWith("{")) return null
    return runCatching {
        val json = JSONObject(trimmed)
        if (!json.has("code")) return@runCatching null
        CloudreveLikeResponse(
            code = json.optInt("code", 0),
            message = json.optString("msg").ifBlank { json.optString("message") },
        )
    }.getOrNull()
}

private fun parseJsonString(body: String, key: String): String? = runCatching {
    if (!body.trim().startsWith("{")) return@runCatching null
    JSONObject(body).optString(key).takeIf { it.isNotBlank() }
}.getOrNull()

private fun String.errorSuffix(): String = if (isBlank()) "" else " ${take(512)}"

private fun String.withChunkQuery(chunkIndex: Int): String {
    val builder = toHttpUrl().newBuilder()
    builder.setQueryParameter("chunk", chunkIndex.toString())
    return builder.build().toString()
}

private fun String.xmlEscaped(): String = buildString(length) {
    for (char in this@xmlEscaped) {
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(char)
        }
    }
}

@Suppress("unused")
private fun String.urlEncodePathSegment(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")

@Suppress("unused")
private fun resolveHttpUrl(baseUrl: String?, url: String): String {
    val trimmed = url.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        return trimmed
    }
    val normalizedBase = baseUrl?.trim()?.trimEnd('/')?.let { "$it/" }
    return normalizedBase?.let { runCatching { URI(it).resolve(trimmed).toString() }.getOrDefault(trimmed) } ?: trimmed
}
