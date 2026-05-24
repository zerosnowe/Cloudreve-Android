package com.zerostudio.cloudreve.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateShareRequest(
    val uris: List<String>,
    val password: String? = null,
    val expires: Long? = null,
)

@Serializable
data class ShareDto(
    val id: String = "",
    val url: String = "",
    val password: String? = null,
    val expires: Long? = null,
)
