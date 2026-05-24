package com.zerostudio.cloudreve.feature.auth

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val authModule = module {
    viewModel { AuthViewModel(get()) }
}
