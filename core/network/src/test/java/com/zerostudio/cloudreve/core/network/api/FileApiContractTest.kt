package com.zerostudio.cloudreve.core.network.api

import com.zerostudio.cloudreve.core.network.dto.ApiEnvelope
import com.zerostudio.cloudreve.core.network.dto.FileListResponse
import com.zerostudio.cloudreve.core.network.dto.unwrap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT

class FileApiContractTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun listFilesUsesCloudreveV4FileEndpoint() {
        val method = CloudreveFileApi::class.java.methods.first { it.name == "listFiles" }

        assertEquals("file", method.getAnnotation(GET::class.java)?.value)
    }

    @Test
    fun fileInfoUsesCloudreveV4InfoEndpoint() {
        val method = CloudreveFileApi::class.java.methods.first { it.name == "getInfo" }

        assertEquals("file/info", method.getAnnotation(GET::class.java)?.value)
    }

    @Test
    fun downloadUrlUsesCloudreveV4UrlEndpoint() {
        val method = CloudreveFileApi::class.java.methods.first { it.name == "createDownloadUrl" }

        assertEquals("file/url", method.getAnnotation(POST::class.java)?.value)
    }

    @Test
    fun createUploadSessionUsesCloudreveV4UploadEndpoint() {
        val method = CloudreveFileApi::class.java.methods.first { it.name == "createUploadSession" }

        assertEquals("file/upload", method.getAnnotation(PUT::class.java)?.value)
    }

    @Test
    fun deleteUploadSessionUsesCloudreveV4UploadEndpoint() {
        val method = CloudreveFileApi::class.java.methods.first { it.name == "deleteUploadSession" }
        val annotation = method.getAnnotation(HTTP::class.java)

        assertEquals("DELETE", annotation?.method)
        assertEquals("file/upload", annotation?.path)
    }

    @Test
    fun listStoragePoliciesUsesCloudreveV4UserSettingEndpoint() {
        val method = CloudreveUserApi::class.java.methods.first { it.name == "listStoragePolicies" }

        assertEquals("user/setting/policies", method.getAnnotation(GET::class.java)?.value)
    }

    @Test
    fun listFilesParsesCloudreveV4FilesResponse() {
        val envelope = json.decodeFromString<ApiEnvelope<FileListResponse>>(
            """
            {
              "code": 0,
              "data": {
                "files": [
                  {
                    "id": "file-id",
                    "name": "notes.md",
                    "path": "cloudreve://my/notes.md",
                    "type": 0,
                    "size": 42,
                    "created_at": "2025-11-08T15:04:26.702611+11:00",
                    "updated_at": "2025-11-08T15:04:26.702611+11:00",
                    "metadata": {
                      "thumb": "https://example.com/thumb.jpg"
                    }
                  }
                ],
                "parent": {
                  "id": "root",
                  "name": "",
                  "path": "cloudreve://my/",
                  "type": 1
                },
                "storage_policy": {
                  "id": "policy-root",
                  "name": "Local",
                  "type": "local",
                  "relay": true
                }
              }
            }
            """.trimIndent(),
        )

        val response = envelope.unwrap()
        assertEquals("notes.md", response.files.single().name)
        assertEquals(0, response.files.single().type)
        assertNotNull(response.parent)
        assertEquals("policy-root", response.storagePolicy?.id)
    }

    @Test
    fun listFilesKeepsBackwardCompatibleObjectsAlias() {
        val envelope = json.decodeFromString<ApiEnvelope<FileListResponse>>(
            """
            {
              "code": 0,
              "data": {
                "objects": [
                  {
                    "id": "file-id",
                    "name": "notes.md",
                    "path": "cloudreve://my/notes.md",
                    "type": 0
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals("notes.md", envelope.unwrap().files.single().name)
    }
}
