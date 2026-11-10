package com.comsat.audio.service

import java.util.Random
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class AtcActivityDetectorTest {
    // Speech as the level meter sees it, one value per 100 ms: syllables and short gaps.
    private val speech = floatArrayOf(0.16f, 0.11f, 0.19f, 0.07f, 0.14f, 0.02f, 0.17f, 0.12f)
    // A quiet talker on a receiver that goes silent under a carrier.
    private val quiet = floatArrayOf(.08f, .09f, .085f, .025f, .006f, .012f, .07f, .095f, .08f, .01f, .02f, .007f)

    /** Feeds `millis` of levels starting at `from`; `level` gets the time since `from`. */
    private fun AtcActivityDetector.feed(from: Long, millis: Long, step: Long = 100, level: (Long) -> Float): Long {
        var time = from
        while (time < from + millis) {
            update(level(time - from), time)
            time += step
        }
        return time
    }

    private fun FloatArray.at(elapsed: Long) = this[(elapsed / 100 % size).toInt()]

    /** The detector as released up to 1.7.2: its floor followed every level. */
    private class Released {
        private var noiseFloor = 0.003f
        private var candidateSince: Long? = null
        private var heldUntil = 0L
        private var previousAt: Long? = null
        var activity = 0f

        fun update(rms: Float, nowMillis: Long): Float {
            val dt = previousAt?.let { (nowMillis - it).coerceIn(0, 500) } ?: 100L
            previousAt = nowMillis
            val level = if (rms.isFinite()) rms.coerceIn(0f, 1f) else 0f
            if (level > max(0.015f, noiseFloor * 2.8f)) {
                val since = candidateSince ?: nowMillis.also { candidateSince = it }
                if (nowMillis - since >= 150) heldUntil = nowMillis + 1_500
            } else {
                candidateSince = null
            }
            noiseFloor += (level - noiseFloor) * (1f - exp(-dt / if (level > noiseFloor) 8_000f else 2_000f))
            activity = if (nowMillis < heldUntil) (activity + dt / 150f).coerceAtMost(1f)
                else (activity - dt / 1_000f).coerceAtLeast(0f)
            return activity
        }
    }

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

    @Test fun twoClicksApartDoNotDuck() {
        val detector = AtcActivityDetector()
        for (time in 0L..5_000L step 100) detector.update(0.005f, time)
        detector.update(0.3f, 5_100)
        detector.update(0.005f, 5_200)
        detector.update(0.005f, 5_300)
        detector.update(0.3f, 5_400)
        for (time in 5_500L..6_000L step 100) assertEquals(0f, detector.update(0.005f, time), 0f)
    }

    @Test fun longTransmissionKeepsTheMusicDownUntilItEnds() {
        // Before 1.7.3 the floor followed the transmission itself: after about four
        // seconds speech stopped counting as speech and the music came back over it.
        val detector = AtcActivityDetector()
        val released = Released()
        var lostBefore = 0
        var time = 0L
        while (time <= 60_000L) {
            detector.update(speech.at(time), time)
            if (released.update(speech.at(time), time) < 1f && time >= 1_000L) lostBefore++
            if (time >= 1_000L) assertEquals("at $time ms", 1f, detector.activity, 0f)
            time += 100
        }
        assertTrue("the released detector lost most of this transmission", lostBefore > 400)
        // After it ends the hold runs out and the music comes back.
        detector.feed(time, 4_000) { 0.004f }
        assertEquals(0f, detector.activity, 0f)
    }

    @Test fun speechOverElevatedNoiseIsHeldToItsEnd() {
        val detector = AtcActivityDetector()
        // A minute of open-squelch noise becomes the floor…
        var time = detector.feed(0, 60_000) { 0.04f }
        assertEquals(0f, detector.activity, 0f)
        // …and a transmission on top of that noise is heard as one, to its end.
        val start = time
        while (time < start + 20_000L) {
            val voice = speech.at(time - start)
            detector.update(sqrt(voice * voice + 0.04f * 0.04f), time)
            if (time >= start + 1_000L) assertEquals("at ${time - start} ms", 1f, detector.activity, 0f)
            time += 100
        }
        detector.feed(time, 4_000) { 0.04f }
        assertEquals(0f, detector.activity, 0f)
    }

    @Test fun evenSpeechWithRareGapsIsNotLearnedAsNoise() {
        // A steady voice with two short dips in a second is still speech, not a carrier.
        val voice = floatArrayOf(.12f, .11f, .13f, .12f, .14f, .06f, .12f, .11f, .12f, .07f)
        for (noise in floatArrayOf(0f, 0.04f)) {
            val detector = AtcActivityDetector()
            var time = if (noise > 0f) detector.feed(0, 60_000) { noise } else 0L
            val start = time
            while (time < start + 40_000L) {
                val level = voice.at(time - start)
                detector.update(sqrt(level * level + noise * noise), time)
                if (time >= start + 1_000L) {
                    assertEquals("noise $noise at ${time - start} ms", 1f, detector.activity, 0f)
                }
                time += 100
            }
        }
    }

    @Test fun noiseIsLearnedAsQuicklyAsBefore() {
        // Steady hiss, hiss that wanders by fifteen percent, hiss with a click every second,
        // hiss that appears later, and polling as slow as every 600 ms: the music is back when it used to be.
        val cases = listOf<Pair<Long, (Long) -> Float>>(
            100L to { _ -> 0.04f },
            100L to { t -> 0.04f * (if (t / 100 % 2 == 0L) 1.15f else 0.85f) },
            100L to { t -> if (t % 1_000L == 500L) 0.3f else 0.04f },
            100L to { t -> if (t < 30_000L) 0.005f else 0.05f },
            250L to { _ -> 0.04f },
            500L to { _ -> 0.04f },
            600L to { _ -> 0.04f },
        )
        cases.forEachIndexed { index, (step, level) ->
            val detector = AtcActivityDetector()
            val released = Released()
            var backNow = 0L
            var backBefore = 0L
            var time = 0L
            while (time <= 60_000L) {
                if (detector.update(level(time), time) > 0f) backNow = time + step
                if (released.update(level(time), time) > 0f) backBefore = time + step
                time += step
            }
            assertTrue("case $index: the music was down at all", backBefore > 0L)
            assertTrue("case $index: back at $backNow ms, before at $backBefore ms", backNow <= backBefore + 300)
        }
    }

    @Test fun speechOverNoiseThatAppearedLaterStillLowersTheMusic() {
        val detector = AtcActivityDetector()
        var time = detector.feed(0, 30_000) { 0.005f }
        time = detector.feed(time, 8_000) { 0.05f }
        assertEquals(0f, detector.activity, 0f)
        detector.feed(time, 3_000) { sqrt(speech.at(it) * speech.at(it) + 0.05f * 0.05f) * 1.5f }
        assertEquals(1f, detector.activity, 0f)
    }

    @Test fun noiseThatStopsIsForgotten() {
        // After a noisy minute the squelch closes; quiet speech is heard again.
        val detector = AtcActivityDetector()
        var time = detector.feed(0, 60_000) { 0.04f }
        time = detector.feed(time, 20_000) { 0.002f }
        val voice = floatArrayOf(.05f, .03f, .06f, .02f, .05f, .004f, .06f, .04f)
        detector.feed(time, 5_000) { voice.at(it) }
        assertEquals(1f, detector.activity, 0f)
    }

    @Test fun quietTransmissionsOnAnOpenSquelchReceiverAreHeldAlmostWhole() {
        // A receiver whose noise is louder between transmissions than under a carrier,
        // quiet talkers, pauses barely longer than the hold (the scenario that sank an
        // earlier rework). The first transmission needs the floor to come down; every
        // later one is held for at least nine tenths of its length.
        for (keyUp in longArrayOf(0, 800)) {
            for (pauses in listOf(longArrayOf(2_600), longArrayOf(2_600, 2_800, 2_700, 2_900, 2_600))) {
                val detector = AtcActivityDetector()
                var time = detector.feed(0, 60_000) { 0.04f }
                repeat(40) { cycle ->
                    time = detector.feed(time, keyUp) { 0.006f }
                    var full = 0
                    val start = time
                    while (time < start + 10_000L) {
                        if (detector.update(quiet.at(time - start), time) == 1f) full++
                        time += 100
                    }
                    assertTrue("key-up $keyUp, cycle $cycle: $full of 100", full >= if (cycle == 0) 30 else 90)
                    time = detector.feed(time, pauses[cycle % pauses.size]) {
                        0.04f * (if (it / 100 % 2 == 0L) 1.02f else 0.98f)
                    }
                }
            }
        }
    }

    @Test fun quietVoiceAfterStartupHissIsNotLost() {
        // Review case: four seconds of hiss, a quieter carrier, then a quiet voice. A floor
        // that learned the hiss faster than before would have missed the whole transmission.
        val detector = AtcActivityDetector()
        var time = detector.feed(0, 4_000) { 0.04f }
        time = detector.feed(time, 200) { 0.006f }
        val start = time
        while (time < start + 4_000L) {
            assertEquals("at ${time - start} ms", 1f, detector.update(quiet.at(time - start) * 0.6f, time), 0f)
            time += 100
        }
    }

    @Test fun neverLowersTheMusicLessThanTheReleasedDetector() {
        // The floor never exceeds the one that followed everything, so whatever the
        // receiver does, the music is down at least whenever it was before 1.7.3.
        val random = Random(20261110)
        repeat(300) { trial ->
            val detector = AtcActivityDetector()
            val released = Released()
            val kind = trial % 4
            var time = 0L
            repeat(200 + random.nextInt(2_800)) { tick ->
                val level = when (kind) {
                    0 -> 0.04f * (1f + 0.1f * random.nextGaussian().toFloat())
                    1 -> if (tick % (1 + random.nextInt(30)) == 0) floatArrayOf(0.003f, 0.05f, 0.015f, 0.2f)[random.nextInt(4)] else 0.02f
                    2 -> floatArrayOf(0.003f, 0.006f, 0.04f, 0.12f, 0.2f, 0.08f)[random.nextInt(6)] *
                        exp(0.5f * random.nextGaussian().toFloat())
                    else -> if (random.nextInt(10) < 3) quiet[random.nextInt(quiet.size)] * (0.5f + random.nextFloat()) else 0.04f
                }
                // Regular ticks, jitter, a repeated timestamp, slow polling and a long stall.
                time += longArrayOf(100, 100, 100, 100, 90, 110, 250, 400, 30, 0, 600, 5_000)[random.nextInt(12)]
                val now = detector.update(level, time)
                val before = released.update(level, time)
                assertTrue("trial $trial at $time ms: $now < $before", now >= before)
            }
        }
    }

    @Test fun aHoldWithNoEndIsNotATransmission() {
        // A noisy carrier with dropouts never looks flat. After two minutes without the
        // music coming back it is learned anyway, within seconds, and the music stays back.
        val detector = AtcActivityDetector()
        var back = -1L
        var time = 0L
        while (time < 240_000L) {
            val activity = detector.update(if (time / 100 % 7 == 3L) 0.001f else 0.05f, time)
            if (time in 1_000L..119_000L) assertEquals("at $time ms", 1f, activity, 0f)
            if (activity == 0f && back < 0 && time > 1_000L) back = time
            if (back >= 0) assertEquals("at $time ms", 0f, activity, 0f)
            time += 100
        }
        assertTrue("music back at $back ms", back in 120_000L..130_000L)
    }

    @Test fun burstsThatLetTheHoldLapseAreLearnedToo() {
        // Review case: one-second bursts with 1.6 s between them. The hold lapses for a
        // moment each time but the music never comes back up, so the two minutes keep running.
        val detector = AtcActivityDetector()
        var back = -1L
        var time = 0L
        while (time < 520_000L) {
            val activity = detector.update(if (time % 2_600L < 1_000L) 0.05f else 0.015f, time)
            if (time in 5_000L..110_000L) assertTrue("at $time ms", activity > 0.5f)
            if (activity == 0f && back < 0 && time > 5_000L) back = time
            if (back >= 0) assertEquals("at $time ms", 0f, activity, 0f)
            time += 100
        }
        assertTrue("music back at $back ms", back in 120_000L..130_000L)
    }

    @Test fun aPauseThatBringsTheMusicBackRestartsTheTwoMinutes() {
        // Review case: six seconds of speech, then 2.2 s of silence, again and again. The
        // music comes all the way back for one tick before every transmission, so none,
        // however late, may be treated as part of an endless one: each is held to its end.
        val detector = AtcActivityDetector()
        var time = 0L
        var returns = 0
        repeat(40) { cycle ->
            var down = 0
            val start = time
            while (time < start + 6_000L) {
                val activity = detector.update(speech.at(time - start), time)
                if (activity >= 0.5f) down++
                if (activity == 0f && cycle > 0) returns++
                time += 100
            }
            assertTrue("transmission $cycle at $start ms: $down of 60", down >= 55)
            time = detector.feed(time, 2_200) { 0.003f }
        }
        assertEquals("the music came all the way back before every later transmission", 39, returns)
    }

    @Test fun afterAnEndlessBroadcastAPauseBringsTheGateBack() {
        val detector = AtcActivityDetector()
        var time = detector.feed(0, 200_000) { speech.at(it) }
        assertEquals(0f, detector.activity, 0f)
        time = detector.feed(time, 6_000) { 0.003f }
        detector.feed(time, 3_000) { speech.at(it) }
        assertEquals(1f, detector.activity, 0f)
    }

    @Test fun hissThatFollowsATransmissionKeepsTheMusicDownForSecondsNotMore() {
        // The price of holding a transmission to its end: the floor did not rise during
        // it, so hiss that opens right after it has to be learned first. The music is
        // mostly back within four and a half seconds, fully within five, and stays back.
        val detector = AtcActivityDetector()
        var time = detector.feed(0, 5_000) { 0.003f }
        time = detector.feed(time, 20_000) { speech.at(it) }
        val end = time
        while (time < end + 20_000L) {
            val activity = detector.update(0.04f, time)
            if (time >= end + 4_500L) assertTrue("at ${time - end} ms: $activity", activity < 0.5f)
            if (time >= end + 5_000L) assertEquals("at ${time - end} ms", 0f, activity, 0f)
            time += 100
        }
    }

    @Test fun flatMeansFourFifthsOfASecondOrMoreWithinTheBand() {
        fun levels(inside: Int, outside: Int) = List(outside) { 0.2f } + List(inside) { 0.04f }
        // Nine of twelve is three quarters: not flat. Ten of twelve is.
        assertFalse(AtcActivityDetector.isFlat(levels(9, 3), 1_100, 0.04f))
        assertTrue(AtcActivityDetector.isFlat(levels(10, 2), 1_100, 0.04f))
        assertTrue(AtcActivityDetector.isFlat(levels(16, 4), 1_900, 0.04f))
        assertFalse(AtcActivityDetector.isFlat(levels(15, 5), 1_900, 0.04f))
        // The band is one and a half times the lowest level, plus a little.
        assertTrue(AtcActivityDetector.isFlat(List(10) { if (it % 2 == 0) 0.04f else 0.061f }, 900, 0.061f))
        assertFalse(AtcActivityDetector.isFlat(List(10) { if (it % 2 == 0) 0.04f else 0.07f }, 900, 0.04f))
        // The newest level must be inside it: the first loud syllable is not flat.
        assertFalse(AtcActivityDetector.isFlat(levels(20, 0), 1_900, 0.12f))
        // The band's edge itself is inside.
        val edge = 0.04f * 1.5f + 0.002f
        assertTrue(AtcActivityDetector.isFlat(List(10) { if (it == 0) 0.04f else edge }, 900, edge))
        assertFalse(AtcActivityDetector.isFlat(List(10) { if (it == 0) 0.04f else edge }, 900, Math.nextUp(edge)))
        // Less than 900 ms of history, or fewer than four samples, decides nothing.
        assertFalse(AtcActivityDetector.isFlat(levels(9, 0), 899, 0.04f))
        assertTrue(AtcActivityDetector.isFlat(levels(9, 0), 900, 0.04f))
        assertFalse(AtcActivityDetector.isFlat(levels(3, 0), 1_800, 0.04f))
        assertTrue(AtcActivityDetector.isFlat(levels(4, 0), 1_800, 0.04f))
        // With four samples all four must be inside.
        assertFalse(AtcActivityDetector.isFlat(levels(3, 1), 1_800, 0.04f))
    }

    @Test fun invalidLevelsAreHarmlessAndResetStartsAfresh() {
        val detector = AtcActivityDetector()
        var time = 0L
        // Not a number, an infinity and anything negative count as silence; a finite level over full scale as full scale.
        for ((odd, plain) in listOf(Float.NaN to 0f, -1f to 0f, Float.NEGATIVE_INFINITY to 0f, Float.POSITIVE_INFINITY to 0f, 5f to 1f)) {
            val a = AtcActivityDetector()
            val b = AtcActivityDetector()
            for (tick in 0L..8_000L step 100) {
                val voiced = tick in 2_000L..5_000L
                assertEquals("$odd at $tick ms", b.update(if (voiced) plain else 0.02f, tick), a.update(if (voiced) odd else 0.02f, tick), 0f)
            }
        }
        for (level in floatArrayOf(Float.NaN, -1f, Float.POSITIVE_INFINITY, 5f, 0.1f, Float.NEGATIVE_INFINITY)) {
            repeat(5) {
                val activity = detector.update(level, time)
                assertTrue(activity in 0f..1f)
                time += 100
            }
        }
        detector.feed(time, 30_000) { speech.at(it) }
        detector.reset()
        val fresh = AtcActivityDetector()
        for (elapsed in 0L..20_000L step 100) {
            val level = if (elapsed < 8_000L) 0.04f else quiet.at(elapsed)
            assertEquals("at $elapsed ms", fresh.update(level, elapsed), detector.update(level, 500_000L + elapsed), 0f)
        }
    }
}
