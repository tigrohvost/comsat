package com.comsat.audio.data.api

import com.comsat.audio.data.model.MetForecastDto
import retrofit2.http.GET
import retrofit2.http.Query

// MET Norway needs no API key, only an identifying User-Agent (set on the
// shared client). Coordinates go through metCoordinate().
interface MetNorwayApi {
    @GET("weatherapi/locationforecast/2.0/compact")
    suspend fun getForecast(
        @Query("lat") latitude: String,
        @Query("lon") longitude: String
    ): MetForecastDto
}
