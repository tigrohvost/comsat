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
    suspend fun getStations(): List<SomaStation> = withContext(Dispatchers.IO) {
        somaFmApi.getChannels().channels
            .filter { it.id.isNotBlank() }
            .map { it.toStation() }
            .sortedByDescending { it.listeners }
    }
}
