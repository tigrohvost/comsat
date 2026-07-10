package com.comsat.audio.data.repository

import com.comsat.audio.data.api.MetarApi
import com.comsat.audio.data.model.AtisData
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
        runCatching { metarApi.getMetar(icao).firstOrNull()?.toAtisData() }.getOrNull()
    }
}
