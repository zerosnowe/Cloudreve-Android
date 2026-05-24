package com.zerostudio.cloudreve.feature.files

import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val filesModule = module {
    viewModel { parameters ->
        FilesViewModel(
            repository = get(),
            transferScheduler = get(),
            dispatchers = get(),
            initialUri = runCatching { parameters.get<CloudreveUri>() }.getOrDefault(CloudreveUri.Root),
        )
    }
}
