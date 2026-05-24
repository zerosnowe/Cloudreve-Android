package com.zerostudio.cloudreve.core.domain.model

data class ShareLink(
    val id: String,
    val url: String,
    val password: String?,
    val expiresAtEpochMillis: Long?,
)
