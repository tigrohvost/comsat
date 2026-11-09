package com.comsat.audio.data.model

import org.junit.Assert.*
import org.junit.Test

class MixStorageTest {
    private val station = SomaStation("rain-radio", "Rain Radio", "", "rain", "", "",
        network = "RAIN NET", nextTrackApiBase = "https://rain.example")

    @Test fun roundTripPreservesChainedStationAndVolumes() {
        val mix = SavedMix(name = "Вечер", airportIcao = "KJFK", station = station,
            atcVolume = 0.25f, somaVolume = 0.75f)
        assertEquals(listOf(mix), MixStorage.mixes(MixStorage.encode(listOf(mix))))
        assertEquals(station, MixStorage.station(MixStorage.encode(station)))
    }

    @Test fun corruptIncompleteAndInvalidRecordsAreIgnored() {
        assertTrue(MixStorage.mixes("broken").isEmpty())
        assertTrue(MixStorage.mixes("[null,{}]").isEmpty())
        assertNull(MixStorage.station("{}"))
        assertNull(MixStorage.station(null))
        val invalid = SavedMix(name = "Bad volume", station = station, atcVolume = -1f)
        assertTrue(MixStorage.mixes(MixStorage.encode(listOf(invalid))).isEmpty())
    }
}
