package com.comsat.audio.data.repository

import com.comsat.audio.data.api.MetarApi
import com.comsat.audio.data.model.AtisData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetarRepository @Inject constructor(
    private val metarApi: MetarApi
) {
    // ATIS is auxiliary panel data; any failure degrades to "no data"
    suspend fun fetchMetar(icao: String): AtisData? = withContext(Dispatchers.IO) {
        try {
            metarApi.getMetar(icao).firstOrNull()?.toAtisData()
        } catch (e: CancellationException) {
            // collectLatest relies on cancellation when the selected airport changes.
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
