package com.comsat.audio.data.repository

import com.comsat.audio.data.model.SomaStation
import org.junit.Assert.*
import org.junit.Test

class StationRestorationTest {
    private val station = SomaStation("groovesalad", "Groove Salad", "Ambient", "ambient",
        "https://saved.example/stream", "https://saved.example/image.jpg")

    @Test fun offlineColdStartKeepsTheWholeStation() {
        val settings = AppSettings(stationId = station.id, stationSnapshot = station)
        assertEquals(station, restoredStation(settings, emptyList()))
    }

    @Test fun catalogReplacesSnapshotMetadata() {
        val fresh = station.copy(imageUrl = "https://fresh.example/art.jpg", listeners = 100)
        assertEquals(fresh, restoredStation(AppSettings(stationId = station.id,
            stationSnapshot = station), listOf(fresh)))
    }

    @Test fun oldSettingsMigrateAndUnrelatedSnapshotIsIgnored() {
        val migrated = restoredStation(AppSettings(stationId = "dronezone", stationTitle = "Drone Zone",
            stationSnapshot = station), emptyList())!!
        assertEquals("Drone Zone", migrated.title)
        assertEquals("https://ice1.somafm.com/dronezone-128-mp3", migrated.streamUrl)
        assertNull(restoredStation(AppSettings(), listOf(station)))
    }
}
