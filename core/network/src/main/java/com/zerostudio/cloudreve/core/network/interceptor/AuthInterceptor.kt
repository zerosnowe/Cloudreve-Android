package com.zerostudio.cloudreve.core.network.interceptor

import com.zerostudio.cloudreve.core.domain.repository.CredentialStore
import com.zerostudio.cloudreve.core.domain.repository.TokenRefresher
import com.zerostudio.cloudreve.core.network.dto.isCloudreveAuthenticationError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class AuthInterceptor(
    private val credentialStore: CredentialStore,
    private val tokenRefresher: TokenRefresher,
    private val json: Json,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val request = original.withCurrentAccessToken()
        val response = chain.proceed(request)
        if (!request.canRefreshToken() || !response.requiresTokenRefresh()) return response

        if (!tokenRefresher.refreshBlocking()) return response

        val refreshedToken = credentialStore.loadSession()
            ?.accessToken
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return response

        response.close()
        val retry = original.newBuilder()
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX $refreshedToken")
            .build()
        return chain.proceed(retry)
    }

    private fun Request.withCurrentAccessToken(): Request {
        if (!canAttachToken()) return this
        val token = credentialStore.loadSession()
            ?.accessToken
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return this
        return newBuilder()
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX $token")
            .build()
    }

    private fun Response.requiresTokenRefresh(): Boolean {
        if (code == HTTP_UNAUTHORIZED) return true
        val rawBody = runCatching { peekBody(MAX_AUTH_ERROR_BODY_BYTES).string() }.getOrNull()
            ?: return false
        return runCatching {
            val payload = json.parseToJsonElement(rawBody).jsonObject
            val code = payload[CODE_FIELD]?.jsonPrimitive?.intOrNull ?: return false
            val message = payload[MSG_FIELD]?.jsonPrimitive?.contentOrNull
                ?: payload[MESSAGE_FIELD]?.jsonPrimitive?.contentOrNull
                ?: ""
            isCloudreveAuthenticationError(code, message)
        }.getOrDefault(false)
    }

    private fun Request.canAttachToken(): Boolean = !isSessionEndpoint()

    private fun Request.canRefreshToken(): Boolean = !isSessionEndpoint()

    private fun Request.isSessionEndpoint(): Boolean {
        val path = url.encodedPath.trimEnd('/')
        return path.endsWith("/session/token") || path.endsWith("/session/token/refresh")
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer"
        const val HTTP_UNAUTHORIZED = 401
        const val MAX_AUTH_ERROR_BODY_BYTES = 4_096L
        const val CODE_FIELD = "code"
        const val MSG_FIELD = "msg"
        const val MESSAGE_FIELD = "message"
    }
}
