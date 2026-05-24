package com.zerostudio.cloudreve.core.network.api

import com.zerostudio.cloudreve.core.network.dto.ApiEnvelope
import com.zerostudio.cloudreve.core.network.dto.RemoteDownloadRequest
import com.zerostudio.cloudreve.core.network.dto.RemoteDownloadTaskDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface CloudreveWorkflowApi {
    @POST("workflow/remote-download")
    suspend fun createRemoteDownload(@Body body: RemoteDownloadRequest): ApiEnvelope<RemoteDownloadTaskDto>

    @GET("workflow/remote-download")
    suspend fun remoteDownloads(): ApiEnvelope<List<RemoteDownloadTaskDto>>
}
