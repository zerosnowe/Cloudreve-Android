package com.zerostudio.cloudreve.core.common

sealed class CloudreveException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    data class Api(
        val code: Int,
        override val message: String,
    ) : CloudreveException(message)

    data class Network(
        override val message: String,
        override val cause: Throwable? = null,
    ) : CloudreveException(message, cause)

    data class Unauthorized(
        override val message: String = "Authentication expired",
    ) : CloudreveException(message)

    data class MissingSession(
        override val message: String = "No active Cloudreve session",
    ) : CloudreveException(message)
}
