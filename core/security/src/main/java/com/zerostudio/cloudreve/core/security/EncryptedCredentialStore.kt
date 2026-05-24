package com.zerostudio.cloudreve.core.security

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.zerostudio.cloudreve.core.domain.model.CloudreveInstance
import com.zerostudio.cloudreve.core.domain.model.CloudreveSession
import com.zerostudio.cloudreve.core.domain.repository.CredentialStore

class EncryptedCredentialStore(context: Context) : CredentialStore {
    private val appContext = context.applicationContext

    private val masterKey: MasterKey by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val preferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EncryptedSharedPreferences.create(
            appContext,
            "cloudreve.credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun loadSession(): CloudreveSession? {
        val baseUrl = preferences.getString(KEY_BASE_URL, null) ?: return null
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val instanceId = preferences.getString(KEY_INSTANCE_ID, baseUrl) ?: baseUrl
        val instanceName = preferences.getString(KEY_INSTANCE_NAME, "Cloudreve") ?: "Cloudreve"
        val userId = preferences.getString(KEY_USER_ID, "") ?: ""
        val nickname = preferences.getString(KEY_NICKNAME, "") ?: ""
        return CloudreveSession(
            instance = CloudreveInstance(
                id = instanceId,
                name = instanceName,
                baseUrl = baseUrl,
            ),
            userId = userId,
            nickname = nickname,
            accessToken = accessToken,
            refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null),
            expiresAtEpochMillis = preferences.getLong(KEY_EXPIRES_AT, 0L).takeIf { it > 0L },
        )
    }

    override fun saveSession(session: CloudreveSession) {
        preferences.edit {
            putString(KEY_INSTANCE_ID, session.instance.id)
            putString(KEY_INSTANCE_NAME, session.instance.name)
            putString(KEY_BASE_URL, session.instance.baseUrl)
            putString(KEY_USER_ID, session.userId)
            putString(KEY_NICKNAME, session.nickname)
            putString(KEY_ACCESS_TOKEN, session.accessToken)
            putString(KEY_REFRESH_TOKEN, session.refreshToken)
            putLong(KEY_EXPIRES_AT, session.expiresAtEpochMillis ?: 0L)
        }
    }

    override fun clearSession() {
        preferences.edit { clear() }
    }

    private companion object {
        const val KEY_INSTANCE_ID = "instance_id"
        const val KEY_INSTANCE_NAME = "instance_name"
        const val KEY_BASE_URL = "base_url"
        const val KEY_USER_ID = "user_id"
        const val KEY_NICKNAME = "nickname"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
