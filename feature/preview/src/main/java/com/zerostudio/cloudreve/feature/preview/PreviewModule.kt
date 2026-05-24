package com.zerostudio.cloudreve.feature.preview

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val previewModule = module {
    single {
        CloudreveAlbumRepository(
            repository = get(),
            dispatchers = get(),
        )
    }
    single {
        AlbumSyncNotificationController(
            context = get(),
        )
    }
    viewModel {
        AlbumViewModel(
            albumRepository = get(),
            notificationController = get(),
            dispatchers = get(),
        )
    }
    viewModel { parameters ->
        ImagePreviewViewModel(
            repository = get(),
            appContext = get(),
            authenticatedClient = get(),
            args = parameters.get(),
        )
    }
    viewModel { parameters ->
        MusicPlayerViewModel(
            repository = get(),
            appContext = get(),
            authenticatedClient = get(),
            dispatchers = get(),
            args = parameters.get(),
        )
    }
}
