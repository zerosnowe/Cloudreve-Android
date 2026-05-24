package com.zerostudio.cloudreve.core.data.mapper

import com.zerostudio.cloudreve.core.database.entity.FileEntity
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.model.FileType
import com.zerostudio.cloudreve.core.network.dto.FileDto
import org.junit.Assert.assertEquals
import org.junit.Test

class FileMappersTest {
    @Test
    fun cloudreveV4FolderTypeMapsToFolder() {
        val node = FileDto(
            id = "folder-id",
            name = "Photos",
            path = "cloudreve://my/Photos",
            type = 1,
        ).toDomain(parentUri = CloudreveUri.Root)

        assertEquals(FileType.Folder, node.type)
        assertEquals(CloudreveUri.Root, node.parentUri)
    }

    @Test
    fun cloudreveV4UpdatedAtMapsToEpochMillis() {
        val node = FileDto(
            id = "file-id",
            name = "notes.md",
            path = "cloudreve://my/notes.md",
            type = 0,
            updatedAt = "2025-11-08T15:04:26.702611+11:00",
        ).toDomain(parentUri = CloudreveUri.Root)

        assertEquals(FileType.Code, node.type)
        assertEquals(1762574666702L, node.updatedAtEpochMillis)
    }

    @Test
    fun imageExtensionsMapToImageWithoutMime() {
        val extensions = listOf(
            "jpg", "jpeg", "jpe", "png", "gif", "webp", "bmp", "dib", "wbmp", "heic",
            "heif", "avif", "svg", "svgz", "tif", "tiff", "ico", "cur", "apng", "jxl",
            "jp2", "j2k", "jpf", "jpx", "jpm", "mj2", "raw", "dng", "cr2", "cr3",
            "nef", "nrw", "arw", "srf", "sr2", "orf", "rw2", "raf", "pef", "x3f",
            "psd", "tga", "exr", "hdr",
        )

        extensions.forEach { extension ->
            val node = FileDto(
                id = "image-$extension",
                name = "sample.$extension",
                path = "cloudreve://my/sample.$extension",
                type = 0,
                mime = null,
            ).toDomain(parentUri = CloudreveUri.Root)

            assertEquals("Expected .$extension to be treated as image", FileType.Image, node.type)
        }
    }

    @Test
    fun legacyUnknownCacheImageExtensionMapsToImage() {
        val node = FileEntity(
            instanceId = "https://cloudreve.example.com",
            uri = "cloudreve://my/camera.HEIC",
            id = "legacy-image",
            name = "camera.HEIC",
            parentUri = "cloudreve://my/",
            type = FileType.Unknown.name,
            size = 1024L,
            mimeType = null,
            updatedAtEpochMillis = 0L,
            isFavorite = false,
            isOffline = false,
        ).toDomain()

        assertEquals(FileType.Image, node.type)
    }

    @Test
    fun archiveExtensionsMapToArchiveWithoutMime() {
        val extensions = listOf(
            "zip", "zipx", "rar", "7z", "tar", "tgz", "gz", "bz", "bz2", "tbz",
            "tbz2", "xz", "txz", "lz", "lz4", "lzma", "lzop", "z", "tz", "zst",
            "tzst", "cab", "cpio", "pax", "shar", "arj", "ace", "alz", "arc", "sit",
            "sitx", "sea", "hqx", "pak", "jar", "aar", "war", "ear", "sar", "xpi",
            "whl", "egg", "xapk", "apks", "ipa", "deb", "rpm", "pkg", "iso", "dmg",
        )

        assertEquals(50, extensions.distinct().size)

        extensions.forEach { extension ->
            val node = FileDto(
                id = "archive-$extension",
                name = "sample.$extension",
                path = "cloudreve://my/sample.$extension",
                type = 0,
                mime = null,
            ).toDomain(parentUri = CloudreveUri.Root)

            assertEquals("Expected .$extension to be treated as archive", FileType.Archive, node.type)
        }
    }

    @Test
    fun legacyUnknownCacheArchiveExtensionMapsToArchive() {
        val node = FileEntity(
            instanceId = "https://cloudreve.example.com",
            uri = "cloudreve://my/archive.ZIPX",
            id = "legacy-archive",
            name = "archive.ZIPX",
            parentUri = "cloudreve://my/",
            type = FileType.Unknown.name,
            size = 1024L,
            mimeType = null,
            updatedAtEpochMillis = 0L,
            isFavorite = false,
            isOffline = false,
        ).toDomain()

        assertEquals(FileType.Archive, node.type)
    }

    @Test
    fun fileNodeEntityStoresCloudreveInstanceId() {
        val node = FileDto(
            id = "file-id",
            name = "notes.md",
            path = "cloudreve://my/notes.md",
            type = 0,
        ).toDomain(parentUri = CloudreveUri.Root)

        val entity = node.toEntity(instanceId = "https://cloudreve.example.com")

        assertEquals("https://cloudreve.example.com", entity.instanceId)
        assertEquals("cloudreve://my/notes.md", entity.uri)
        assertEquals("cloudreve://my/", entity.parentUri)
    }
}
