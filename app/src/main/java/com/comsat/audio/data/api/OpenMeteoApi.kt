package com.comsat.audio.data.api

import com.comsat.audio.data.model.GeocodingResponseDto
import com.comsat.audio.data.model.OpenMeteoForecastDto
import retrofit2.http.GET
import retrofit2.http.Query

// Open-Meteo needs no API key; its geocoder lives on a separate host.
interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getCurrent(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") fields: String = "temperature_2m,weather_code,is_day"
    ): OpenMeteoForecastDto

    @GET("https://geocoding-api.open-meteo.com/v1/search")
    suspend fun searchPlaces(
        @Query("name") name: String,
        @Query("language") language: String,
        @Query("count") count: Int = 10,
        @Query("format") format: String = "json"
    ): GeocodingResponseDto
}
