package com.zerostudio.cloudreve.core.network.api

import com.zerostudio.cloudreve.core.network.dto.ApiEnvelope
import com.zerostudio.cloudreve.core.network.dto.LoginResponse
import com.zerostudio.cloudreve.core.network.dto.RefreshTokenRequest
import com.zerostudio.cloudreve.core.network.dto.SignInRequest
import com.zerostudio.cloudreve.core.network.dto.TokenDto
import retrofit2.http.Body
import retrofit2.http.POST

interface CloudreveSessionApi {
    @POST("session/token")
    suspend fun signIn(@Body body: SignInRequest): ApiEnvelope<LoginResponse>

    @POST("session/token/refresh")
    suspend fun refresh(@Body body: RefreshTokenRequest): ApiEnvelope<TokenDto>
}
