package com.comsat.audio.data.repository

import com.comsat.audio.data.model.SomaStation

/** Catalog metadata wins, but a saved descriptor can play before it arrives. */
fun restoredStation(settings: AppSettings, catalog: List<SomaStation>): SomaStation? {
    val id = settings.stationId ?: return null
    return catalog.find { it.id == id }
        ?: settings.stationSnapshot?.takeIf { it.id == id }
        // Migration for installations that only saved id/title/network.
        ?: id.takeIf { it.matches(Regex("[a-zA-Z0-9_-]+")) && it != "rain-radio" }?.let {
            SomaStation(it, settings.stationTitle ?: it, "", "",
                "https://ice1.somafm.com/$it-128-mp3", "",
                network = settings.stationNetwork ?: "SOMAFM")
        }
}
