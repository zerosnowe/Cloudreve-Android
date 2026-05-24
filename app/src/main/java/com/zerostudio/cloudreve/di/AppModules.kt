package com.zerostudio.cloudreve.di

import com.zerostudio.cloudreve.core.common.commonModule
import com.zerostudio.cloudreve.core.data.di.dataModule
import com.zerostudio.cloudreve.core.database.di.databaseModule
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.network.di.networkModule
import com.zerostudio.cloudreve.core.security.di.securityModule
import com.zerostudio.cloudreve.core.transfer.di.transferModule
import com.zerostudio.cloudreve.feature.auth.authModule
import com.zerostudio.cloudreve.feature.files.FilesViewModel
import com.zerostudio.cloudreve.feature.preview.previewModule
import com.zerostudio.cloudreve.feature.settings.settingsModule
import com.zerostudio.cloudreve.feature.share.shareModule
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

private val appFilesModule = module {
    viewModel { parameters ->
        FilesViewModel(
            repository = get(),
            transferScheduler = get(),
            dispatchers = get(),
            initialUri = runCatching { parameters.get<CloudreveUri>() }.getOrDefault(CloudreveUri.Root),
        )
    }
}

val allCloudreveModules = listOf(
    commonModule,
    securityModule,
    databaseModule,
    dataModule,
    networkModule,
    transferModule,
    authModule,
    appFilesModule,
    previewModule,
    shareModule,
    settingsModule,
)
