package com.comsat.audio.widget

import com.comsat.audio.data.model.Airport
import com.comsat.audio.data.model.AtcFeed
import com.comsat.audio.data.model.StreamStatus
import com.comsat.audio.data.repository.AppSettings
import com.comsat.audio.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetModelTest {
    private val airport = Airport(
        icao = "KJFK",
        name = "John F. Kennedy Intl",
        city = "New York",
        country = "US",
        region = "North America",
        feeds = listOf(AtcFeed("kjfk_twr", "Tower"))
    )

    @Test
    fun `a new install has empty selections and stopped channels`() {
        val model = widgetModel(AppSettings(), PlaybackSnapshot(), null)

        assertNull(model.airportIcao)
        assertNull(model.airportName)
        assertNull(model.stationTitle)
        assertNull(model.stationNetwork)
        assertEquals(StreamStatus.IDLE, model.atcStatus)
        assertEquals(StreamStatus.IDLE, model.somaStatus)
        assertEquals(ThemeMode.NORDIC, model.themeMode)
    }

    @Test
    fun `cold state restores names without requiring the station directory`() {
        val settings = AppSettings(
            airportIcao = "KJFK",
            stationId = "groovesalad",
            stationTitle = "Groove Salad",
            stationNetwork = "SOMAFM",
            themeMode = ThemeMode.LIGHT
        )
        val model = widgetModel(settings, PlaybackSnapshotRepository().snapshot.value, airport)

        assertEquals("KJFK", model.airportIcao)
        assertEquals("John F. Kennedy Intl", model.airportName)
        assertEquals("Groove Salad", model.stationTitle)
        assertEquals("SOMAFM", model.stationNetwork)
        assertEquals(StreamStatus.IDLE, model.atcStatus)
        assertEquals(StreamStatus.IDLE, model.somaStatus)
        assertEquals(ThemeMode.LIGHT, model.themeMode)
    }

    @Test
    fun `legacy settings retain a recognizable Rain Radio selection`() {
        val model = widgetModel(AppSettings(stationId = "rain-radio"), PlaybackSnapshot(), null)

        assertEquals("Rain Radio", model.stationTitle)
        assertEquals("RAIN NET", model.stationNetwork)
    }

    @Test
    fun `legacy station ID remains visible until its title is available`() {
        val model = widgetModel(AppSettings(stationId = "dronezone"), PlaybackSnapshot(), null)

        assertEquals("dronezone", model.stationTitle)
        assertEquals("SOMAFM", model.stationNetwork)
    }

    @Test
    fun `cleared selections cannot borrow names or playback from stale state`() {
        val model = widgetModel(
            AppSettings(stationTitle = "Old station", stationNetwork = "Old network"),
            PlaybackSnapshot(StreamStatus.PLAYING, StreamStatus.PLAYING),
            airport
        )

        assertNull(model.airportName)
        assertNull(model.stationTitle)
        assertNull(model.stationNetwork)
        assertEquals(StreamStatus.IDLE, model.atcStatus)
        assertEquals(StreamStatus.IDLE, model.somaStatus)
    }

    @Test
    fun `missing airport in a changed catalog preserves its saved ICAO`() {
        val model = widgetModel(AppSettings(airportIcao = "XXXX"), PlaybackSnapshot(), airport)

        assertEquals("XXXX", model.airportIcao)
        assertNull(model.airportName)
    }

    @Test
    fun `preparing ATC does not overwrite ongoing ambient playback`() {
        val playback = PlaybackSnapshotRepository()
        playback.setAtcPreparing(true)
        playback.updatePlayback(StreamStatus.IDLE, StreamStatus.PLAYING)
        val settings = AppSettings(airportIcao = "KJFK", stationId = "rain-radio")

        val preparing = widgetModel(settings, playback.snapshot.value, airport)
        assertEquals(StreamStatus.LOADING, preparing.atcStatus)
        assertEquals(StreamStatus.PLAYING, preparing.somaStatus)

        playback.setAtcPreparing(false)
        val cancelled = widgetModel(settings, playback.snapshot.value, airport)
        assertEquals(StreamStatus.IDLE, cancelled.atcStatus)
        assertEquals(StreamStatus.PLAYING, cancelled.somaStatus)
    }

    @Test
    fun `service teardown clears playback and pending requests`() {
        val playback = PlaybackSnapshotRepository()
        playback.updatePlayback(StreamStatus.PLAYING, StreamStatus.RECONNECTING)
        playback.setSomaPreparing(true)

        playback.clearPlayback()

        assertEquals(PlaybackSnapshot(), playback.snapshot.value)
    }

    @Test
    fun `a new process cannot inherit a live playback claim`() {
        val oldProcess = PlaybackSnapshotRepository()
        oldProcess.updatePlayback(StreamStatus.PLAYING, StreamStatus.PLAYING)

        assertEquals(PlaybackSnapshot(), PlaybackSnapshotRepository().snapshot.value)
    }
}
