package com.comsat.audio.widget

import com.comsat.audio.data.model.Airport
import com.comsat.audio.data.model.StreamStatus
import com.comsat.audio.data.model.WeatherCondition
import com.comsat.audio.data.model.WeatherState
import com.comsat.audio.data.repository.AppSettings
import com.comsat.audio.ui.theme.ThemeMode
import kotlin.math.roundToInt

data class WidgetModel(
    val airportIcao: String?,
    val airportName: String?,
    val stationTitle: String?,
    val stationNetwork: String?,
    val atcStatus: StreamStatus,
    val somaStatus: StreamStatus,
    val themeMode: ThemeMode,
    val weather: WidgetWeather = WidgetWeather()
)

data class WidgetWeather(
    // Null until a location is chosen and, for the device location, found
    val place: String? = null,
    // Null without a recent report for that place
    val tempC: Int? = null,
    val condition: WeatherCondition? = null,
    val isDay: Boolean = true
)

// Older readings would misstate the weather; show the place without them.
const val WEATHER_MAX_AGE_MS = 3 * 60 * 60_000L

fun widgetWeather(state: WeatherState, airport: Airport?, nowMillis: Long): WidgetWeather {
    val place = state.target(airport) ?: return WidgetWeather()
    val report = state.report?.takeIf { it.isUsable(place, nowMillis, WEATHER_MAX_AGE_MS) }
    return WidgetWeather(
        place = place.name,
        tempC = report?.tempC?.roundToInt(),
        condition = report?.condition,
        isDay = report?.isDay ?: true
    )
}

fun widgetModel(
    settings: AppSettings,
    playback: PlaybackSnapshot,
    airport: Airport?,
    weather: WidgetWeather = WidgetWeather()
): WidgetModel {
    val airportIcao = settings.airportIcao?.takeIf { it.isNotBlank() }
    val stationId = settings.stationId?.takeIf { it.isNotBlank() }
    val rainStation = stationId == "rain-radio"
    return WidgetModel(
        airportIcao = airportIcao,
        airportName = airport?.takeIf { it.icao == airportIcao }?.name,
        stationTitle = stationId?.let {
            settings.stationTitle?.takeIf(String::isNotBlank)
                ?: if (rainStation) "Rain Radio" else it
        },
        stationNetwork = stationId?.let {
            settings.stationNetwork?.takeIf(String::isNotBlank)
                ?: if (rainStation) "RAIN NET" else "SOMAFM"
        },
        atcStatus = when {
            airportIcao == null -> StreamStatus.IDLE
            playback.atcPreparing -> StreamStatus.LOADING
            else -> playback.atcStatus
        },
        somaStatus = when {
            stationId == null -> StreamStatus.IDLE
            playback.somaPreparing -> StreamStatus.LOADING
            else -> playback.somaStatus
        },
        themeMode = settings.themeMode,
        weather = weather
    )
}
