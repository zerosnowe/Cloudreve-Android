package com.zerostudio.cloudreve.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SiteInfoDto(
    val name: String = "Cloudreve",
    val version: String = "",
    val pro: Boolean = false,
)

@Serializable
data class RemoteDownloadRequest(
    val url: String,
    val dst: String,
)

@Serializable
data class RemoteDownloadTaskDto(
    val id: String = "",
    val name: String = "",
    val status: String = "",
    val progress: Float = 0f,
)

@Serializable
data class WebDavAccountDto(
    val id: String = "",
    val name: String = "",
    val root: String = "cloudreve://",
)
