package com.comsat.audio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import kotlin.math.max

// Winamp-style spectrum: bars rise instantly, decay slowly; peak caps fall
// slower still. Fed by real FFT bands from the service; scaled by the channel
// fader so the display follows the mix. All rates are per second.
private const val BAR_DECAY = 2.2f
private const val PEAK_DECAY = 0.5f

@Composable
fun SpectrumBar(
    bands: FloatArray,
    active: Boolean,
    volume: Float,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val latestBands by rememberUpdatedState(bands)
    val latestActive by rememberUpdatedState(active)
    val latestVolume by rememberUpdatedState(volume)

    val levels = remember { FloatArray(SpectrumAnalysis.BANDS) }
    val peaks = remember { FloatArray(SpectrumAnalysis.BANDS) }
    var frame by remember { mutableLongStateOf(0L) }

    // Frame loop drives decay physics; pauses with the composition (offscreen)
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceAtMost(0.1f)
            last = now
            val src = latestBands
            val gain = if (latestActive) latestVolume else 0f
            for (i in levels.indices) {
                val target = (src.getOrElse(i) { 0f } * gain).coerceIn(0f, 1f)
                levels[i] = max(target, levels[i] - BAR_DECAY * dt)
                peaks[i] = max(levels[i], peaks[i] - PEAK_DECAY * dt)
            }
            frame = now
        }
    }

    val capColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    val screenBg = MaterialTheme.colorScheme.background.copy(alpha = 0.6f)
    val outline = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .border(1.dp, outline)
            .background(screenBg)
    ) {
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            @Suppress("UNUSED_EXPRESSION") frame  // invalidate on every physics tick
            val n = levels.size
            val bw = size.width / n
            val usable = size.height - 8.dp.toPx()
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
                        topLeft = Offset(i * bw + 2.dp.toPx(), size.height - h),
                        size = Size(bw - 4.dp.toPx(), h)
                    )
                }
                val peakY = size.height - peaks[i] * usable - 3.dp.toPx()
                drawRect(
                    color = capColor,
                    topLeft = Offset(i * bw + 2.dp.toPx(), peakY),
                    size = Size(bw - 4.dp.toPx(), 1.5.dp.toPx())
                )
            }
        }
        Text(
            text = "SPECTRUM",
            fontSize = 7.sp,
            letterSpacing = 2.sp,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
            color = outline,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 3.dp, end = 6.dp)
        )
    }
}
