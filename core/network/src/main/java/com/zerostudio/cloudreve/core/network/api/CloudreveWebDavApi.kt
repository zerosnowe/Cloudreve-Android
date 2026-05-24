package com.zerostudio.cloudreve.core.network.api

import com.zerostudio.cloudreve.core.network.dto.ApiEnvelope
import com.zerostudio.cloudreve.core.network.dto.WebDavAccountDto
import retrofit2.http.GET

interface CloudreveWebDavApi {
    @GET("webdav/accounts")
    suspend fun accounts(): ApiEnvelope<List<WebDavAccountDto>>
}
