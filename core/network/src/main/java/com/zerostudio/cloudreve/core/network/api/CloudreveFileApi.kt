package com.zerostudio.cloudreve.core.network.api

import com.zerostudio.cloudreve.core.network.dto.ApiEnvelope
import com.zerostudio.cloudreve.core.network.dto.BatchFileRequest
import com.zerostudio.cloudreve.core.network.dto.CreateFileRequest
import com.zerostudio.cloudreve.core.network.dto.DeleteUploadSessionRequest
import com.zerostudio.cloudreve.core.network.dto.DownloadUrlRequest
import com.zerostudio.cloudreve.core.network.dto.DownloadUrlResponse
import com.zerostudio.cloudreve.core.network.dto.FileInfoDto
import com.zerostudio.cloudreve.core.network.dto.FileListResponse
import com.zerostudio.cloudreve.core.network.dto.FileDto
import com.zerostudio.cloudreve.core.network.dto.MoveCopyFileRequest
import com.zerostudio.cloudreve.core.network.dto.RenameFileRequest
import com.zerostudio.cloudreve.core.network.dto.ThumbnailUrlResponse
import com.zerostudio.cloudreve.core.network.dto.UploadSessionRequest
import com.zerostudio.cloudreve.core.network.dto.UploadSessionResponse
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

interface CloudreveFileApi {
    @GET("file")
    suspend fun listFiles(
        @Query("uri") uri: String,
        @Query("page") page: Int = 0,
        @Query("page_size") pageSize: Int = 200,
    ): ApiEnvelope<FileListResponse>

    @GET("file/info")
    suspend fun getInfo(
        @Query("uri") uri: String,
        @Query("extended") extended: Boolean = true,
        @Query("folder_summary") folderSummary: Boolean = false,
    ): ApiEnvelope<FileInfoDto>

    @POST("file/url")
    suspend fun createDownloadUrl(@Body body: DownloadUrlRequest): ApiEnvelope<DownloadUrlResponse>

    @GET("file/thumb")
    suspend fun getThumbnail(@Query("uri") uri: String): ApiEnvelope<ThumbnailUrlResponse>

    @PATCH("file/rename")
    suspend fun rename(@Body body: RenameFileRequest): ApiEnvelope<Unit?>

    @HTTP(method = "DELETE", path = "file", hasBody = true)
    suspend fun delete(@Body body: BatchFileRequest): ApiEnvelope<Unit?>

    @POST("file/move")
    suspend fun move(@Body body: MoveCopyFileRequest): ApiEnvelope<Unit?>

    @POST("file/copy")
    suspend fun copy(@Body body: MoveCopyFileRequest): ApiEnvelope<Unit?>

    @POST("file/create")
    suspend fun create(@Body body: CreateFileRequest): ApiEnvelope<FileDto?>

    @PUT("file/upload")
    suspend fun createUploadSession(@Body body: UploadSessionRequest): ApiEnvelope<UploadSessionResponse>

    @POST("file/upload/{sessionId}/{chunkIndex}")
    suspend fun uploadChunk(
        @Path("sessionId") sessionId: String,
        @Path("chunkIndex") chunkIndex: Int,
        @Body body: RequestBody,
    ): ApiEnvelope<Unit?>

    @HTTP(method = "DELETE", path = "file/upload", hasBody = true)
    suspend fun deleteUploadSession(@Body body: DeleteUploadSessionRequest): ApiEnvelope<Unit?>
}
