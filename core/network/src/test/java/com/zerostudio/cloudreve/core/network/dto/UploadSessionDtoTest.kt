package com.zerostudio.cloudreve.core.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadSessionDtoTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun uploadSessionParsesLegacyTypeAndCompleteUrlFields() {
        val response = json.decodeFromString<ApiEnvelope<UploadSessionResponse>>(
            """
            {
              "code": 0,
              "data": {
                "session_id": "session-1",
                "upload_id": "upload-1",
                "chunk_size": 1048576,
                "upload_urls": ["https://example.com/upload/part-1"],
                "completeURL": "https://example.com/upload/complete",
                "callback_secret": "secret-1",
                "storage_policy": {
                  "id": "policy-1",
                  "type": "s3",
                  "relay": false
                }
              }
            }
            """.trimIndent(),
        ).unwrap()

        assertEquals("session-1", response.sessionId)
        assertEquals("https://example.com/upload/complete", response.completeUrl)
        assertEquals("secret-1", response.callbackSecret)
        assertEquals("s3", response.storagePolicy?.typeAsString())
    }

    @Test
    fun uploadSessionParsesPolicyTypeAndSnakeCaseCompleteUrlAliases() {
        val response = json.decodeFromString<ApiEnvelope<UploadSessionResponse>>(
            """
            {
              "code": 0,
              "data": {
                "session_id": "session-2",
                "chunk_size": 5242880,
                "complete_url": "https://example.com/upload/complete",
                "callback_secret": "secret-2",
                "storage_policy": {
                  "id": "policy-2",
                  "policy_type": "onedrive",
                  "relay": false
                }
              }
            }
            """.trimIndent(),
        ).unwrap()

        assertEquals("https://example.com/upload/complete", response.completeUrl)
        assertEquals("secret-2", response.callbackSecret)
        assertEquals("onedrive", response.storagePolicy?.typeAsString())
    }

    @Test
    fun uploadSessionParsesUploadPolicyAndChunkConcurrency() {
        val response = json.decodeFromString<ApiEnvelope<UploadSessionResponse>>(
            """
            {
              "code": 0,
              "data": {
                "session_id": "session-3",
                "chunk_size": 4194304,
                "upload_urls": ["https://upload.example.com/base"],
                "upload_policy": "base64-policy",
                "mime_type": "image/png",
                "callback_secret": "secret-3",
                "storage_policy": {
                  "id": "policy-3",
                  "policy_type": "upyun",
                  "relay": false,
                  "chunk_concurrency": 3,
                  "streaming_encryption": true
                },
                "encrypt_metadata": {
                  "cipher": "aes-256-ctr",
                  "key_plain_text": "Zm9v",
                  "iv": "YmFy"
                }
              }
            }
            """.trimIndent(),
        ).unwrap()

        assertEquals("base64-policy", response.uploadPolicy)
        assertEquals("image/png", response.mimeType)
        assertEquals(3, response.storagePolicy?.chunkConcurrency)
        assertTrue(response.storagePolicy?.streamingEncryption == true)
        assertEquals("aes-256-ctr", response.encryptMetadata?.cipher)
    }

    @Test
    fun uploadSessionRequestSerializesSelectedPolicyId() {
        val payload = json.encodeToString(
            UploadSessionRequest(
                uri = "cloudreve://my/readme.txt",
                size = 1024,
                policyId = "policy-1",
                mimeType = "text/plain",
            ),
        )

        assertTrue(payload.contains("\"policy_id\":\"policy-1\""))
        assertTrue(payload.contains("\"uri\":\"cloudreve://my/readme.txt\""))
    }
}

private fun UploadStoragePolicyDto.typeAsString(): String =
    type?.toString()?.trim('"')?.lowercase().orEmpty()
