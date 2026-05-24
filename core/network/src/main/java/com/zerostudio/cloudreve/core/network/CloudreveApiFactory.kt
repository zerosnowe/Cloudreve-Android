package com.zerostudio.cloudreve.core.network

import com.zerostudio.cloudreve.core.network.api.CloudreveFileApi
import com.zerostudio.cloudreve.core.network.api.CloudreveSessionApi
import com.zerostudio.cloudreve.core.network.api.CloudreveShareApi
import com.zerostudio.cloudreve.core.network.api.CloudreveSiteApi
import com.zerostudio.cloudreve.core.network.api.CloudreveUploadCallbackApi
import com.zerostudio.cloudreve.core.network.api.CloudreveUserApi
import com.zerostudio.cloudreve.core.network.api.CloudreveWebDavApi
import com.zerostudio.cloudreve.core.network.api.CloudreveWorkflowApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class CloudreveApiFactory(
    private val json: Json,
    private val authenticatedClient: OkHttpClient,
) {
    private val contentType = "application/json".toMediaType()

    fun sessionApi(baseUrl: String): CloudreveSessionApi = retrofit(baseUrl).create(CloudreveSessionApi::class.java)
    fun fileApi(baseUrl: String): CloudreveFileApi = retrofit(baseUrl).create(CloudreveFileApi::class.java)
    fun uploadCallbackApi(baseUrl: String): CloudreveUploadCallbackApi = retrofit(baseUrl).create(CloudreveUploadCallbackApi::class.java)
    fun shareApi(baseUrl: String): CloudreveShareApi = retrofit(baseUrl).create(CloudreveShareApi::class.java)
    fun siteApi(baseUrl: String): CloudreveSiteApi = retrofit(baseUrl).create(CloudreveSiteApi::class.java)
    fun userApi(baseUrl: String): CloudreveUserApi = retrofit(baseUrl).create(CloudreveUserApi::class.java)
    fun workflowApi(baseUrl: String): CloudreveWorkflowApi = retrofit(baseUrl).create(CloudreveWorkflowApi::class.java)
    fun webDavApi(baseUrl: String): CloudreveWebDavApi = retrofit(baseUrl).create(CloudreveWebDavApi::class.java)

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(normalizeBaseUrl(baseUrl))
        .client(authenticatedClient)
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()

    private fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/api/v4")) {
            "$trimmed/"
        } else {
            "$trimmed/api/v4/"
        }
    }
}
