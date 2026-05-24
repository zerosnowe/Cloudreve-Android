package com.zerostudio.cloudreve

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.zerostudio.cloudreve.di.allCloudreveModules
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class CloudreveApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CloudreveApplication)
            modules(allCloudreveModules)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val authenticatedClient = GlobalContext.get().get<OkHttpClient>()
        return ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { authenticatedClient },
                    ),
                )
            }
            .build()
    }
}
