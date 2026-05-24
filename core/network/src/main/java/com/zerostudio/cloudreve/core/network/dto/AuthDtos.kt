package com.zerostudio.cloudreve.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonNames
import java.time.OffsetDateTime

@Serializable
data class SignInRequest(
    val email: String,
    val password: String,
    @SerialName("captcha") val captchaCode: String? = null,
    @SerialName("ticket") val captchaTicket: String? = null,
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class LoginResponse(
    val user: UserDto = UserDto(),
    val token: TokenDto = TokenDto(),
)

@Serializable
data class TokenDto(
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("accessToken", "token")
    @SerialName("access_token")
    val accessToken: String = "",
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("refreshToken")
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("accessExpires", "access_expires_at", "expires_at", "expiresAt")
    @SerialName("access_expires")
    val accessExpires: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("refreshExpires", "refresh_expires_at")
    @SerialName("refresh_expires")
    val refreshExpires: String? = null,
) {
    fun accessExpiresAtEpochMillis(): Long? = accessExpires?.toEpochMillisOrNull()
    fun refreshExpiresAtEpochMillis(): Long? = refreshExpires?.toEpochMillisOrNull()
}

@Serializable
data class UserDto(
    val id: String = "",
    val nickname: String = "",
    val email: String? = null,
)

private fun String.toEpochMillisOrNull(): Long? =
    runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }.getOrNull()
