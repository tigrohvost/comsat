package com.comsat.audio.data.repository

import com.comsat.audio.data.model.Airport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveAtcRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val checkClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun getAirportsWithStatus(): List<Airport> = withContext(Dispatchers.IO) {
        AIRPORT_CATALOG.map { airport ->
            async { airport.copy(isOnline = checkIcecastOnline(airport.streamUrl)) }
        }.awaitAll()
    }

    // Icecast ignores HEAD; use GET and close immediately after reading the response code.
    // Active stream → 200; offline/missing mount → IOException or non-200.
    private fun checkIcecastOnline(streamUrl: String): Boolean = try {
        val request = Request.Builder()
            .url(streamUrl)
            .addHeader("Icy-MetaData", "1")
            .get()
            .build()
        checkClient.newCall(request).execute().use { it.code == 200 }
    } catch (_: IOException) {
        false
    }
}
