package com.zerostudio.cloudreve.feature.share

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val shareModule = module {
    viewModel { ShareViewModel(get()) }
}
