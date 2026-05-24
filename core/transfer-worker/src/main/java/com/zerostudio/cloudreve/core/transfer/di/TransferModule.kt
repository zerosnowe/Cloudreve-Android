package com.zerostudio.cloudreve.core.transfer.di

import com.zerostudio.cloudreve.core.transfer.TransferScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val transferModule = module {
    single { TransferScheduler(androidContext(), get()) }
}
