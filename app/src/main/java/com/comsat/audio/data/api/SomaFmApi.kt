package com.comsat.audio.data.api

import com.comsat.audio.data.model.SomaChannelsResponse
import retrofit2.http.GET

interface SomaFmApi {
    @GET("channels.json")
    suspend fun getChannels(): SomaChannelsResponse
}
