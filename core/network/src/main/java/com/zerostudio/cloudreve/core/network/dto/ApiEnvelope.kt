package com.zerostudio.cloudreve.core.network.dto

import com.zerostudio.cloudreve.core.common.CloudreveException
import kotlinx.serialization.Serializable

@Serializable
data class ApiEnvelope<T>(
    val code: Int = 0,
    val data: T? = null,
    val msg: String = "",
    val message: String = "",
)

fun <T> ApiEnvelope<T>.unwrap(): T {
    throwIfError()
    return data ?: throw CloudreveException.Api(code = code, message = "Cloudreve response data is empty")
}

fun <T> ApiEnvelope<T>.unwrapNullable(): T? {
    throwIfError()
    return data
}

fun ApiEnvelope<Unit?>.unwrapUnit() {
    throwIfError()
}

fun isCloudreveAuthenticationError(code: Int, message: String): Boolean {
    if (code == CLOUDREVE_NOT_LOGGED_IN_CODE) return true
    val normalized = message.trim().lowercase()
    return normalized == "login required" ||
        normalized == "authentication required" ||
        normalized == "not logged in" ||
        normalized == "unauthorized" ||
        normalized.contains("login required") ||
        normalized.contains("authentication required") ||
        normalized.contains("not logged in") ||
        normalized.contains("not authenticated") ||
        normalized.contains("invalid token") ||
        normalized.contains("token expired") ||
        normalized.contains("未登录") ||
        normalized.contains("请先登录") ||
        normalized.contains("需要登录") ||
        normalized.contains("登录已过期")
}

private fun ApiEnvelope<*>.throwIfError() {
    if (code == 0) return
    val message = errorMessage()
    if (isCloudreveAuthenticationError(code, message)) {
        throw CloudreveException.Unauthorized(message)
    }
    throw CloudreveException.Api(code = code, message = message)
}

private fun ApiEnvelope<*>.errorMessage(): String =
    msg.ifBlank { message }.ifBlank { "Cloudreve API error $code" }

private const val CLOUDREVE_NOT_LOGGED_IN_CODE = 401
