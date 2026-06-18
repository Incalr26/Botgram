package com.incalr26.botgram.data.remote

import android.app.Application
import com.incalr26.botgram.util.LogManager
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {
    private lateinit var client: OkHttpClient

    fun init(application: Application) {
        val loggingInterceptor = HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
            override fun log(message: String) {
                LogManager.write(application, message)
            }
        }).apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun getClient(): OkHttpClient = client
}
