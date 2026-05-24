package com.zerostudio.cloudreve.core.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthDtosTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun signInRequestUsesCloudreveV4EmailField() {
        val body = json.encodeToString(SignInRequest(email = "dev@example.com", password = "secret"))

        assertTrue(body.contains("\"email\":\"dev@example.com\""))
        assertFalse(body.contains("userName"))
    }

    @Test
    fun refreshRequestUsesCloudreveV4SnakeCaseField() {
        val body = json.encodeToString(RefreshTokenRequest(refreshToken = "refresh"))

        assertEquals("{\"refresh_token\":\"refresh\"}", body)
    }

    @Test
    fun tokenDtoParsesCloudreveV4ExpiresTimestamp() {
        val token = json.decodeFromString<TokenDto>(
            """
            {
              "access_token": "access",
              "refresh_token": "refresh",
              "access_expires": "2025-11-08T15:04:26.702611+11:00"
            }
            """.trimIndent(),
        )

        assertEquals("access", token.accessToken)
        assertEquals("refresh", token.refreshToken)
        assertEquals(1762574666702L, token.accessExpiresAtEpochMillis())
    }

    @Test
    fun tokenDtoParsesCompatibleTokenAliases() {
        val token = json.decodeFromString<TokenDto>(
            """
            {
              "token": "access",
              "refreshToken": "refresh",
              "expiresAt": "2025-11-08T15:04:26.702611+11:00"
            }
            """.trimIndent(),
        )

        assertEquals("access", token.accessToken)
        assertEquals("refresh", token.refreshToken)
        assertEquals(1762574666702L, token.accessExpiresAtEpochMillis())
    }
}
