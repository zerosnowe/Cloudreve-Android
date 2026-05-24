package com.zerostudio.cloudreve.core.network.api

import com.zerostudio.cloudreve.core.network.dto.ApiEnvelope
import com.zerostudio.cloudreve.core.network.dto.SiteInfoDto
import retrofit2.http.GET

interface CloudreveSiteApi {
    @GET("site/config")
    suspend fun siteInfo(): ApiEnvelope<SiteInfoDto>
}
