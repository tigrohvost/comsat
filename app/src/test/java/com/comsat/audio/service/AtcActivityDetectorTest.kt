package com.comsat.audio.service

import org.junit.Assert.*
import org.junit.Test

class AtcActivityDetectorTest {
    @Test fun silenceNoiseAndShortClickDoNotDuck() {
        val detector = AtcActivityDetector()
        for (time in 0L..10_000L step 100) assertEquals(0f, detector.update(0.005f, time), 0f)
        detector.update(0.3f, 10_100)
        assertEquals(0f, detector.update(0.005f, 10_200), 0f)
    }

    @Test fun speechHoldsThenReleasesSmoothly() {
        val detector = AtcActivityDetector()
        for (time in 0L..1_000L step 100) detector.update(0.15f, time)
        assertEquals(1f, detector.activity, 0f)
        for (time in 1_100L..2_300L step 100) detector.update(0.001f, time)
        assertEquals(1f, detector.activity, 0f)
        for (time in 2_400L..2_800L step 100) detector.update(0.001f, time)
        assertTrue(detector.activity in 0.1f..0.9f)
        for (time in 2_900L..4_000L step 100) detector.update(0.001f, time)
        assertEquals(0f, detector.activity, 0f)
    }

    @Test fun sustainedNoiseBecomesTheNoiseFloor() {
        val detector = AtcActivityDetector()
        for (time in 0L..60_000L step 100) detector.update(0.04f, time)
        assertEquals(0f, detector.activity, 0f)
        detector.reset()
        assertEquals(0f, detector.activity, 0f)
    }
}
