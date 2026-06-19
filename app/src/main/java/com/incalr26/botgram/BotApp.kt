package com.incalr26.botgram

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.incalr26.botgram.data.local.DatabaseHelper
import com.incalr26.botgram.data.remote.ApiClient

class BotApp : Application(), ImageLoaderFactory {

    lateinit var databaseHelper: DatabaseHelper
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        databaseHelper = DatabaseHelper(this)
        ApiClient.init(this)
    }

    // Coil 使用 ApiClient 的 OkHttpClient（已配置代理）
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(ApiClient.getClient())
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("coil_cache")).build() }
            .build()
    }

    companion object {
        lateinit var instance: BotApp
            private set
    }
}
