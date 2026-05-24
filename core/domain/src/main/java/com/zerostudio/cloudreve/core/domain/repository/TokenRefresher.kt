package com.zerostudio.cloudreve.core.domain.repository

interface TokenRefresher {
    fun refreshBlocking(): Boolean
}

class NoOpTokenRefresher : TokenRefresher {
    override fun refreshBlocking(): Boolean = false
}
