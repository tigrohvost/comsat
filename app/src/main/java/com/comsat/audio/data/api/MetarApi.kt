package com.comsat.audio.data.api

import com.comsat.audio.data.model.MetarDto
import retrofit2.http.GET
import retrofit2.http.Query

interface MetarApi {
    @GET("api/data/metar")
    suspend fun getMetar(
        @Query("ids") icao: String,
        @Query("format") format: String = "json"
    ): List<MetarDto>
}
