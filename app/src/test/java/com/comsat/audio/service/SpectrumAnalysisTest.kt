package com.comsat.audio.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SpectrumAnalysisTest {

    private fun sine(freq: Float, sampleRate: Int, amplitude: Float = 1f) =
        FloatArray(SpectrumAnalysis.FFT_SIZE) { i ->
            amplitude * sin(2.0 * PI * freq * i / sampleRate).toFloat()
        }

    // The band whose log-spaced range [50 Hz .. 16 kHz over 24 bands] holds freq
    private fun bandFor(freq: Float, sampleRate: Int): Int {
        val maxFreq = minOf(16_000f, sampleRate / 2f)
        val logMin = Math.log(50.0)
        val logSpan = Math.log(maxFreq.toDouble()) - logMin
        val b = ((Math.log(freq.toDouble()) - logMin) / logSpan * SpectrumAnalysis.BANDS).toInt()
        return b.coerceIn(0, SpectrumAnalysis.BANDS - 1)
    }

    @Test
    fun silenceProducesZeroBands() {
        val out = SpectrumAnalysis.analyze(FloatArray(SpectrumAnalysis.FFT_SIZE), 44_100)
        assertEquals(SpectrumAnalysis.BANDS, out.size)
        for (v in out) assertEquals(0f, v, 1e-4f)
    }

    @Test
    fun pureToneDominatesItsBand() {
        val sampleRate = 44_100
        val freq = 1_000f
        val out = SpectrumAnalysis.analyze(sine(freq, sampleRate), sampleRate)
        val expected = bandFor(freq, sampleRate)
        val loudest = out.indices.maxBy { out[it] }
        // Spectral leakage may push the peak into the adjacent band
        assertTrue(
            "loudest band $loudest not near expected $expected: ${out.toList()}",
            loudest in (expected - 1)..(expected + 1)
        )
        // A full-scale tone must be near the top of the normalized range
        assertTrue("peak too quiet: ${out[loudest]}", out[loudest] > 0.8f)
    }

    @Test
    fun lowAndHighTonesLandInDifferentBands() {
        val sampleRate = 44_100
        val low = SpectrumAnalysis.analyze(sine(100f, sampleRate), sampleRate)
        val high = SpectrumAnalysis.analyze(sine(8_000f, sampleRate), sampleRate)
        val lowPeak = low.indices.maxBy { low[it] }
        val highPeak = high.indices.maxBy { high[it] }
        assertTrue("expected $lowPeak << $highPeak", highPeak - lowPeak > 8)
    }

    @Test
    fun quietToneScoresLowerThanLoudTone() {
        val sampleRate = 44_100
        val loud = SpectrumAnalysis.analyze(sine(1_000f, sampleRate), sampleRate)
        val quiet = SpectrumAnalysis.analyze(sine(1_000f, sampleRate, amplitude = 0.01f), sampleRate)
        assertTrue(loud.max() > quiet.max())
        assertTrue("quiet tone (-40 dB) should still register", quiet.max() > 0.1f)
    }

    @Test
    fun lowSampleRateStreamStillMapsWithinBands() {
        // ATC feeds are often 22.05 kHz mono MP3
        val sampleRate = 22_050
        val out = SpectrumAnalysis.analyze(sine(2_000f, sampleRate), sampleRate)
        assertTrue(out.max() > 0.8f)
    }
}
