package com.comsat.audio.data.repository

import com.comsat.audio.data.model.Airport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveAtcRepository @Inject constructor(
    okHttpClient: OkHttpClient
) {
    // LiveATC's Icecast servers usually answer in ~1 s but hold the response
    // headers for up to ~9 s when busy, so the read timeout has to be generous.
    // Concurrency is capped to keep the sweep polite: 36 simultaneous stream
    // connections from one IP is exactly the pattern LiveATC rate-limits, and
    // a 404 for a dead mount comes back in well under a second anyway.
    private val checkClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val probeSlots = Semaphore(MAX_CONCURRENT_PROBES)

    suspend fun getAirportsWithStatus(): List<Airport> = withContext(Dispatchers.IO) {
        AIRPORT_CATALOG.map { airport ->
            async {
                val online = probeSlots.withPermit { checkIcecastOnline(airport.streamUrl) }
                airport.copy(isOnline = online)
            }
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

    private companion object {
        const val MAX_CONCURRENT_PROBES = 4
    }
}
