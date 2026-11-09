package com.comsat.audio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.runtime.withFrameNanos
import com.comsat.audio.service.SpectrumAnalysis
import kotlin.math.exp
import kotlin.math.max

// Smooth the ~30 Hz FFT updates at display refresh rate, with a quick attack
// and a softer release. Time constants are in seconds so the response stays
// consistent across refresh rates; peak caps drift down at units per second.
private const val BAR_ATTACK_SECONDS = 0.045f
private const val BAR_RELEASE_SECONDS = 0.22f
private const val PEAK_DECAY = 0.5f
private const val SETTLED_LEVEL = 0.001f

@Composable
fun SpectrumBar(
    bands: FloatArray,
    active: Boolean,
    volume: Float,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val latestBands by rememberUpdatedState(bands)
    val latestVolume by rememberUpdatedState(volume)

    val levels = remember { FloatArray(SpectrumAnalysis.BANDS) }
    val peaks = remember { FloatArray(SpectrumAnalysis.BANDS) }
    var frame by remember { mutableLongStateOf(0L) }

    // Let the peaks settle after pausing/muting, then stop requesting frames.
    // An idle audio panel should not redraw both spectra at display refresh rate.
    val animating = active && volume > 0f
    LaunchedEffect(animating) {
        var last = withFrameNanos { it }
        while (animating || peaks.any { it > 0f }) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceAtMost(0.1f)
            last = now
            val src = latestBands
            val gain = if (animating) latestVolume else 0f
            val attack = 1f - exp(-dt / BAR_ATTACK_SECONDS)
            val release = 1f - exp(-dt / BAR_RELEASE_SECONDS)
            for (i in levels.indices) {
                val target = (src.getOrElse(i) { 0f } * gain).coerceIn(0f, 1f)
                val current = levels[i]
                val follow = if (target > current) attack else release
                val level = current + (target - current) * follow
                // Exponential release approaches zero indefinitely; snap the
                // invisible tail to rest so paused/muted panels stop drawing.
                levels[i] = if (target == 0f && level < SETTLED_LEVEL) 0f else level
                peaks[i] = max(levels[i], peaks[i] - PEAK_DECAY * dt)
            }
            frame = now
        }
    }

    val capColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    val screenBg = MaterialTheme.colorScheme.background.copy(alpha = 0.6f)
    val outline = MaterialTheme.colorScheme.outline
    val gridColor = accent.copy(alpha = 0.10f)

    // Height is the caller's call: a fixed .height() or a Column weight so the
    // bar absorbs whatever is left on screen. Unconstrained, it falls back to
    // 64 dp and never collapses below 40 dp.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .border(1.dp, outline)
            .background(screenBg)
    ) {
        Canvas(Modifier.fillMaxWidth().heightIn(min = 64.dp).fillMaxHeight()) {
            @Suppress("UNUSED_EXPRESSION") frame  // invalidate on every physics tick
            val n = levels.size
            val bw = size.width / n
            // One square cell per FFT band; lines sit in the gaps between bars.
            val gridStroke = 0.5.dp.toPx()
            if (bw > 0f) {
                for (i in 1 until n) {
                    val x = i * bw
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), gridStroke)
                }
                var y = size.height - bw
                while (y > 0f) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), gridStroke)
                    y -= bw
                }
            }
            val usable = (size.height - 8.dp.toPx()).coerceAtLeast(0f)
            val gap = minOf(2.dp.toPx(), bw * 0.2f)
            val gradient = Brush.verticalGradient(
                0f to accent.copy(alpha = 0.75f),
                1f to accent.copy(alpha = 0.35f),
                startY = 0f,
                endY = size.height
            )
            for (i in 0 until n) {
                val h = levels[i] * usable
                if (h > 0.5f) {
                    drawRect(
                        brush = gradient,
                        topLeft = Offset(i * bw + gap, size.height - h),
                        size = Size(bw - gap * 2, h)
                    )
                }
                val peakY = size.height - peaks[i] * usable - 3.dp.toPx()
                drawRect(
                    color = capColor,
                    topLeft = Offset(i * bw + gap, peakY),
                    size = Size(bw - gap * 2, 1.5.dp.toPx())
                )
            }
        }
        Text(
            text = "SPECTRUM",
            fontSize = 7.sp,
            letterSpacing = 2.sp,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 3.dp, end = 6.dp)
        )
    }
}
