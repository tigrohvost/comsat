package com.comsat.audio.widget

import com.comsat.audio.data.model.Airport
import com.comsat.audio.data.model.HourlyWeather
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
    val isDay: Boolean = true,
    // Null without hourly data covering the next 12 hours
    val forecast: WidgetForecast? = null
)

data class WidgetForecast(
    // Four whole hours, three apart, across the next 12 hours
    val hours: List<ForecastHour>,
    // Hourly temperatures across the chart: x runs 0..1 over the next 12 hours
    // (a bit beyond at both ends) and the four hours sit at [HOUR_X], the
    // centers of four equal columns under the chart.
    val curve: List<ChartPoint>
) {
    // Linear between the hourly points; null outside them
    fun tempAt(x: Float): Double? {
        val after = curve.indexOfFirst { it.x >= x }
        if (after < 0) return null
        val next = curve[after]
        if (next.x == x) return next.tempC
        val previous = curve.getOrNull(after - 1) ?: return null
        return previous.tempC + (next.tempC - previous.tempC) * (x - previous.x) / (next.x - previous.x)
    }

    companion object {
        val HOUR_X = listOf(0.125f, 0.375f, 0.625f, 0.875f)
    }
}

data class ForecastHour(val timeMillis: Long, val tempC: Int, val condition: WeatherCondition, val isDay: Boolean)

data class ChartPoint(val x: Float, val tempC: Double)

// Older readings would misstate the weather; show the place without them.
const val WEATHER_MAX_AGE_MS = 3 * 60 * 60_000L

private const val HOUR_MS = 60 * 60_000L

/**
 * Columns follow the hour in effect at [nowMillis] by 2, 5, 8 and 11 hours, so
 * the chart starts half past it: with columns at 1/8, 3/8, 5/8 and 7/8 of the
 * width, x = 0 is that hour + 30 min and x = 1 twelve hours later.
 */
fun widgetForecast(hourly: List<HourlyWeather>, nowMillis: Long): WidgetForecast? {
    val sorted = hourly.sortedBy { it.timeMillis }
    val anchor = sorted.lastOrNull { it.timeMillis <= nowMillis }?.timeMillis ?: return null
    val hours = listOf(2, 5, 8, 11).map { offset ->
        val hour = sorted.find { it.timeMillis == anchor + offset * HOUR_MS } ?: return null
        ForecastHour(hour.timeMillis, hour.tempC.roundToInt(), hour.condition, hour.isDay)
    }
    val start = anchor + HOUR_MS / 2
    val span = 12 * HOUR_MS
    // One point past each edge lets the line run to the border.
    val curve = sorted
        .filter { it.timeMillis in anchor..anchor + 13 * HOUR_MS }
        .map { ChartPoint((it.timeMillis - start).toFloat() / span, it.tempC) }
    return WidgetForecast(hours, curve)
}

fun widgetWeather(state: WeatherState, airport: Airport?, nowMillis: Long): WidgetWeather {
    val place = state.target(airport) ?: return WidgetWeather()
    val report = state.report?.takeIf { it.isUsable(place, nowMillis, WEATHER_MAX_AGE_MS) }
    return WidgetWeather(
        place = place.name,
        tempC = report?.tempC?.roundToInt(),
        condition = report?.condition,
        isDay = report?.isDay ?: true,
        forecast = report?.let { widgetForecast(it.hourly, nowMillis) }
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
