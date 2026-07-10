package com.comsat.audio.service

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time spectrum analyzer fed by a [TeeAudioProcessor] in the player's
 * audio sink chain. Receives raw 16-bit PCM on the playback thread (pre-fader,
 * no permissions needed) and publishes [SpectrumAnalysis.BANDS] log-spaced
 * bands normalized 0..1, at most every [EMIT_INTERVAL_MS] (~30 Hz).
 */
@UnstableApi
class SpectrumProcessor : TeeAudioProcessor.AudioBufferSink {

    companion object {
        const val BANDS = SpectrumAnalysis.BANDS
        private const val EMIT_INTERVAL_MS = 33L
        private val ZERO = FloatArray(BANDS)
    }

    private val _bands = MutableStateFlow(ZERO)
    val bands: StateFlow<FloatArray> = _bands

    private var sampleRate = 44_100
    private var channelCount = 2

    // Mono ring of the most recent FFT window worth of samples
    private val window = FloatArray(SpectrumAnalysis.FFT_SIZE)
    private var windowPos = 0
    private var samplesSinceEmit = 0
    private var lastEmitAt = 0L

    fun reset() {
        _bands.value = ZERO
    }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        // Encoding is always ENCODING_PCM_16BIT at this point in the sink chain.
        this.sampleRate = sampleRateHz
        this.channelCount = channelCount
        windowPos = 0
        window.fill(0f)
        _bands.value = ZERO
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val buf = buffer.asShortBuffer()
        val frames = buf.remaining() / channelCount
        for (f in 0 until frames) {
            var acc = 0
            for (c in 0 until channelCount) acc += buf.get(f * channelCount + c).toInt()
            window[windowPos] = acc / (channelCount * 32768f)
            windowPos = (windowPos + 1) % window.size
        }
        samplesSinceEmit += frames

        val now = android.os.SystemClock.elapsedRealtime()
        if (samplesSinceEmit >= window.size / 2 && now - lastEmitAt >= EMIT_INTERVAL_MS) {
            samplesSinceEmit = 0
            lastEmitAt = now
            // Unroll the ring oldest-first into a plain window for analysis
            val samples = FloatArray(window.size) { i -> window[(windowPos + i) % window.size] }
            _bands.value = SpectrumAnalysis.analyze(samples, sampleRate)
        }
    }
}

/**
 * Pure DSP: Hann window → radix-2 FFT → log-spaced band folding in dB.
 * No Android dependencies so it is unit-testable on the JVM.
 */
object SpectrumAnalysis {
    const val BANDS = 24
    const val FFT_SIZE = 1024            // power of two
    private const val DB_FLOOR = -60f    // silence cutoff
    private const val FREQ_MIN = 50f
    private const val FREQ_MAX = 16_000f

    private val hann = FloatArray(FFT_SIZE) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1))).toFloat()
    }

    /** [samples] length must be [FFT_SIZE]; returns [BANDS] values in 0..1. */
    fun analyze(samples: FloatArray, sampleRate: Int): FloatArray {
        require(samples.size == FFT_SIZE) { "expected $FFT_SIZE samples" }
        val re = FloatArray(FFT_SIZE) { i -> samples[i] * hann[i] }
        val im = FloatArray(FFT_SIZE)
        fft(re, im)

        val nyquist = sampleRate / 2f
        val maxFreq = min(FREQ_MAX, nyquist)
        val out = FloatArray(BANDS)
        val logMin = ln(FREQ_MIN)
        val logSpan = ln(maxFreq) - logMin
        val binHz = sampleRate.toFloat() / FFT_SIZE

        for (b in 0 until BANDS) {
            val f0 = exp(logMin + logSpan * b / BANDS)
            val f1 = exp(logMin + logSpan * (b + 1) / BANDS)
            val k0 = (f0 / binHz).toInt().coerceIn(1, FFT_SIZE / 2 - 1)
            val k1 = (f1 / binHz).toInt().coerceIn(k0, FFT_SIZE / 2 - 1)
            var peak = 0f
            for (k in k0..k1) {
                // Normalize so a full-scale windowed sine is ~1.0
                val mag = sqrt(re[k] * re[k] + im[k] * im[k]) / (FFT_SIZE / 4f)
                if (mag > peak) peak = mag
            }
            val db = 20f * log10(peak.coerceAtLeast(1e-9f))
            out[b] = ((db - DB_FLOOR) / -DB_FLOOR).coerceIn(0f, 1f)
        }
        return out
    }

    // In-place iterative radix-2 Cooley-Tukey
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
            var m = n shr 1
            while (m in 1..j) { j -= m; m = m shr 1 }
            j += m
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val aRe = re[i + k]; val aIm = im[i + k]
                    val bIdx = i + k + len / 2
                    val bRe = re[bIdx] * curRe - im[bIdx] * curIm
                    val bIm = re[bIdx] * curIm + im[bIdx] * curRe
                    re[i + k] = aRe + bRe
                    im[i + k] = aIm + bIm
                    re[bIdx] = aRe - bRe
                    im[bIdx] = aIm - bIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
