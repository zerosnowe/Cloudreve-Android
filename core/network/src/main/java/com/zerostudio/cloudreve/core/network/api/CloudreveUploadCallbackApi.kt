package com.zerostudio.cloudreve.core.network.api

import com.zerostudio.cloudreve.core.network.dto.ApiEnvelope
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface CloudreveUploadCallbackApi {
    @POST("callback/onedrive/{sessionId}/{callbackSecret}")
    suspend fun completeOnedriveUpload(
        @Path("sessionId") sessionId: String,
        @Path("callbackSecret") callbackSecret: String,
    ): ApiEnvelope<Unit?>

    @GET("callback/s3/{sessionId}/{callbackSecret}")
    suspend fun completeS3Upload(
        @Path("sessionId") sessionId: String,
        @Path("callbackSecret") callbackSecret: String,
    ): ApiEnvelope<Unit?>

    @GET("callback/cos/{sessionId}/{callbackSecret}")
    suspend fun completeCosUpload(
        @Path("sessionId") sessionId: String,
        @Path("callbackSecret") callbackSecret: String,
    ): ApiEnvelope<Unit?>

    @POST("callback/obs/{sessionId}/{callbackSecret}")
    suspend fun completeObsUpload(
        @Path("sessionId") sessionId: String,
        @Path("callbackSecret") callbackSecret: String,
    ): ApiEnvelope<Unit?>
}
