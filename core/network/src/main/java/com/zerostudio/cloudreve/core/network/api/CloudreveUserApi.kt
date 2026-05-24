package com.zerostudio.cloudreve.core.network.api

import com.zerostudio.cloudreve.core.network.dto.ApiEnvelope
import com.zerostudio.cloudreve.core.network.dto.UploadStoragePolicyDto
import com.zerostudio.cloudreve.core.network.dto.UserDto
import retrofit2.http.GET

interface CloudreveUserApi {
    @GET("user/me")
    suspend fun me(): ApiEnvelope<UserDto>

    @GET("user/setting/policies")
    suspend fun listStoragePolicies(): ApiEnvelope<List<UploadStoragePolicyDto>>
}
