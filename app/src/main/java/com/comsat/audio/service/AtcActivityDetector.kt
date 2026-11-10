package com.comsat.audio.service

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Adaptive level gate: reject short squelch clicks, hold speech, then release. */
class AtcActivityDetector {
    private var noiseFloor = 0.003f
    // The floor as it was learned up to 1.7.2: it followed every level, speech included.
    private var trackedFloor = 0.003f
    private var candidateSince: Long? = null
    private var heldUntil = 0L
    private var activeSince: Long? = null
    private var previousAt: Long? = null
    // The levels of the last two seconds: speech has syllables and gaps, noise is flat.
    private val recent = ArrayDeque<Sample>()
    var activity: Float = 0f
        private set

    fun reset() {
        noiseFloor = 0.003f
        trackedFloor = 0.003f
        candidateSince = null
        heldUntil = 0
        activeSince = null
        previousAt = null
        recent.clear()
        activity = 0f
    }

    /** [nowMillis] is a clock that never goes back (the service passes elapsedRealtime). */
    fun update(rms: Float, nowMillis: Long): Float {
        val dt = previousAt?.let { (nowMillis - it).coerceIn(0, 500) } ?: 100L
        previousAt = nowMillis
        val level = if (rms.isFinite()) rms.coerceIn(0f, 1f) else 0f
        val aboveNoise = level > max(0.015f, noiseFloor * 2.8f)
        if (aboveNoise) {
            val since = candidateSince ?: nowMillis.also { candidateSince = it }
            if (nowMillis - since >= 150) heldUntil = nowMillis + 1_500
        } else {
            candidateSince = null
        }
        val held = nowMillis < heldUntil
        // How long the music has been down without coming all the way back. A hold that
        // lapses for a moment between bursts does not restart this clock (see the end).
        if (held && activeSince == null) activeSince = nowMillis
        recent.addLast(Sample(nowMillis, level))
        while (recent.first().at < nowMillis - WINDOW_MS) recent.removeFirst()

        trackedFloor += (level - trackedFloor) * follow(dt, if (level > trackedFloor) 8_000f else 2_000f)
        // Slow tracking adapts to persistent elevated squelch noise. A transmission is
        // not noise: while speech is held the floor does not rise, or a long transmission
        // would lift it over its own level and bring the music back halfway through.
        // Two things are noise even while held: a flat level (an open carrier, learned
        // quickly) and activity that never ends. The floor never exceeds the one that
        // followed everything, so the music goes down whenever it did before 1.7.3.
        val rising = level > noiseFloor
        val flat = held && rising &&
            isFlat(recent.map { it.level }, nowMillis - recent.first().at, level)
        val endless = activeSince?.let { nowMillis - it >= LONGEST_ACTIVITY_MS } ?: false
        if (!rising || !held || flat || endless) {
            val tau = if (rising && !flat) 8_000f else 2_000f
            noiseFloor = min(trackedFloor, noiseFloor + (level - noiseFloor) * follow(dt, tau))
        }
        activity = if (held) (activity + dt / 150f).coerceAtMost(1f)
            else (activity - dt / 1_000f).coerceAtLeast(0f)
        if (!held && activity <= 0f) activeSince = null
        return activity
    }

    private fun follow(dt: Long, tau: Float) = 1f - exp(-dt / tau)

    private class Sample(val at: Long, val level: Float)

    internal companion object {
        private const val WINDOW_MS = 2_000L
        private const val FLAT_MIN_SAMPLES = 4
        private const val FLAT_MIN_SPAN_MS = 900L
        private const val FLAT_RATIO = 1.5f
        private const val LONGEST_ACTIVITY_MS = 120_000L

        /**
         * Flat: at least four levels over nearly a second or more, of which four fifths, and
         * the newest one, stay within one and a half times the lowest. Clicks riding on
         * noise are ignored; the first loud syllable after a flat stretch is not flat.
         * Fewer than four levels in the two-second window (polling slower than about
         * every 660 ms) decide nothing.
         */
        internal fun isFlat(levels: List<Float>, spanMillis: Long, level: Float): Boolean {
            if (levels.size < FLAT_MIN_SAMPLES || spanMillis < FLAT_MIN_SPAN_MS) return false
            val sorted = levels.sorted()
            val band = sorted.first() * FLAT_RATIO + 0.002f
            return sorted[(4 * sorted.size - 1) / 5] <= band && level <= band
        }
    }
}
