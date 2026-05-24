package com.zerostudio.cloudreve.core.data.mapper

import com.zerostudio.cloudreve.core.database.entity.FileEntity
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.model.FileDetails
import com.zerostudio.cloudreve.core.domain.model.FileNode
import com.zerostudio.cloudreve.core.domain.model.FileType
import com.zerostudio.cloudreve.core.network.dto.FileDto
import com.zerostudio.cloudreve.core.network.dto.FileExtendedInfoDto
import com.zerostudio.cloudreve.core.network.dto.FileInfoDto
import java.time.OffsetDateTime
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun FileDto.toDomain(parentUri: CloudreveUri = CloudreveUri.Root): FileNode {
    val resolvedUri = path.ifBlank { CloudreveUri.Root.child(name).value }
    return FileNode(
        id = id.ifBlank { resolvedUri },
        name = name,
        uri = CloudreveUri.parse(resolvedUri),
        parentUri = parentUri,
        type = inferFileType(type, name, mime),
        size = size,
        mimeType = mime,
        updatedAtEpochMillis = updatedAt.toEpochMillisOrZero(),
        thumbnailUrl = null,
    )
}

fun FileNode.toEntity(instanceId: String): FileEntity = FileEntity(
    instanceId = instanceId,
    uri = uri.value,
    id = id,
    name = name,
    parentUri = parentUri.value,
    type = type.name,
    size = size,
    mimeType = mimeType,
    updatedAtEpochMillis = updatedAtEpochMillis,
    isFavorite = isFavorite,
    isOffline = isOffline,
    thumbnailUrl = thumbnailUrl,
)

fun FileEntity.toDomain(): FileNode {
    val storedType = runCatching { FileType.valueOf(type) }.getOrDefault(FileType.Unknown)
    val resolvedType = if (storedType == FileType.Unknown) {
        inferFileType(apiType = 0, name = name, mime = mimeType)
    } else {
        storedType
    }
    return FileNode(
        id = id,
        name = name,
        uri = CloudreveUri.parse(uri),
        parentUri = CloudreveUri.parse(parentUri),
        type = resolvedType,
        size = size,
        mimeType = mimeType,
        updatedAtEpochMillis = updatedAtEpochMillis,
        isFavorite = isFavorite,
        isOffline = isOffline,
        thumbnailUrl = thumbnailUrl,
    )
}

fun FileInfoDto.toDetails(): FileDetails {
    val resolvedUri = path.ifBlank { CloudreveUri.Root.child(name).value }
    return FileDetails(
        id = id.ifBlank { resolvedUri },
        name = name,
        uri = CloudreveUri.parse(resolvedUri),
        type = inferFileType(type, name, mime),
        size = size,
        mimeType = mime,
        createdAtEpochMillis = createdAt.toEpochMillisOrZero(),
        updatedAtEpochMillis = updatedAt.toEpochMillisOrZero(),
        owned = owned,
        storagePolicyName = extendedInfo.storagePolicyDisplayName(),
        storageUsed = extendedInfo?.storageUsed,
        metadata = metadata.orEmpty().mapValues { (_, value) ->
            (value as? JsonPrimitive)?.contentOrNull ?: value.toString()
        },
    )
}

private fun FileExtendedInfoDto?.storagePolicyDisplayName(): String? {
    if (this == null) return null
    storagePolicyName?.takeIf { it.isNotBlank() }?.let { return it }
    return when (val policy = storagePolicy) {
        is JsonPrimitive -> policy.contentOrNull
        is JsonObject -> policy["name"]?.jsonPrimitive?.contentOrNull
            ?: policy["id"]?.jsonPrimitive?.contentOrNull
        else -> null
    }
}

private fun String?.toEpochMillisOrZero(): Long =
    this?.let { value ->
        runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
    } ?: 0L

private fun inferFileType(apiType: Int, name: String, mime: String?): FileType {
    if (apiType == CLOUDREVE_FOLDER_TYPE) {
        return FileType.Folder
    }
    val lowerMime = mime.orEmpty().lowercase(Locale.ROOT)
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        lowerMime.startsWith("image/") || extension in imageExtensions -> FileType.Image
        lowerMime.startsWith("video/") -> FileType.Video
        lowerMime.startsWith("audio/") || extension in audioExtensions -> FileType.Audio
        lowerMime == "application/pdf" || extension == "pdf" -> FileType.Pdf
        extension in officeExtensions -> FileType.Office
        extension in codeExtensions -> FileType.Code
        lowerMime in archiveMimeTypes || extension in archiveExtensions -> FileType.Archive
        else -> FileType.Unknown
    }
}

private val imageExtensions = setOf(
    "jpg", "jpeg", "jpe", "png", "gif", "webp", "bmp", "dib", "wbmp", "heic",
    "heif", "avif", "svg", "svgz", "tif", "tiff", "ico", "cur", "apng", "jxl",
    "jp2", "j2k", "jpf", "jpx", "jpm", "mj2", "raw", "dng", "cr2", "cr3",
    "nef", "nrw", "arw", "srf", "sr2", "orf", "rw2", "raf", "pef", "x3f",
    "psd", "tga", "exr", "hdr",
)
private val officeExtensions = setOf("doc", "docx", "ppt", "pptx", "xls", "xlsx", "odt", "ods", "odp")
private val codeExtensions = setOf("kt", "java", "js", "ts", "json", "xml", "html", "css", "go", "rs", "py", "md", "yaml", "yml")
private val audioExtensions = setOf(
    "mp3", "aac", "m4a", "m4b", "m4p", "flac", "wav", "wave", "ogg", "oga",
    "opus", "wma", "alac", "aif", "aiff", "aifc", "ape", "mpc", "ac3", "ec3",
    "amr", "3ga", "mid", "midi", "kar", "rmi", "dts", "dsf", "dff", "tak",
    "tta", "ra", "rm", "au", "snd", "caf", "gsm", "mka", "weba", "wv",
    "spx", "mpga", "mp2", "mp1", "adts", "aa", "aax", "awb", "voc", "8svx",
    "cda", "it", "s3m", "xm", "mod", "umx",
)
private val archiveMimeTypes = setOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/vnd.rar",
    "application/x-rar-compressed",
    "application/x-7z-compressed",
    "application/gzip",
    "application/x-gzip",
    "application/x-bzip",
    "application/x-bzip2",
    "application/x-xz",
    "application/x-lz4",
    "application/x-lzip",
    "application/zstd",
    "application/x-zstd",
    "application/x-tar",
    "application/java-archive",
    "application/x-iso9660-image",
    "application/x-apple-diskimage",
)
private val archiveExtensions = setOf(
    "zip", "zipx", "rar", "7z", "tar", "tgz", "gz", "bz", "bz2", "tbz",
    "tbz2", "xz", "txz", "lz", "lz4", "lzma", "lzop", "z", "tz", "zst",
    "tzst", "cab", "cpio", "pax", "shar", "arj", "ace", "alz", "arc", "sit",
    "sitx", "sea", "hqx", "pak", "jar", "aar", "war", "ear", "sar", "xpi",
    "whl", "egg", "xapk", "apks", "ipa", "deb", "rpm", "pkg", "iso", "dmg",
)
private const val CLOUDREVE_FOLDER_TYPE = 1
