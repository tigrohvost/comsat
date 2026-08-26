package com.comsat.audio.di

import com.comsat.audio.BuildConfig
import com.comsat.audio.data.api.MetarApi
import com.comsat.audio.data.api.SomaFmApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            // Some mobile carriers (seen on Beeline RU, LTE) silently drop the
            // third-or-so request sent over a reused TLS connection to Cloudflare
            // (d.liveatc.net): the request goes out, no bytes ever come back,
            // and the call dies on the read timeout. A fresh connection per
            // request never shows this. So: never keep idle connections, and
            // stay on HTTP/1.1 — h2 would multiplex every request onto one
            // connection and hit the same wall immediately. Nothing here needs
            // h2, and the extra TLS handshakes are negligible.
            .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
            .protocols(listOf(Protocol.HTTP_1_1))
            .apply {
                // Logs stream URLs; keep out of release builds. Network-level so
                // every hop shows up, including the d.liveatc.net 302 that
                // precedes each Icecast request.
                if (BuildConfig.DEBUG) {
                    addNetworkInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .build()

    @Provides
    @Singleton
    fun provideSomaFmApi(client: OkHttpClient): SomaFmApi =
        Retrofit.Builder()
            .baseUrl("https://somafm.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SomaFmApi::class.java)

    @Provides
    @Singleton
    fun provideMetarApi(client: OkHttpClient): MetarApi =
        Retrofit.Builder()
            .baseUrl("https://aviationweather.gov/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MetarApi::class.java)
}
