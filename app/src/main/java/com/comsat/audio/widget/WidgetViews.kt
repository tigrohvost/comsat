package com.comsat.audio.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import com.comsat.audio.MainActivity
import com.comsat.audio.R
import com.comsat.audio.data.model.StreamStatus
import com.comsat.audio.data.model.WeatherCondition
import com.comsat.audio.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// WEATHER pairs a weather readout with the SLIM rows.
enum class WidgetFormat { FULL, SQUARE, SLIM, WEATHER }

/** A small, event-driven display: no network requests or animation in the launcher. */
object WidgetViews {
    // How much of the 12-hour forecast fits beside the current weather
    private enum class ForecastSize { NONE, TEXT, CHART, TALL_CHART }

    // A 5 × 1 cell span, with room for the forecast beside the readout and radio rows
    private const val FORECAST_MIN_WIDTH = 320f
    private const val TALL_CHART_MIN_HEIGHT = 72f
    private const val MIN_CHART_SPAN_C = 4.0
    private val FORECAST_ICONS = intArrayOf(
        R.id.widget_forecast_icon_1, R.id.widget_forecast_icon_2, R.id.widget_forecast_icon_3
    )
    private val FORECAST_TEMPS = intArrayOf(
        R.id.widget_forecast_temp_1, R.id.widget_forecast_temp_2, R.id.widget_forecast_temp_3
    )
    private val FORECAST_TIMES = intArrayOf(
        R.id.widget_forecast_time_1, R.id.widget_forecast_time_2, R.id.widget_forecast_time_3
    )

    fun render(
        context: Context, model: WidgetModel, options: Bundle, format: WidgetFormat = WidgetFormat.FULL
    ): RemoteViews {
        if (format != WidgetFormat.FULL) {
            val minimumWidth = if (format == WidgetFormat.WEATHER) 180f else 110f
            val minimumHeight = if (format == WidgetFormat.SQUARE) 80 else 40
            val comfortableHeight = if (format == WidgetFormat.SQUARE) 110 else 56
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val sizes = mutableMapOf(
                    SizeF(minimumWidth, minimumHeight.toFloat()) to createSmall(context, model, format, short = true),
                    SizeF(minimumWidth, comfortableHeight.toFloat()) to createSmall(context, model, format, short = false)
                )
                if (format == WidgetFormat.WEATHER) {
                    sizes[SizeF(FORECAST_MIN_WIDTH, minimumHeight.toFloat())] =
                        createSmall(context, model, format, short = true, forecastSize = ForecastSize.TEXT)
                    sizes[SizeF(FORECAST_MIN_WIDTH, comfortableHeight.toFloat())] =
                        createSmall(context, model, format, short = false, forecastSize = ForecastSize.CHART)
                    sizes[SizeF(FORECAST_MIN_WIDTH, TALL_CHART_MIN_HEIGHT)] =
                        createSmall(context, model, format, short = false, forecastSize = ForecastSize.TALL_CHART)
                }
                return RemoteViews(sizes)
            }
            val short = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, comfortableHeight) < comfortableHeight
            val forecastSize = when {
                format != WidgetFormat.WEATHER ||
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) < FORECAST_MIN_WIDTH -> ForecastSize.NONE
                short -> ForecastSize.TEXT
                else -> ForecastSize.CHART
            }
            return createSmall(context, model, format, short, forecastSize)
        }
        val largeText = context.resources.configuration.fontScale > 1.2f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The launcher chooses a fitting view on resize without starting our process.
            return RemoteViews(mapOf(
                SizeF(180f, 140f) to create(context, model, compact = true, icons = false, heading = !largeText),
                SizeF(250f, 140f) to create(context, model, compact = true, icons = !largeText, heading = !largeText),
                SizeF(250f, 170f) to create(context, model, compact = largeText, icons = !largeText)
            ))
        }
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140)
        return create(context, model, compact = largeText || height < 170, icons = !largeText && width >= 250,
            heading = !largeText || height >= 170)
    }

    private fun create(
        context: Context, model: WidgetModel, compact: Boolean, icons: Boolean, heading: Boolean = true
    ): RemoteViews {
        val palette = palette(model.themeMode)
        return RemoteViews(context.packageName, R.layout.comsat_widget).apply {
            setInt(R.id.widget_panel, "setBackgroundResource", palette.background)
            setTextColor(R.id.widget_heading, palette.text)
            setViewVisibility(R.id.widget_heading, if (heading) View.VISIBLE else View.GONE)
            setInt(R.id.widget_divider, "setBackgroundColor", palette.outline)
            setTextColor(R.id.widget_airport_label, palette.atc)
            // Nordic purple works for the icon; use stronger contrast for its small label.
            setTextColor(R.id.widget_station_label,
                if (model.themeMode == ThemeMode.NORDIC) palette.secondary else palette.radio)
            setInt(R.id.widget_airport_icon, "setColorFilter", palette.atc)
            setInt(R.id.widget_station_icon, "setColorFilter", palette.radio)
            setViewVisibility(R.id.widget_airport_icon, if (icons) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.widget_station_icon, if (icons) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.widget_airport_detail, if (compact) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.widget_station_detail, if (compact) View.GONE else View.VISIBLE)
            source(
                context, R.id.widget_airport_source, R.id.widget_airport_detail, R.id.widget_airport_status,
                model.airportIcao, model.airportName, R.string.widget_choose_airport, model.atcStatus, palette
            )
            source(
                context, R.id.widget_station_source, R.id.widget_station_detail, R.id.widget_station_status,
                model.stationTitle, model.stationNetwork, R.string.widget_choose_station, model.somaStatus, palette
            )
            openAppOnClick(context)
        }
    }

    private fun createSmall(
        context: Context, model: WidgetModel, format: WidgetFormat, short: Boolean,
        forecastSize: ForecastSize = ForecastSize.NONE
    ): RemoteViews {
        val palette = palette(model.themeMode)
        val slim = format != WidgetFormat.SQUARE
        val largeText = context.resources.configuration.fontScale > 1.2f
        // Without hourly data the wide widget keeps the plain weather layout.
        val forecast = model.weather.forecast?.takeIf { forecastSize != ForecastSize.NONE }
        return RemoteViews(context.packageName, when (format) {
            WidgetFormat.WEATHER ->
                if (forecast != null) R.layout.comsat_widget_weather_forecast else R.layout.comsat_widget_weather
            WidgetFormat.SLIM -> R.layout.comsat_widget_slim
            else -> R.layout.comsat_widget_square
        }).apply {
            setInt(R.id.widget_panel, "setBackgroundResource", palette.background)
            if (slim) {
                setInt(R.id.widget_airport_icon, "setColorFilter", palette.atc)
                setInt(R.id.widget_station_icon, "setColorFilter", palette.radio)
            } else {
                setTextColor(R.id.widget_airport_label, palette.atc)
                setTextColor(R.id.widget_station_label,
                    if (model.themeMode == ThemeMode.NORDIC) palette.secondary else palette.radio)
            }
            // Launchers may calculate spans against both portrait and landscape grids.
            // Support their shorter cells without forcing an extra row in the picker.
            val textSize = when {
                slim && short -> if (largeText) 10f else 12f
                slim -> if (largeText) 12f else 14f
                short && largeText -> 12f
                largeText -> 16f
                else -> 18f
            }
            setTextViewTextSize(R.id.widget_airport_source, TypedValue.COMPLEX_UNIT_SP, textSize)
            setTextViewTextSize(R.id.widget_station_source, TypedValue.COMPLEX_UNIT_SP, textSize)
            if (short) {
                val density = context.resources.displayMetrics.density
                setViewPadding(R.id.widget_panel, (8 * density).toInt(), (2 * density).toInt(),
                    (8 * density).toInt(), (2 * density).toInt())
                val statusSize = if (slim) 10f else 8f
                setTextViewTextSize(R.id.widget_airport_status, TypedValue.COMPLEX_UNIT_SP, statusSize)
                setTextViewTextSize(R.id.widget_station_status, TypedValue.COMPLEX_UNIT_SP, statusSize)
                if (!slim && largeText) {
                    setTextViewTextSize(R.id.widget_airport_label, TypedValue.COMPLEX_UNIT_SP, 8f)
                    setTextViewTextSize(R.id.widget_station_label, TypedValue.COMPLEX_UNIT_SP, 8f)
                }
            }
            source(context, R.id.widget_airport_source, null, R.id.widget_airport_status,
                model.airportIcao, model.airportName, R.string.widget_choose_airport,
                model.atcStatus, palette, compactStatus = true)
            source(context, R.id.widget_station_source, null, R.id.widget_station_status,
                model.stationTitle, model.stationNetwork, R.string.widget_choose_station,
                model.somaStatus, palette, compactStatus = true)
            if (format == WidgetFormat.WEATHER) {
                weather(context, model.weather, palette, short, largeText)
                if (forecast != null) forecast(context, forecast, palette, forecastSize, largeText)
            }
            openAppOnClick(context)
        }
    }

    private fun RemoteViews.weather(
        context: Context, weather: WidgetWeather, palette: Palette, short: Boolean, largeText: Boolean
    ) {
        setInt(R.id.widget_weather_divider, "setBackgroundColor", palette.outline)
        val condition = weather.condition
        setImageViewResource(R.id.widget_weather_icon, when {
            weather.place == null -> R.drawable.widget_weather_location
            condition == null -> R.drawable.widget_weather_cloudy
            else -> weatherIcon(condition, weather.isDay)
        })
        setInt(R.id.widget_weather_icon, "setColorFilter", if (condition == null) palette.secondary else palette.atc)
        val temperature = weather.tempC?.let { context.getString(R.string.widget_weather_temperature, it) }
        val place = weather.place ?: context.getString(R.string.widget_weather_set_location)
        setTextViewText(R.id.widget_weather_temp, temperature ?: context.getString(R.string.widget_weather_no_data))
        setTextViewText(R.id.widget_weather_place, place)
        setTextColor(R.id.widget_weather_temp, palette.text)
        setTextColor(R.id.widget_weather_place, palette.secondary)
        setTextViewTextSize(R.id.widget_weather_temp, TypedValue.COMPLEX_UNIT_SP, when {
            short && largeText -> 11f
            short -> 14f
            largeText -> 16f
            else -> 20f
        })
        setTextViewTextSize(R.id.widget_weather_place, TypedValue.COMPLEX_UNIT_SP, when {
            short && largeText -> 8f
            short || largeText -> 10f
            else -> 11f
        })
        val reading = if (temperature != null && condition != null) {
            "$temperature, ${context.getString(conditionLabel(condition))}"
        } else {
            context.getString(R.string.widget_weather_unavailable)
        }
        setContentDescription(R.id.widget_weather, if (weather.place == null) {
            context.getString(R.string.widget_weather_description_unset)
        } else {
            context.getString(R.string.widget_weather_description, place, reading)
        })
        // The readout opens the location picker; the radio half still opens the app.
        setOnClickPendingIntent(R.id.widget_weather, weatherLocationIntent(context))
    }

    private fun weatherLocationIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 21, Intent(context, WeatherLocationActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun RemoteViews.forecast(
        context: Context, forecast: WidgetForecast, palette: Palette, size: ForecastSize, largeText: Boolean
    ) {
        val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "ha"
        val timeFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton))
            .withZone(ZoneId.systemDefault())
        val tempSize = when {
            largeText -> 8f
            size == ForecastSize.TEXT -> 9f
            else -> 10f
        }
        val timeSize = when {
            largeText -> 7f
            size == ForecastSize.TEXT -> 8f
            else -> 8.5f
        }
        val spoken = forecast.hours.mapIndexed { index, hour ->
            val time = timeFormat.format(Instant.ofEpochMilli(hour.timeMillis))
            val temperature = context.getString(R.string.widget_weather_temperature, hour.tempC)
            setTextViewText(FORECAST_TEMPS[index], temperature)
            setTextColor(FORECAST_TEMPS[index], palette.text)
            setTextViewTextSize(FORECAST_TEMPS[index], TypedValue.COMPLEX_UNIT_SP, tempSize)
            setTextViewText(FORECAST_TIMES[index], time)
            setTextColor(FORECAST_TIMES[index], palette.secondary)
            setTextViewTextSize(FORECAST_TIMES[index], TypedValue.COMPLEX_UNIT_SP, timeSize)
            // Enlarged text needs the column width for the temperature.
            setViewVisibility(FORECAST_ICONS[index], if (largeText) View.GONE else View.VISIBLE)
            setImageViewResource(FORECAST_ICONS[index], weatherIcon(hour.condition, hour.isDay))
            setInt(FORECAST_ICONS[index], "setColorFilter", palette.atc)
            context.getString(R.string.widget_forecast_hour, time, temperature, context.getString(conditionLabel(hour.condition)))
        }
        if (size == ForecastSize.TEXT) {
            setViewVisibility(R.id.widget_forecast_chart, View.GONE)
        } else {
            val height = if (size == ForecastSize.TALL_CHART) {
                R.dimen.widget_forecast_chart_height_tall
            } else {
                R.dimen.widget_forecast_chart_height
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setViewLayoutHeightDimen(R.id.widget_forecast_chart, height)
            setImageViewBitmap(R.id.widget_forecast_chart, forecastChart(context, forecast, palette.atc, height))
        }
        setContentDescription(R.id.widget_forecast,
            context.getString(R.string.widget_forecast_description, spoken.joinToString("; ")))
        setOnClickPendingIntent(R.id.widget_forecast, weatherLocationIntent(context))
    }

    // Drawn at the view's exact size: fitXY then only maps pixels one to one.
    private fun forecastChart(context: Context, forecast: WidgetForecast, color: Int, heightDimen: Int): Bitmap {
        val resources = context.resources
        val density = resources.displayMetrics.density
        val width = resources.getDimensionPixelSize(R.dimen.widget_forecast_width)
        val height = resources.getDimensionPixelSize(heightDimen)
        val stroke = 1.5f * density
        val dot = 2.25f * density
        val inset = dot + stroke / 2
        var low = forecast.curve.minOf { it.tempC }
        var high = forecast.curve.maxOf { it.tempC }
        // A degree or two of change should not fill the whole height.
        if (high - low < MIN_CHART_SPAN_C) {
            val middle = (high + low) / 2
            low = middle - MIN_CHART_SPAN_C / 2
            high = middle + MIN_CHART_SPAN_C / 2
        }
        fun x(fraction: Float) = fraction * width
        fun y(tempC: Double) = (inset + (high - tempC) / (high - low) * (height - 2 * inset)).toFloat()

        // Bézier segments with the curve's slopes at both ends: the line tempAt follows
        val curve = forecast.curve
        val line = Path().apply { moveTo(x(curve[0].x), y(curve[0].tempC)) }
        for (index in 1..curve.lastIndex) {
            val previous = curve[index - 1]
            val point = curve[index]
            val third = (point.x - previous.x) / 3
            line.cubicTo(
                x(previous.x + third), y(previous.tempC + forecast.slopes[index - 1] * third),
                x(point.x - third), y(point.tempC - forecast.slopes[index] * third),
                x(point.x), y(point.tempC)
            )
        }
        val area = Path(line).apply {
            lineTo(x(curve.last().x), height.toFloat())
            lineTo(x(curve.first().x), height.toFloat())
            close()
        }
        val bitmap = createBitmap(width, height)
        Canvas(bitmap).apply {
            drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                    ColorUtils.setAlphaComponent(color, 0x50), ColorUtils.setAlphaComponent(color, 0),
                    Shader.TileMode.CLAMP)
            })
            drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                this.color = color
            })
            val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            for (hourX in WidgetForecast.HOUR_X) {
                forecast.tempAt(hourX)?.let { drawCircle(x(hourX), y(it), dot, dots) }
            }
        }
        return bitmap
    }

    private fun weatherIcon(condition: WeatherCondition, day: Boolean) = when (condition) {
        WeatherCondition.CLEAR -> if (day) R.drawable.widget_weather_clear_day else R.drawable.widget_weather_clear_night
        WeatherCondition.PARTLY_CLOUDY ->
            if (day) R.drawable.widget_weather_partly_day else R.drawable.widget_weather_partly_night
        WeatherCondition.CLOUDY -> R.drawable.widget_weather_cloudy
        WeatherCondition.FOG -> R.drawable.widget_weather_fog
        WeatherCondition.DRIZZLE -> R.drawable.widget_weather_drizzle
        WeatherCondition.RAIN -> R.drawable.widget_weather_rain
        WeatherCondition.SNOW -> R.drawable.widget_weather_snow
        WeatherCondition.THUNDERSTORM -> R.drawable.widget_weather_thunder
    }

    private fun conditionLabel(condition: WeatherCondition) = when (condition) {
        WeatherCondition.CLEAR -> R.string.weather_clear
        WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
        WeatherCondition.CLOUDY -> R.string.weather_cloudy
        WeatherCondition.FOG -> R.string.weather_fog
        WeatherCondition.DRIZZLE -> R.string.weather_drizzle
        WeatherCondition.RAIN -> R.string.weather_rain
        WeatherCondition.SNOW -> R.string.weather_snow
        WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
    }

    private fun RemoteViews.openAppOnClick(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        setOnClickPendingIntent(R.id.widget_panel, PendingIntent.getActivity(
            context, 20, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
    }

    private fun RemoteViews.source(
        context: Context,
        sourceId: Int,
        detailId: Int?,
        statusId: Int,
        title: String?,
        detail: String?,
        emptyTitle: Int,
        status: StreamStatus,
        palette: Palette,
        compactStatus: Boolean = false
    ) {
        val titleText = title ?: context.getString(emptyTitle)
        val detailText = detail ?: if (title == null) context.getString(R.string.widget_selection_hint) else ""
        setTextViewText(sourceId, titleText)
        if (detailId != null) {
            setTextViewText(detailId, detailText)
            setTextColor(detailId, palette.secondary)
        }
        setContentDescription(sourceId, listOf(titleText, detailText).filter { it.isNotBlank() }.joinToString(". "))
        setTextColor(sourceId, palette.text)
        val statusLabel = if (title == null) R.string.widget_not_selected else when (status) {
            StreamStatus.IDLE -> R.string.widget_stopped
            StreamStatus.LOADING -> R.string.widget_connecting
            StreamStatus.PLAYING -> R.string.widget_playing
            StreamStatus.BUFFERING -> R.string.widget_buffering
            StreamStatus.PAUSED -> R.string.widget_paused
            StreamStatus.RECONNECTING -> R.string.widget_reconnecting
            StreamStatus.ERROR -> R.string.widget_offline
        }
        val statusText = context.getString(statusLabel)
        setTextViewText(statusId, if (compactStatus) context.getString(R.string.widget_status_dot) else statusText)
        setContentDescription(statusId, "$titleText. $statusText")
        setTextColor(statusId, when {
            title == null -> palette.secondary
            status == StreamStatus.PLAYING -> palette.playing
            status == StreamStatus.ERROR -> palette.error
            status in listOf(StreamStatus.LOADING, StreamStatus.BUFFERING, StreamStatus.RECONNECTING) -> palette.warning
            else -> palette.secondary
        })
    }

    private data class Palette(
        val background: Int, val text: Int, val secondary: Int, val outline: Int,
        val atc: Int, val radio: Int, val playing: Int, val warning: Int, val error: Int
    )

    private fun palette(mode: ThemeMode) = when (mode) {
        ThemeMode.NORDIC -> Palette(
            R.drawable.widget_background_nordic, NordText.toArgb(), NordTextSec.toArgb(), NordOutline.toArgb(),
            NordPrimary.toArgb(), NordSecondary.toArgb(), NordTertiary.toArgb(), NordYellow.toArgb(), NordError.toArgb()
        )
        ThemeMode.DARK -> Palette(
            R.drawable.widget_background_dark, TextPrimary.toArgb(), TextSecondary.toArgb(), Outline.toArgb(),
            CyanNeon.toArgb(), MagentaNeon.toArgb(), GreenNeon.toArgb(), NordYellow.toArgb(), RedError.toArgb()
        )
        ThemeMode.LIGHT -> Palette(
            R.drawable.widget_background_light, TextPrimaryDay.toArgb(), TextSecDay.toArgb(), OutlineDay.toArgb(),
            CyanDay.toArgb(), MagentaDay.toArgb(), GreenDay.toArgb(), WarningDay.toArgb(), ErrorDay.toArgb()
        )
    }
}
