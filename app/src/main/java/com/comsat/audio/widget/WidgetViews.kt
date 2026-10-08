package com.comsat.audio.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.comsat.audio.MainActivity
import com.comsat.audio.R
import com.comsat.audio.data.model.StreamStatus
import com.comsat.audio.ui.theme.*

/** A small, event-driven display: no network requests or animation in the launcher. */
object WidgetViews {
    fun render(context: Context, model: WidgetModel, options: Bundle): RemoteViews {
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
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            setOnClickPendingIntent(R.id.widget_panel, PendingIntent.getActivity(
                context, 20, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
        }
    }

    private fun RemoteViews.source(
        context: Context,
        sourceId: Int,
        detailId: Int,
        statusId: Int,
        title: String?,
        detail: String?,
        emptyTitle: Int,
        status: StreamStatus,
        palette: Palette
    ) {
        val titleText = title ?: context.getString(emptyTitle)
        val detailText = detail ?: if (title == null) context.getString(R.string.widget_selection_hint) else ""
        setTextViewText(sourceId, titleText)
        setTextViewText(detailId, detailText)
        setContentDescription(sourceId, listOf(titleText, detailText).filter { it.isNotBlank() }.joinToString(". "))
        setTextColor(sourceId, palette.text)
        setTextColor(detailId, palette.secondary)
        val statusLabel = if (title == null) R.string.widget_not_selected else when (status) {
            StreamStatus.IDLE -> R.string.widget_stopped
            StreamStatus.LOADING -> R.string.widget_connecting
            StreamStatus.PLAYING -> R.string.widget_playing
            StreamStatus.BUFFERING -> R.string.widget_buffering
            StreamStatus.PAUSED -> R.string.widget_paused
            StreamStatus.RECONNECTING -> R.string.widget_reconnecting
            StreamStatus.ERROR -> R.string.widget_offline
        }
        setTextViewText(statusId, context.getString(statusLabel))
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
