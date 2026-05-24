package com.zerostudio.cloudreve.core.data.repository

import com.zerostudio.cloudreve.core.domain.model.CloudreveSession
import com.zerostudio.cloudreve.core.domain.repository.CredentialStore
import com.zerostudio.cloudreve.core.domain.repository.TokenRefresher
import com.zerostudio.cloudreve.core.network.api.CloudreveSessionApi
import com.zerostudio.cloudreve.core.network.dto.RefreshTokenRequest
import com.zerostudio.cloudreve.core.network.dto.unwrap
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class DefaultTokenRefresher(
    private val credentialStore: CredentialStore,
    private val json: Json,
) : TokenRefresher {
    private val lock = Any()
    private val contentType = "application/json".toMediaType()
    private val client = OkHttpClient.Builder().build()

    override fun refreshBlocking(): Boolean = synchronized(lock) {
        val session = credentialStore.loadSession() ?: return false
        val refreshToken = session.refreshToken?.trim()?.takeIf { it.isNotBlank() } ?: return false
        runCatching {
            runBlocking {
                val api = Retrofit.Builder()
                    .baseUrl(normalizeBaseUrl(session.instance.baseUrl))
                    .client(client)
                    .addConverterFactory(json.asConverterFactory(contentType))
                    .build()
                    .create(CloudreveSessionApi::class.java)
                val token = api.refresh(RefreshTokenRequest(refreshToken)).unwrap()
                val accessToken = token.accessToken.trim().takeIf { it.isNotBlank() }
                    ?: error("Cloudreve token refresh did not return an access token")
                credentialStore.saveSession(
                    session.copy(
                        accessToken = accessToken,
                        refreshToken = token.refreshToken?.trim()?.takeIf { it.isNotBlank() } ?: session.refreshToken,
                        expiresAtEpochMillis = token.accessExpiresAtEpochMillis(),
                    ),
                )
            }
        }.isSuccess
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/api/v4")) "$trimmed/" else "$trimmed/api/v4/"
    }
}
