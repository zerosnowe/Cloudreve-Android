package com.zerostudio.cloudreve.core.common

import com.zerostudio.cloudreve.core.common.locale.AppLanguageController
import com.zerostudio.cloudreve.core.common.locale.DefaultAppLanguageController
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val commonModule = module {
    single<DispatchersProvider> { DefaultDispatchersProvider() }
    single<AppLanguageController> { DefaultAppLanguageController(androidContext()) }
}
