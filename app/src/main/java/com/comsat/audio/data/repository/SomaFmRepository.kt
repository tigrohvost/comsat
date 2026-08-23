package com.comsat.audio.data.repository

import com.comsat.audio.data.api.SomaFmApi
import com.comsat.audio.data.model.SomaStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SomaFmRepository @Inject constructor(
    private val somaFmApi: SomaFmApi
) {
    // Rain's own radio, pinned ahead of the SomaFM directory and usable as a
    // fallback when the somafm.com API is unreachable.
    val customStations = listOf(
        SomaStation(
            id = "rain-radio",
            title = "Rain Radio",
            description = "Generative lofi · ambient · experimental, composed live by Rain",
            genre = "lofi ambient",
            streamUrl = RAIN_RADIO_BASE,
            imageUrl = "",
            network = "RAIN NET",
            nextTrackApiBase = RAIN_RADIO_BASE
        )
    )

    suspend fun getStations(): List<SomaStation> = withContext(Dispatchers.IO) {
        customStations + somaFmApi.getChannels().channels
            .filter { it.id.isNotBlank() }
            .map { it.toStation() }
            .sortedByDescending { it.listeners }
    }

    private companion object {
        const val RAIN_RADIO_BASE = "http://192.144.15.152:8788"
    }
}
