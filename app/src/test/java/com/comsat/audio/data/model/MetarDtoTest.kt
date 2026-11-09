package com.comsat.audio.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetarDtoTest {

    @Test
    fun mapsAllFields() {
        val dto = MetarDto(
            temp = 23.4, dewp = 14.1, wdir = "320", wspd = 8.0,
            altim = 1013.2, reportTime = "2026-10-29T05:00:00.000Z"
        )
        val atis = dto.toAtisData()!!
        assertEquals(23.4, atis.tempC, 0.001)
        assertEquals(14.1, atis.dewpointC, 0.001)
        assertEquals(1013, atis.qnhHpa)
        assertEquals(320, atis.windDirDeg)
        assertEquals(8, atis.windSpeedKt)
        assertEquals("2026-10-29T05:00:00.000Z", atis.observedUtc)
        assertEquals("OBS 0500Z", atis.observationText)
        assertEquals(java.time.Instant.parse("2026-10-29T05:00:00Z").toEpochMilli(), atis.observedAtMillis)
    }

    @Test
    fun variableWindDirectionMapsToNull() {
        val dto = MetarDto(temp = 20.0, dewp = 10.0, wdir = "VRB", wspd = 5.0)
        assertNull(dto.toAtisData()!!.windDirDeg)
        assertEquals(5, dto.toAtisData()!!.windSpeedKt)
    }

    @Test
    fun missingTempOrDewpointYieldsNull() {
        assertNull(MetarDto(temp = null, dewp = 10.0).toAtisData())
        assertNull(MetarDto(temp = 20.0, dewp = null).toAtisData())
    }

    @Test
    fun missingOptionalFieldsStayNull() {
        val atis = MetarDto(temp = 20.0, dewp = 10.0).toAtisData()!!
        assertNull(atis.qnhHpa)
        assertNull(atis.windDirDeg)
        assertNull(atis.windSpeedKt)
        assertNull(atis.observedUtc)
    }

    // wdir arrives as a JSON number normally and as the string "VRB" for
    // variable wind; the DTO declares String so Gson accepts both
    @Test
    fun gsonParsesNumericAndStringWdir() {
        val gson = Gson()
        val numeric = gson.fromJson(
            """{"temp":20.0,"dewp":10.0,"wdir":320,"wspd":8.0}""", MetarDto::class.java
        )
        assertEquals(320, numeric.toAtisData()!!.windDirDeg)

        val variable = gson.fromJson(
            """{"temp":20.0,"dewp":10.0,"wdir":"VRB","wspd":8.0}""", MetarDto::class.java
        )
        assertNull(variable.toAtisData()!!.windDirDeg)
    }

    @Test fun observationEpochWinsAndStalenessUsesObservationTime() {
        val atis = MetarDto(temp = 20.0, dewp = 10.0, obsTime = 10_000,
            reportTime = "2026-10-29T05:00:00.000Z").toAtisData()!!
        assertEquals(10_000_000L, atis.observedAtMillis)
        assertFalse(atis.isStale(10_000_000 + 3 * 60 * 60_000L))
        assertTrue(atis.isStale(10_000_001 + 3 * 60 * 60_000L))
        val unknown = MetarDto(temp = 20.0, dewp = 10.0, reportTime = "bad").toAtisData()!!
        assertEquals("OBS ----", unknown.observationText)
        assertTrue(unknown.isStale(0))
    }
}
