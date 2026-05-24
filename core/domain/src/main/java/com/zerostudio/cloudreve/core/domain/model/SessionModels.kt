package com.zerostudio.cloudreve.core.domain.model

data class CloudreveInstance(
    val id: String,
    val name: String,
    val baseUrl: String,
)

data class CloudreveSession(
    val instance: CloudreveInstance,
    val userId: String,
    val nickname: String,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMillis: Long?,
)

data class SignInCommand(
    val baseUrl: String,
    val username: String,
    val password: String,
    val captchaCode: String? = null,
    val twoFactorCode: String? = null,
)
