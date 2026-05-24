package com.zerostudio.cloudreve.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class FileListResponse(
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("objects")
    val files: List<FileDto> = emptyList(),
    val parent: FileDto? = null,
    @SerialName("storage_policy") val storagePolicy: UploadStoragePolicyDto? = null,
    val pagination: PaginationDto? = null,
)

@Serializable
data class FileDto(
    val id: String = "",
    val name: String = "",
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("uri")
    val path: String = "",
    val type: Int = 0,
    val size: Long = 0,
    val mime: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val metadata: JsonObject? = null,
)

@Serializable
data class FileInfoDto(
    val id: String = "",
    val name: String = "",
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("uri")
    val path: String = "",
    val type: Int = 0,
    val size: Long = 0,
    val mime: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val metadata: JsonObject? = null,
    val owned: Boolean? = null,
    @SerialName("primary_entity") val primaryEntity: String? = null,
    @SerialName("extended_info") val extendedInfo: FileExtendedInfoDto? = null,
)

@Serializable
data class FileExtendedInfoDto(
    @SerialName("storage_policy") val storagePolicy: JsonElement? = null,
    @SerialName("storage_policy_name") val storagePolicyName: String? = null,
    @SerialName("storage_used") val storageUsed: Long? = null,
)

@Serializable
data class DownloadUrlRequest(
    val uris: List<String>,
    val download: Boolean = false,
    val redirect: Boolean = false,
    val archive: Boolean = false,
    @SerialName("no_cache") val noCache: Boolean = false,
    @SerialName("use_primary_site_url") val usePrimarySiteUrl: Boolean = true,
)

@Serializable
data class DownloadUrlResponse(
    val urls: List<DownloadUrlDto> = emptyList(),
    val expires: String? = null,
)

@Serializable
data class ThumbnailUrlResponse(
    val url: String = "",
    val obfuscated: Boolean? = false,
    val expires: String? = null,
)

@Serializable
data class DownloadUrlDto(
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("src", "source")
    val url: String = "",
    val name: String? = null,
    val size: Long? = null,
)

@Serializable
data class PaginationDto(
    val page: Int = 0,
    @SerialName("page_size") val pageSize: Int = 0,
    val total: Int = 0,
)

@Serializable
data class BatchFileRequest(
    val uris: List<String>,
)

@Serializable
data class RenameFileRequest(
    val uri: String,
    val name: String,
)

@Serializable
data class MoveCopyFileRequest(
    val uris: List<String>,
    val target: String,
)

@Serializable
data class CreateFileRequest(
    val uri: String,
    val type: String,
    @SerialName("err_on_conflict") val errOnConflict: Boolean = true,
)

@Serializable
data class UploadSessionRequest(
    val uri: String,
    val size: Long,
    @SerialName("policy_id") val policyId: String? = null,
    @SerialName("last_modified") val lastModified: Long? = null,
    val previous: String? = null,
    @SerialName("entity_type") val entityType: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("encryption_supported") val encryptionSupported: List<String> = emptyList(),
)

@Serializable
data class UploadSessionResponse(
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("sessionId")
    @SerialName("session_id")
    val sessionId: String = "",
    @SerialName("upload_id") val uploadId: String = "",
    @SerialName("chunk_size") val chunkSize: Long = 0,
    @SerialName("upload_urls") val uploadUrls: List<String> = emptyList(),
    val credential: String? = null,
    @SerialName("upload_policy") val uploadPolicy: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("completeURL", "complete_url")
    val completeUrl: String? = null,
    @SerialName("storage_policy") val storagePolicy: UploadStoragePolicyDto? = null,
    @SerialName("encrypt_metadata") val encryptMetadata: UploadEncryptMetadataDto? = null,
    val uri: String = "",
    val expires: Long? = null,
    @SerialName("callback_secret") val callbackSecret: String = "",
)

@Serializable
data class UploadStoragePolicyDto(
    val id: String = "",
    val name: String = "",
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("type", "policy_type")
    val type: JsonElement? = null,
    val relay: Boolean = false,
    @SerialName("max_size") val maxSize: Long? = null,
    @SerialName("chunk_concurrency") val chunkConcurrency: Int? = null,
    @SerialName("streaming_encryption") val streamingEncryption: Boolean? = null,
)

@Serializable
data class UploadEncryptMetadataDto(
    val cipher: String? = null,
    @SerialName("key_plain_text") val keyPlainText: String? = null,
    val iv: String? = null,
)

@Serializable
data class DeleteUploadSessionRequest(
    val id: String,
    val uri: String,
)
