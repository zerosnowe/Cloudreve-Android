package com.zerostudio.cloudreve.core.security.di

import com.zerostudio.cloudreve.core.domain.repository.CredentialStore
import com.zerostudio.cloudreve.core.security.EncryptedCredentialStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val securityModule = module {
    single<CredentialStore> { EncryptedCredentialStore(androidContext()) }
}
