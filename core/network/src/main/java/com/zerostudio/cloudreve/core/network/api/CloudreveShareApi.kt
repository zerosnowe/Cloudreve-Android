package com.zerostudio.cloudreve.core.network.api

import com.zerostudio.cloudreve.core.network.dto.ApiEnvelope
import com.zerostudio.cloudreve.core.network.dto.CreateShareRequest
import com.zerostudio.cloudreve.core.network.dto.ShareDto
import retrofit2.http.Body
import retrofit2.http.POST

interface CloudreveShareApi {
    @POST("share")
    suspend fun createShare(@Body body: CreateShareRequest): ApiEnvelope<ShareDto>
}
