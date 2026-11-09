package com.comsat.audio.service

import kotlin.math.exp
import kotlin.math.max

/** Adaptive level gate: reject short squelch clicks, hold speech, then release. */
class AtcActivityDetector {
    private var noiseFloor = 0.003f
    private var candidateSince: Long? = null
    private var heldUntil = 0L
    private var previousAt: Long? = null
    var activity: Float = 0f
        private set

    fun reset() {
        noiseFloor = 0.003f
        candidateSince = null
        heldUntil = 0
        previousAt = null
        activity = 0f
    }

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
        // Slow tracking also adapts to persistent elevated squelch noise.
        val follow = 1f - exp(-dt / if (level > noiseFloor) 8_000f else 2_000f)
        noiseFloor += (level - noiseFloor) * follow
        activity = if (nowMillis < heldUntil) (activity + dt / 150f).coerceAtMost(1f)
            else (activity - dt / 1_000f).coerceAtLeast(0f)
        return activity
    }
}
