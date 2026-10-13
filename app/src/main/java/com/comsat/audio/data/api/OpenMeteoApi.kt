package com.comsat.audio.data.api

import com.comsat.audio.data.model.FORECAST_HOURS
import com.comsat.audio.data.model.GeocodingResponseDto
import com.comsat.audio.data.model.OpenMeteoForecastDto
import retrofit2.http.GET
import retrofit2.http.Query

// Open-Meteo needs no API key; its geocoder lives on a separate host.
interface OpenMeteoApi {
    // Hourly data starts at the current hour.
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,weather_code,is_day",
        @Query("hourly") hourly: String = "temperature_2m,weather_code,is_day",
        @Query("forecast_hours") hours: Int = FORECAST_HOURS,
        @Query("timeformat") timeFormat: String = "unixtime"
    ): OpenMeteoForecastDto

    @GET("https://geocoding-api.open-meteo.com/v1/search")
    suspend fun searchPlaces(
        @Query("name") name: String,
        @Query("language") language: String,
        @Query("count") count: Int = 10,
        @Query("format") format: String = "json"
    ): GeocodingResponseDto
}
