package com.zerostudio.cloudreve.core.domain.repository

import com.zerostudio.cloudreve.core.domain.model.CloudreveSession

interface CredentialStore {
    fun loadSession(): CloudreveSession?
    fun saveSession(session: CloudreveSession)
    fun clearSession()
}
