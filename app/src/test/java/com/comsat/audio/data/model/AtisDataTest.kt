package com.comsat.audio.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AtisDataTest {

    private fun atis(
        tempC: Double = 20.0,
        dewpointC: Double = 10.0,
        qnhHpa: Int? = 1013,
        windDirDeg: Int? = 320,
        windSpeedKt: Int? = 8
    ) = AtisData(tempC, dewpointC, qnhHpa, windDirDeg, windSpeedKt, observedUtc = null)

    // ─── Magnus relative humidity ─────────────────────────────────────────────

    @Test
    fun humidityIs100WhenTempEqualsDewpoint() {
        assertEquals(100.0, relativeHumidity(20.0, 20.0), 0.01)
    }

    @Test
    fun humidityForKnownPair20over10() {
        // Magnus with constants 17.625 / 243.04 gives 52.54%
        assertEquals(52.54, relativeHumidity(20.0, 10.0), 0.1)
    }

    @Test
    fun humidityForKnownPair30over0() {
        assertEquals(14.42, relativeHumidity(30.0, 0.0), 0.1)
    }

    @Test
    fun humidityPctRoundsToInt() {
        assertEquals(53, atis(tempC = 20.0, dewpointC = 10.0).humidityPct)
    }

    // ─── Display formatting ───────────────────────────────────────────────────

    @Test
    fun tempAndDewRoundToWholeDegrees() {
        val a = atis(tempC = 23.4, dewpointC = -5.4)
        assertEquals("23°", a.tempText)
        assertEquals("-5°", a.dewText)
    }

    @Test
    fun rhTextHasPercentSign() {
        assertEquals("53%", atis(tempC = 20.0, dewpointC = 10.0).rhText)
    }

    @Test
    fun qnhTextUsesQPrefix() {
        assertEquals("Q1013", atis(qnhHpa = 1013).qnhText)
    }

    @Test
    fun qnhTextPlaceholderWhenMissing() {
        assertEquals("Q----", atis(qnhHpa = null).qnhText)
    }

    @Test
    fun windTextZeroPadsSpeed() {
        assertEquals("320°/08KT", atis(windDirDeg = 320, windSpeedKt = 8).windText)
    }

    @Test
    fun windTextCalmWhenSpeedZero() {
        assertEquals("CALM", atis(windSpeedKt = 0).windText)
    }

    @Test
    fun windTextVariableWhenDirectionMissing() {
        assertEquals("VRB/05KT", atis(windDirDeg = null, windSpeedKt = 5).windText)
    }

    @Test
    fun windTextPlaceholderWhenSpeedMissing() {
        assertEquals("---", atis(windSpeedKt = null).windText)
    }

    @Test
    fun windTextZeroPadsDirectionToThreeDigits() {
        assertEquals("080°/08KT", atis(windDirDeg = 80, windSpeedKt = 8).windText)
    }

    @Test
    fun humidityPctClampsTo100() {
        assertEquals(100, atis(tempC = 20.0, dewpointC = 20.2).humidityPct)
    }
}
