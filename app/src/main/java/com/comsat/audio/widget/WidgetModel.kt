package com.comsat.audio.widget

import com.comsat.audio.data.model.Airport
import com.comsat.audio.data.model.StreamStatus
import com.comsat.audio.data.repository.AppSettings
import com.comsat.audio.ui.theme.ThemeMode

data class WidgetModel(
    val airportIcao: String?,
    val airportName: String?,
    val stationTitle: String?,
    val stationNetwork: String?,
    val atcStatus: StreamStatus,
    val somaStatus: StreamStatus,
    val themeMode: ThemeMode
)

fun widgetModel(
    settings: AppSettings,
    playback: PlaybackSnapshot,
    airport: Airport?
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
        themeMode = settings.themeMode
    )
}
