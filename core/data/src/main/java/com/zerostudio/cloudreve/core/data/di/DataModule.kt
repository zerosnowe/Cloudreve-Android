package com.zerostudio.cloudreve.core.data.di

import com.zerostudio.cloudreve.core.data.repository.DefaultCloudreveRepository
import com.zerostudio.cloudreve.core.data.repository.DefaultTokenRefresher
import com.zerostudio.cloudreve.core.database.dao.FileDao
import com.zerostudio.cloudreve.core.database.dao.TransferDao
import com.zerostudio.cloudreve.core.domain.repository.CloudreveRepository
import com.zerostudio.cloudreve.core.domain.repository.TokenRefresher
import com.zerostudio.cloudreve.core.network.CloudreveApiFactory
import org.koin.dsl.module

val dataModule = module {
    single<TokenRefresher> { DefaultTokenRefresher(get(), get()) }
    single<CloudreveRepository> {
        DefaultCloudreveRepository(
            apiFactory = lazy { get<CloudreveApiFactory>() },
            credentialStore = get(),
            fileDao = lazy { get<FileDao>() },
            transferDao = lazy { get<TransferDao>() },
            dispatchers = get(),
        )
    }
}
