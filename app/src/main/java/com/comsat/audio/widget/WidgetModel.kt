package com.comsat.audio.widget

import com.comsat.audio.data.model.Airport
import com.comsat.audio.data.model.HourlyWeather
import com.comsat.audio.data.model.StreamStatus
import com.comsat.audio.data.model.WeatherCondition
import com.comsat.audio.data.model.WeatherState
import com.comsat.audio.data.repository.AppSettings
import com.comsat.audio.ui.theme.ThemeMode
import kotlin.math.hypot
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
    // Three whole hours, four apart, across the next 12 hours
    val hours: List<ForecastHour>,
    // Hourly temperatures across the chart: x runs 0..1 over the next 12 hours
    // (a bit beyond the end) and the three hours sit at [HOUR_X], the centers
    // of three equal columns under the chart.
    val curve: List<ChartPoint>
) {
    // The line's slope at each point of [curve], in °C per unit of x
    val slopes: List<Double> = monotoneSlopes(curve)

    // On the smooth line; null outside the hourly points
    fun tempAt(x: Float): Double? {
        val after = curve.indexOfFirst { it.x >= x }
        if (after < 0) return null
        val next = curve[after]
        if (next.x == x) return next.tempC
        val previous = curve.getOrNull(after - 1) ?: return null
        // Cubic Hermite between the two hours
        val width = (next.x - previous.x).toDouble()
        val t = (x - previous.x) / width
        return (1 + 2 * t) * (1 - t) * (1 - t) * previous.tempC + t * (1 - t) * (1 - t) * width * slopes[after - 1] +
            t * t * (3 - 2 * t) * next.tempC - t * t * (1 - t) * width * slopes[after]
    }

    companion object {
        val HOUR_X = listOf(1f / 6, 0.5f, 5f / 6)
    }
}

data class ForecastHour(val timeMillis: Long, val tempC: Int, val condition: WeatherCondition, val isDay: Boolean)

data class ChartPoint(val x: Float, val tempC: Double)

/**
 * Fritsch–Carlson slopes for a smooth line through [points]: each stretch
 * between two hours stays within their temperatures, so the line never shows
 * a peak or a dip the forecast does not have.
 */
fun monotoneSlopes(points: List<ChartPoint>): List<Double> {
    if (points.size < 2) return List(points.size) { 0.0 }
    val secants = points.zipWithNext { a, b -> (b.tempC - a.tempC) / (b.x - a.x) }
    val slopes = DoubleArray(points.size) { index ->
        when (index) {
            0 -> secants.first()
            points.lastIndex -> secants.last()
            // Level where the temperature turns
            else -> if (secants[index - 1] * secants[index] <= 0) 0.0 else (secants[index - 1] + secants[index]) / 2
        }
    }
    for ((index, secant) in secants.withIndex()) {
        if (secant == 0.0) {
            slopes[index] = 0.0
            slopes[index + 1] = 0.0
            continue
        }
        val a = slopes[index] / secant
        val b = slopes[index + 1] / secant
        val length = hypot(a, b)
        if (length > 3) {
            slopes[index] = 3 * a / length * secant
            slopes[index + 1] = 3 * b / length * secant
        }
    }
    return slopes.asList()
}

// Older readings would misstate the weather; show the place without them.
const val WEATHER_MAX_AGE_MS = 3 * 60 * 60_000L

private const val HOUR_MS = 60 * 60_000L

/**
 * Columns follow the hour in effect at [nowMillis] by 2, 6 and 10 hours, the
 * middles of its next three 4-hour blocks: with columns at 1/6, 1/2 and 5/6 of
 * the width, x = 0 is that hour and x = 1 twelve hours later.
 */
fun widgetForecast(hourly: List<HourlyWeather>, nowMillis: Long): WidgetForecast? {
    val sorted = hourly.sortedBy { it.timeMillis }
    val anchor = sorted.lastOrNull { it.timeMillis <= nowMillis }?.timeMillis ?: return null
    val hours = listOf(2, 6, 10).map { offset ->
        val hour = sorted.find { it.timeMillis == anchor + offset * HOUR_MS } ?: return null
        ForecastHour(hour.timeMillis, hour.tempC.roundToInt(), hour.condition, hour.isDay)
    }
    val span = 12 * HOUR_MS
    // One point past the right edge lets the line run to the border.
    val curve = sorted
        .filter { it.timeMillis in anchor..anchor + 13 * HOUR_MS }
        .map { ChartPoint((it.timeMillis - anchor).toFloat() / span, it.tempC) }
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
