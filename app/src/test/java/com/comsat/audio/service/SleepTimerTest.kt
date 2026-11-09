package com.comsat.audio.service

import org.junit.Assert.*
import org.junit.Test

class SleepTimerTest {
    @Test fun deadlineIncludesDeepSleepAndFadesOnlyAtTheEnd() {
        val timer = SleepTimer()
        timer.start(10_000, 60_000)
        assertEquals(60_000L, timer.remaining(10_000))
        assertEquals(1f, timer.gain(40_000), 0f)
        assertEquals(0.5f, timer.gain(55_000), 0.001f)
        assertEquals(0L, timer.remaining(100_000))
        assertEquals(0f, timer.gain(100_000), 0f)
    }

    @Test fun cancelAndRestartRestoreUserGain() {
        val timer = SleepTimer()
        timer.start(0, 15_000)
        timer.cancel()
        assertNull(timer.remaining(20_000))
        assertEquals(1f, timer.gain(20_000), 0f)
        timer.start(20_000, 60_000)
        assertEquals(80_000L, timer.deadlineMillis)
    }

    @Test fun displayRoundsUpAndBackoffIsBounded() {
        assertEquals("SLP 15:00", sleepTimeText(899_999))
        assertEquals("SLP 0:01", sleepTimeText(1))
        assertEquals(listOf(5_000L, 10_000L, 30_000L, 30_000L), (1..4).map(::retryDelayMillis))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDurationIsRejected() { SleepTimer().start(0, 0) }
}
