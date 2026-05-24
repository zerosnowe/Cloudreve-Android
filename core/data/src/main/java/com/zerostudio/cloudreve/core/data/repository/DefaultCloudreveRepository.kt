package com.zerostudio.cloudreve.core.data.repository

import com.zerostudio.cloudreve.core.common.CloudreveException
import com.zerostudio.cloudreve.core.common.DispatchersProvider
import com.zerostudio.cloudreve.core.data.mapper.toDomain
import com.zerostudio.cloudreve.core.data.mapper.toDetails
import com.zerostudio.cloudreve.core.data.mapper.toEntity
import com.zerostudio.cloudreve.core.database.dao.FileDao
import com.zerostudio.cloudreve.core.database.dao.TransferDao
import com.zerostudio.cloudreve.core.domain.model.CloudreveInstance
import com.zerostudio.cloudreve.core.domain.model.CloudreveSession
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.model.FileNode
import com.zerostudio.cloudreve.core.domain.model.ShareLink
import com.zerostudio.cloudreve.core.domain.model.SignInCommand
import com.zerostudio.cloudreve.core.domain.model.TransferTask
import com.zerostudio.cloudreve.core.domain.repository.CloudreveRepository
import com.zerostudio.cloudreve.core.domain.repository.CredentialStore
import com.zerostudio.cloudreve.core.network.CloudreveApiFactory
import com.zerostudio.cloudreve.core.network.dto.BatchFileRequest
import com.zerostudio.cloudreve.core.network.dto.CreateFileRequest
import com.zerostudio.cloudreve.core.network.dto.CreateShareRequest
import com.zerostudio.cloudreve.core.network.dto.DownloadUrlRequest
import com.zerostudio.cloudreve.core.network.dto.MoveCopyFileRequest
import com.zerostudio.cloudreve.core.network.dto.RenameFileRequest
import com.zerostudio.cloudreve.core.network.dto.SignInRequest
import com.zerostudio.cloudreve.core.network.dto.unwrap
import com.zerostudio.cloudreve.core.network.dto.unwrapNullable
import com.zerostudio.cloudreve.core.network.dto.unwrapUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

class DefaultCloudreveRepository(
    private val apiFactory: Lazy<CloudreveApiFactory>,
    private val credentialStore: CredentialStore,
    private val fileDao: Lazy<FileDao>,
    private val transferDao: Lazy<TransferDao>,
    private val dispatchers: DispatchersProvider,
) : CloudreveRepository {
    private val repositoryScope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val activeSession = MutableStateFlow<Boolean?>(null)

    init {
        repositoryScope.launch {
            activeSession.value = credentialStore.loadSession() != null
        }
    }

    override fun activeSession(): Flow<Boolean> = activeSession.filterNotNull()

    override suspend fun signIn(command: SignInCommand) = withContext(dispatchers.io) {
        val response = apiFactory.value.sessionApi(command.baseUrl).signIn(
            SignInRequest(
                email = command.username,
                password = command.password,
                captchaCode = command.captchaCode,
            ),
        ).unwrap()
        val accessToken = response.token.accessToken.trim().takeIf { it.isNotBlank() }
            ?: throw CloudreveException.Unauthorized("Cloudreve sign-in did not return an access token")
        val session = CloudreveSession(
            instance = CloudreveInstance(
                id = command.baseUrl,
                name = command.baseUrl.removePrefix("https://").removePrefix("http://"),
                baseUrl = command.baseUrl,
            ),
            userId = response.user.id,
            nickname = response.user.nickname.ifBlank { response.user.email.orEmpty() },
            accessToken = accessToken,
            refreshToken = response.token.refreshToken,
            expiresAtEpochMillis = response.token.accessExpiresAtEpochMillis(),
        )
        credentialStore.saveSession(session)
        activeSession.value = true
        refreshFiles(CloudreveUri.Root)
    }

    override suspend fun signOut() = withContext(dispatchers.io) {
        val instanceId = credentialStore.loadSession()?.instance?.id
        credentialStore.clearSession()
        instanceId?.let { fileDao.value.deleteInstance(it) }
        activeSession.value = false
    }

    override fun listFiles(uri: CloudreveUri): Flow<List<FileNode>> {
        val instanceId = activeInstanceId()
        val parentUri = CloudreveUri.parse(uri.value)
        return fileDao.value.observeChildren(instanceId, parentUri.value)
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()
            .flowOn(dispatchers.io)
    }

    override fun observeCachedImages(): Flow<List<FileNode>> {
        val instanceId = activeInstanceId()
        return fileDao.value.observeImages(instanceId)
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()
            .flowOn(dispatchers.io)
    }

    override suspend fun refreshFiles(uri: CloudreveUri) = withContext(dispatchers.io) {
        authenticated {
            val instanceId = activeInstanceId()
            val parentUri = CloudreveUri.parse(uri.value)
            val api = apiFactory.value.fileApi(activeBaseUrl())
            val response = api.listFiles(parentUri.value).unwrap()
            val files = response.files.map { it.toDomain(parentUri = parentUri) }
            fileDao.value.replaceChildren(
                instanceId = instanceId,
                parentUri = parentUri.value,
                files = files.map { it.toEntity(instanceId) },
            )
        }
    }

    override suspend fun rename(uri: CloudreveUri, newName: String) = withContext(dispatchers.io) {
        authenticated {
            apiFactory.value.fileApi(activeBaseUrl()).rename(RenameFileRequest(uri.value, newName)).unwrapUnit()
            refreshFiles(uri.parent())
        }
    }

    override suspend fun createFolder(parentUri: CloudreveUri, name: String) {
        createFileSystemNode(parentUri = parentUri, name = name, type = CLOUDREVE_CREATE_TYPE_FOLDER)
    }

    override suspend fun createFile(parentUri: CloudreveUri, name: String) {
        createFileSystemNode(parentUri = parentUri, name = name, type = CLOUDREVE_CREATE_TYPE_FILE)
    }

    override suspend fun delete(uris: List<CloudreveUri>) = withContext(dispatchers.io) {
        authenticated {
            val instanceId = activeInstanceId()
            apiFactory.value.fileApi(activeBaseUrl()).delete(BatchFileRequest(uris.map { it.value })).unwrapUnit()
            fileDao.value.deleteByUris(instanceId, uris.map { it.value })
        }
    }

    override suspend fun move(uris: List<CloudreveUri>, target: CloudreveUri) = withContext(dispatchers.io) {
        authenticated {
            apiFactory.value.fileApi(activeBaseUrl()).move(MoveCopyFileRequest(uris.map { it.value }, target.value)).unwrapUnit()
            refreshFiles(target)
        }
    }

    override suspend fun copy(uris: List<CloudreveUri>, target: CloudreveUri) = withContext(dispatchers.io) {
        authenticated {
            apiFactory.value.fileApi(activeBaseUrl()).copy(MoveCopyFileRequest(uris.map { it.value }, target.value)).unwrapUnit()
            refreshFiles(target)
        }
    }

    override suspend fun createShare(uris: List<CloudreveUri>): ShareLink = withContext(dispatchers.io) {
        authenticated {
            val share = apiFactory.value.shareApi(activeBaseUrl())
                .createShare(CreateShareRequest(uris = uris.map { it.value }))
                .unwrap()
            ShareLink(
                id = share.id,
                url = share.url,
                password = share.password,
                expiresAtEpochMillis = share.expires,
            )
        }
    }

    override suspend fun createDownloadUrl(uri: CloudreveUri): String = withContext(dispatchers.io) {
        authenticated {
            val baseUrl = activeBaseUrl()
            val response = apiFactory.value.fileApi(activeBaseUrl())
                .createDownloadUrl(DownloadUrlRequest(uris = listOf(CloudreveUri.parse(uri.value).value)))
                .unwrap()
            response.urls.firstOrNull()?.url?.takeIf { it.isNotBlank() }?.let { url ->
                resolveCloudreveUrl(baseUrl = baseUrl, url = url)
            }
                ?: throw CloudreveException.Api(code = -1, message = "Cloudreve download url is empty")
        }
    }

    override suspend fun createThumbnailUrl(uri: CloudreveUri): String = withContext(dispatchers.io) {
        authenticated {
            val baseUrl = activeBaseUrl()
            val instanceId = activeInstanceId()
            val parsedUri = CloudreveUri.parse(uri.value)
            val response = apiFactory.value.fileApi(baseUrl)
                .getThumbnail(parsedUri.value)
                .unwrap()
            response.url.takeIf { it.isNotBlank() }?.let { rawUrl ->
                val url = if (response.obfuscated == true && rawUrl.shouldDecodeCloudreveTimeFlowString()) {
                    runCatching { decodeCloudreveTimeFlowString(rawUrl) }.getOrDefault(rawUrl)
                } else {
                    rawUrl
                }
                resolveCloudreveUrl(baseUrl = baseUrl, url = url).also { resolvedUrl ->
                    fileDao.value.updateThumbnailUrl(
                        instanceId = instanceId,
                        uri = parsedUri.value,
                        thumbnailUrl = resolvedUrl,
                    )
                }
            } ?: throw CloudreveException.Api(code = -1, message = "Cloudreve thumbnail url is empty")
        }
    }

    override suspend fun getFileDetails(uri: CloudreveUri) = withContext(dispatchers.io) {
        authenticated {
            apiFactory.value.fileApi(activeBaseUrl())
                .getInfo(CloudreveUri.parse(uri.value).value)
                .unwrap()
                .toDetails()
        }
    }

    override suspend fun setFavorite(uri: CloudreveUri, favorite: Boolean) = withContext(dispatchers.io) {
        val instanceId = activeInstanceId()
        fileDao.value.setFavorite(
            instanceId = instanceId,
            uri = CloudreveUri.parse(uri.value).value,
            favorite = favorite,
        )
    }

    override fun transferTasks(): Flow<List<TransferTask>> =
        transferDao.value.observeTransfers()
            .map { transfers -> transfers.map { it.toDomain() } }
            .flowOn(dispatchers.io)

    private fun activeBaseUrl(): String =
        credentialStore.loadSession()?.instance?.baseUrl ?: throw CloudreveException.MissingSession()

    private fun activeInstanceId(): String =
        credentialStore.loadSession()?.instance?.id ?: throw CloudreveException.MissingSession()

    private fun decodeCloudreveTimeFlowString(value: String): String {
        val nowMillis = System.currentTimeMillis()
        decodeTimeFlowStringOrNull(value, nowMillis)?.let { return it }
        timeFlowClockSkewMillis.forEach { offset ->
            decodeTimeFlowStringOrNull(value, nowMillis + offset)?.let { return it }
        }
        throw CloudreveException.Api(code = -1, message = "Cloudreve thumbnail url decode failed")
    }

    private fun decodeTimeFlowStringOrNull(
        value: String,
        nowMillis: Long,
    ): String? = runCatching {
        if (value.isEmpty()) return@runCatching ""
        val nowSeconds = nowMillis.floorDiv(1000L)
        val timeDigits = if (nowSeconds == 0L) {
            mutableListOf(0)
        } else {
            buildList {
                var temp = nowSeconds
                while (temp > 0L) {
                    add((temp % 10L).toInt())
                    temp /= 10L
                }
            }
        }
        val result = value.toMutableList()
        val secret = value.toMutableList()
        val originalLength = secret.size
        var add = originalLength % 2 == 0
        var timeDigitIndex = (originalLength - 1).floorMod(timeDigits.size)

        repeat(originalLength) { position ->
            val targetIndex = originalLength - 1 - position
            val timeDigit = timeDigits[timeDigitIndex]
            var newIndex = if (add) {
                targetIndex + timeDigit * timeDigitIndex
            } else {
                2 * timeDigitIndex * timeDigit - targetIndex
            }
            if (newIndex < 0) newIndex *= -1
            newIndex = newIndex.floorMod(secret.size)
            result[targetIndex] = secret[newIndex]
            val lastIndex = secret.lastIndex
            val last = secret[lastIndex]
            secret[lastIndex] = secret[newIndex]
            secret[newIndex] = last
            secret.removeAt(lastIndex)
            add = !add
            timeDigitIndex -= 1
            if (timeDigitIndex < 0) timeDigitIndex = timeDigits.lastIndex
        }

        val decoded = result.joinToString(separator = "")
        val separatorIndex = decoded.indexOf('|')
        if (separatorIndex <= 0) return@runCatching null
        val timestamp = decoded.substring(0, separatorIndex)
        if (timestamp != nowSeconds.toString()) return@runCatching null
        decoded.substring(separatorIndex + 1)
    }.getOrNull()

    private suspend fun createFileSystemNode(parentUri: CloudreveUri, name: String, type: String) =
        withContext(dispatchers.io) {
            authenticated {
                val instanceId = activeInstanceId()
                val targetUri = parentUri.child(name)
                val created = apiFactory.value.fileApi(activeBaseUrl())
                    .create(CreateFileRequest(uri = targetUri.value, type = type))
                    .unwrapNullable()

                created?.let { file ->
                    fileDao.value.upsertAll(listOf(file.toDomain(parentUri = parentUri).toEntity(instanceId)))
                }
                refreshFiles(parentUri)
            }
        }

    private suspend fun <T> authenticated(block: suspend () -> T): T =
        try {
            block()
        } catch (error: CloudreveException.Unauthorized) {
            val instanceId = credentialStore.loadSession()?.instance?.id
            credentialStore.clearSession()
            instanceId?.let { fileDao.value.deleteInstance(it) }
            activeSession.value = false
            throw error
        }

    private fun resolveCloudreveUrl(baseUrl: String, url: String): String {
        val trimmed = url.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        return runCatching {
            val normalizedBase = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
            URI(normalizedBase).resolve(trimmed).toString()
        }.getOrDefault(trimmed)
    }

    private fun String.shouldDecodeCloudreveTimeFlowString(): Boolean {
        val trimmed = trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return false
        }
        val path = trimmed.substringBefore('?').substringBefore('#')
        if (path.contains("/api/", ignoreCase = true) || path.contains("file/content", ignoreCase = true)) {
            return false
        }
        return true
    }

    private companion object {
        const val CLOUDREVE_CREATE_TYPE_FILE = "file"
        const val CLOUDREVE_CREATE_TYPE_FOLDER = "folder"
        val timeFlowClockSkewMillis = longArrayOf(-2_000L, -1_000L, 1_000L, 2_000L)
    }
}

private fun Int.floorMod(modulus: Int): Int = Math.floorMod(this, modulus)
