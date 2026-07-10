package com.comsat.audio.di

import com.comsat.audio.BuildConfig
import com.comsat.audio.data.api.MetarApi
import com.comsat.audio.data.api.SomaFmApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
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
            .apply {
                // Logs stream URLs; keep out of release builds
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
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
