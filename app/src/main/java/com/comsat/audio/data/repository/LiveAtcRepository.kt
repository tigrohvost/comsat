package com.comsat.audio.data.repository

import com.comsat.audio.data.model.Airport
import com.comsat.audio.data.model.AtcFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveAtcRepository @Inject constructor(
    okHttpClient: OkHttpClient,
    private val catalog: AirportCatalog
) {
    // LiveATC's Icecast servers usually answer in ~1 s but hold the response
    // headers for up to ~9 s when busy, so the read timeout has to be generous.
    // Concurrency is capped to keep the sweep polite: dozens of simultaneous
    // stream connections from one IP is exactly the pattern LiveATC rate-limits,
    // and a 404 for a dead mount comes back in well under a second anyway.
    private val checkClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val probeSlots = Semaphore(MAX_CONCURRENT_PROBES)

    suspend fun getAirportsWithStatus(): List<Airport> = withContext(Dispatchers.IO) {
        catalog.airports.map { airport ->
            async {
                val live = probeSlots.withPermit { findLiveFeed(airport.feeds) }
                airport.copy(activeFeed = live, probed = true)
            }
        }.awaitAll()
    }

    // Walk the preferred feeds in order and return the first one that streams.
    // A missing mount (404) or a refused connection is cheap, so move on to the
    // next candidate; a timeout already cost 15 s, so give up on the airport.
    private fun findLiveFeed(feeds: List<AtcFeed>): AtcFeed? {
        for (feed in feeds.take(MAX_FEEDS_PER_AIRPORT)) {
            when (probe(feed.streamUrl)) {
                ProbeResult.ONLINE -> return feed
                ProbeResult.SKIP -> continue
                ProbeResult.GIVE_UP -> return null
            }
        }
        return null
    }

    private enum class ProbeResult { ONLINE, SKIP, GIVE_UP }

    // Icecast ignores HEAD; use GET and close immediately after reading the response code.
    private fun probe(streamUrl: String): ProbeResult = try {
        val request = Request.Builder()
            .url(streamUrl)
            .addHeader("Icy-MetaData", "1")
            .get()
            .build()
        checkClient.newCall(request).execute().use { response ->
            if (response.code == 200) ProbeResult.ONLINE else ProbeResult.SKIP
        }
    } catch (_: SocketTimeoutException) {
        ProbeResult.GIVE_UP
    } catch (_: ConnectException) {
        ProbeResult.SKIP
    } catch (_: IOException) {
        ProbeResult.GIVE_UP
    }

    private companion object {
        const val MAX_CONCURRENT_PROBES = 4
        const val MAX_FEEDS_PER_AIRPORT = 3
    }
}
