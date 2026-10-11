package com.comsat.audio.data.model

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

// ─── Open-Meteo ───────────────────────────────────────────────────────────────

data class OpenMeteoForecastDto(
    @SerializedName("current") val current: OpenMeteoCurrentDto? = null
)

data class OpenMeteoCurrentDto(
    @SerializedName("temperature_2m") val temperature: Double? = null,
    @SerializedName("weather_code") val weatherCode: Int? = null,
    @SerializedName("is_day") val isDay: Int? = null
) {
    fun toReport(place: WeatherPlace, fetchedAtMillis: Long): WeatherReport? {
        return WeatherReport(
            tempC = temperature ?: return null,
            condition = weatherCondition(weatherCode ?: return null),
            isDay = isDay != 0,
            latitude = place.latitude,
            longitude = place.longitude,
            fetchedAtMillis = fetchedAtMillis
        )
    }
}

// ─── MET Norway ───────────────────────────────────────────────────────────────

// Fallback for networks that cannot reach api.open-meteo.com
data class MetForecastDto(
    @SerializedName("properties") val properties: MetPropertiesDto? = null
) {
    fun toReport(place: WeatherPlace, fetchedAtMillis: Long): WeatherReport? {
        val series = properties?.timeseries.orEmpty()
        // Hourly steps from the current hour on; take the one in effect now.
        val step = series.lastOrNull { candidate -> candidate.startMillis()?.let { it <= fetchedAtMillis } == true }
            ?: series.firstOrNull()
            ?: return null
        val data = step.data ?: return null
        val symbol = (data.nextHour ?: data.nextSixHours)?.summary?.symbolCode ?: return null
        return WeatherReport(
            tempC = data.instant?.details?.airTemperature ?: return null,
            condition = metCondition(symbol),
            isDay = !symbol.endsWith("_night"),
            latitude = place.latitude,
            longitude = place.longitude,
            fetchedAtMillis = fetchedAtMillis
        )
    }
}

data class MetPropertiesDto(
    @SerializedName("timeseries") val timeseries: List<MetStepDto>? = null
)

data class MetStepDto(
    @SerializedName("time") val time: String? = null,
    @SerializedName("data") val data: MetStepDataDto? = null
) {
    fun startMillis(): Long? = time?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
}

data class MetStepDataDto(
    @SerializedName("instant") val instant: MetInstantDto? = null,
    @SerializedName("next_1_hours") val nextHour: MetPeriodDto? = null,
    @SerializedName("next_6_hours") val nextSixHours: MetPeriodDto? = null
)

data class MetInstantDto(
    @SerializedName("details") val details: MetInstantDetailsDto? = null
)

data class MetInstantDetailsDto(
    @SerializedName("air_temperature") val airTemperature: Double? = null
)

data class MetPeriodDto(
    @SerializedName("summary") val summary: MetSummaryDto? = null
)

data class MetSummaryDto(
    @SerializedName("symbol_code") val symbolCode: String? = null
)

// MET symbol codes such as "lightrainshowers_day" or "heavysnowandthunder"
fun metCondition(symbolCode: String): WeatherCondition {
    val symbol = symbolCode.substringBefore('_')
    return when {
        "thunder" in symbol -> WeatherCondition.THUNDERSTORM
        "snow" in symbol || "sleet" in symbol -> WeatherCondition.SNOW
        "rain" in symbol -> WeatherCondition.RAIN
        symbol == "fog" -> WeatherCondition.FOG
        symbol == "clearsky" -> WeatherCondition.CLEAR
        symbol == "fair" || symbol == "partlycloudy" -> WeatherCondition.PARTLY_CLOUDY
        else -> WeatherCondition.CLOUDY
    }
}

// MET rejects coordinates with more than four decimals.
fun metCoordinate(value: Double): String = String.format(Locale.ROOT, "%.4f", value)

// ─── Open-Meteo geocoding ─────────────────────────────────────────────────────

data class GeocodingResponseDto(
    // Omitted entirely when nothing matches
    @SerializedName("results") val results: List<GeocodingPlaceDto>? = null
)

data class GeocodingPlaceDto(
    @SerializedName("name") val name: String = "",
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("admin1") val admin1: String? = null
) {
    fun toPlace(): WeatherPlace? {
        if (name.isBlank()) return null
        return WeatherPlace(
            name = name,
            latitude = latitude ?: return null,
            longitude = longitude ?: return null,
            region = listOfNotNull(admin1, country)
                .filter { it.isNotBlank() && it != name }
                .distinct()
                .joinToString(", ")
        )
    }
}

// ─── Weather ──────────────────────────────────────────────────────────────────

enum class WeatherCondition { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDERSTORM }

// WMO 4677 present-weather codes as used by Open-Meteo
fun weatherCondition(wmoCode: Int): WeatherCondition = when (wmoCode) {
    0 -> WeatherCondition.CLEAR
    1, 2 -> WeatherCondition.PARTLY_CLOUDY
    45, 48 -> WeatherCondition.FOG
    in 51..57 -> WeatherCondition.DRIZZLE
    in 61..67, in 80..82 -> WeatherCondition.RAIN
    in 71..77, 85, 86 -> WeatherCondition.SNOW
    in 95..99 -> WeatherCondition.THUNDERSTORM
    else -> WeatherCondition.CLOUDY
}

data class WeatherPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    // Shown only in the place picker to tell same-named cities apart
    val region: String = ""
) {
    // Within roughly 5 km; forecast grid cells are coarser than that.
    fun isNear(latitude: Double, longitude: Double): Boolean =
        abs(this.latitude - latitude) < 0.05 && abs(this.longitude - longitude) < 0.05
}

data class WeatherReport(
    val tempC: Double,
    val condition: WeatherCondition,
    val isDay: Boolean,
    // The requested place, not the provider's grid cell, so a report can be
    // matched against the current selection.
    val latitude: Double,
    val longitude: Double,
    val fetchedAtMillis: Long
) {
    fun isUsable(place: WeatherPlace, nowMillis: Long, maxAgeMillis: Long): Boolean =
        place.isNear(latitude, longitude) && nowMillis - fetchedAtMillis in 0 until maxAgeMillis
}

enum class WeatherSource { CURRENT_LOCATION, AIRPORT, PLACE }

data class WeatherState(
    // Null until a location is chosen for the weather widget
    val source: WeatherSource? = null,
    val place: WeatherPlace? = null,
    val deviceLocation: WeatherPlace? = null,
    val report: WeatherReport? = null
) {
    fun target(airport: Airport?): WeatherPlace? = when (source) {
        null -> null
        WeatherSource.CURRENT_LOCATION -> deviceLocation
        WeatherSource.PLACE -> place
        WeatherSource.AIRPORT -> airport
            ?.takeIf { it.lat != 0f || it.lon != 0f }
            ?.let { WeatherPlace(it.city.ifBlank { it.icao }, it.lat.toDouble(), it.lon.toDouble()) }
    }
}
