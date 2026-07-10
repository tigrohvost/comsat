package com.comsat.audio.ui.components

import android.content.res.Resources
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comsat.audio.R
import com.comsat.audio.data.model.Airport
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

// ─── World map background ─────────────────────────────────────────────────────
//
// Slow-drifting fragments of a real contour world map with airport markers and
// simulated air traffic, drawn behind the main screen. Coastlines are Natural
// Earth 110m data (public domain) packed into res/raw/coastlines.bin by
// tools/gen_coastlines.py. Everything is vector data driven by a single
// animation clock: no bitmaps, no extra dependencies.

// Latitude window of the map (75°N .. 60°S) — Antarctica is cropped out
private const val LAT_TOP = 75f
private const val LAT_SPAN = 135f
private const val LON_SPAN = 360f

// Map height relative to screen height: >1 shows a zoomed fragment, not the globe
private const val MAP_ZOOM = 1.7f

// One full pan across the world (and one vertical down-up sweep) takes 4 minutes
private const val PAN_LOOP_MS = 240_000

// int16 in coastlines.bin = degrees * 90 (see tools/gen_coastlines.py)
private const val COORD_SCALE = 90f

// Coastline polylines in degree space: x = lon + 180 (0..360), y = LAT_TOP - lat
private fun loadCoastlines(resources: Resources): List<Path> {
    val buf = ByteBuffer.wrap(
        resources.openRawResource(R.raw.coastlines).use { it.readBytes() }
    )
    val paths = mutableListOf<Path>()
    while (buf.remaining() >= 2) {
        val count = buf.short.toInt()
        val path = Path()
        repeat(count) { i ->
            val lon = buf.short / COORD_SCALE
            val lat = buf.short / COORD_SCALE
            val x = lon + 180f
            val y = LAT_TOP - lat
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paths += path
    }
    return paths
}

// Simulated traffic: fixed set of plausible long-haul routes between catalog airports
private val FLIGHT_ROUTES = listOf(
    "KJFK" to "LFPG",
    "KLAX" to "RJTT",
    "CYYZ" to "EHAM",
    "KMIA" to "SBGR",
    "OMDB" to "WSSS",
    "RJTT" to "YSSY",
    "LEMD" to "SAEZ",
    "KSFO" to "RKSI",
    "EKCH" to "OMDB",
    "FACT" to "HAAB",
    "WSSS" to "YMML",
    "KORD" to "KSEA",
)

private data class Flight(
    val start: Offset,      // degree space: x = lon + 180, y = LAT_TOP - lat
    val control: Offset,    // bezier control point (arcs toward the nearest pole)
    val end: Offset,
    val cycles: Int,        // crossings per pan loop — speed
    val phase: Float
)

private fun toDegSpace(lat: Float, lon: Float) = Offset(lon + 180f, LAT_TOP - lat)

private fun buildFlights(airports: List<Airport>): List<Flight> {
    val byIcao = airports.associateBy { it.icao }
    return FLIGHT_ROUTES.mapIndexedNotNull { i, (fromIcao, toIcao) ->
        val from = byIcao[fromIcao] ?: return@mapIndexedNotNull null
        val to = byIcao[toIcao] ?: return@mapIndexedNotNull null
        // Unwrap longitude so trans-Pacific routes don't streak across the map
        var endLon = to.lon
        if (endLon - from.lon > 180f) endLon -= 360f
        if (endLon - from.lon < -180f) endLon += 360f
        val start = toDegSpace(from.lat, from.lon)
        val end = toDegSpace(to.lat, endLon)
        val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
        // Arc "up" (toward the equirectangular top) scaled by route length
        val lift = abs(end.x - start.x) * 0.12f + 4f
        Flight(
            start = start,
            control = Offset(mid.x, mid.y - lift),
            end = end,
            cycles = 3 + (i % 4),
            phase = (i * 0.37f) % 1f
        )
    }
}

private fun bezier(p0: Offset, c: Offset, p1: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        u * u * p0.x + 2f * u * t * c.x + t * t * p1.x,
        u * u * p0.y + 2f * u * t * c.y + t * t * p1.y
    )
}

@Composable
fun WorldMapBackground(
    airports: List<Airport>,
    selectedIcao: String?,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val planeColor = MaterialTheme.colorScheme.secondary
    val coastColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline

    val density = LocalDensity.current
    val labelPaint = remember(gridColor, density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = with(density) { 9.sp.toPx() }
            letterSpacing = 0.15f
            color = gridColor.copy(alpha = 0.9f).toArgb()
        }
    }
    val markerPaint = remember(lineColor, density) {
        android.graphics.Paint(labelPaint).apply {
            color = lineColor.copy(alpha = 0.8f).toArgb()
        }
    }

    val resources = LocalContext.current.resources
    val coastPaths = remember { loadCoastlines(resources) }
    val flights = remember(airports) { buildFlights(airports) }
    val airportDots = remember(airports) {
        airports.map { it.icao to toDegSpace(it.lat, it.lon) }
    }

    // Single clock: 0..1 over one full pan of the world
    val clock by rememberInfiniteTransition(label = "worldMap").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(PAN_LOOP_MS, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "mapClock"
    )

    Canvas(modifier = modifier) {
        val scalePx = size.height * MAP_ZOOM / LAT_SPAN   // px per degree
        val mapW = LON_SPAN * scalePx
        val mapH = LAT_SPAN * scalePx
        val panX = clock * mapW
        // Vertical sweep: cosine so it's continuous where the clock wraps
        val panY = (mapH - size.height).coerceAtLeast(0f) *
            (0.5f - 0.5f * cos(clock * 2f * PI.toFloat()))

        // Draw adjacent copies so the horizontal pan wraps seamlessly
        for (copy in 0..1) {
            val offsetX = -panX + copy * mapW
            if (offsetX > size.width || offsetX + mapW < 0f) continue
            translate(left = offsetX, top = -panY) {
                drawWorld(
                    coastPaths, airportDots, flights, selectedIcao, scalePx, clock,
                    lineColor, planeColor, coastColor, gridColor, labelPaint, markerPaint
                )
            }
        }
    }
}

private fun DrawScope.drawWorld(
    coastPaths: List<Path>,
    airportDots: List<Pair<String, Offset>>,
    flights: List<Flight>,
    selectedIcao: String?,
    scalePx: Float,
    clock: Float,
    lineColor: Color,
    planeColor: Color,
    coastColor: Color,
    gridColor: Color,
    labelPaint: android.graphics.Paint,
    markerPaint: android.graphics.Paint
) {
    val strokePx = max(1f, 1.2.dp.toPx())

    // Graticule: 10° grid with axis labels, aviation chart style
    val gridStroke = gridColor.copy(alpha = 0.30f)
    val nativeCanvas = drawContext.canvas.nativeCanvas
    val labelPad = 3.dp.toPx()
    for (lonDeg in 0..360 step 10) {
        val x = lonDeg * scalePx
        drawLine(gridStroke, Offset(x, 0f), Offset(x, LAT_SPAN * scalePx), 1f)
        val lon = lonDeg - 180
        val text = (if (lon < 0) "W" else "E") + "%03d".format(abs(lon))
        nativeCanvas.drawText(text, x + labelPad, labelPaint.textSize + labelPad, labelPaint)
    }
    for (latDeg in 0..135 step 10) {
        val y = latDeg * scalePx
        drawLine(gridStroke, Offset(0f, y), Offset(LON_SPAN * scalePx, y), 1f)
        val lat = (LAT_TOP - latDeg).toInt()
        val text = (if (lat < 0) "S" else "N") + "%02d".format(abs(lat))
        nativeCanvas.drawText(text, labelPad, y - labelPad, labelPaint)
    }

    // Coastlines
    scale(scalePx, scalePx, pivot = Offset.Zero) {
        for (path in coastPaths) {
            drawPath(path, coastColor.copy(alpha = 0.30f), style = Stroke(width = strokePx / scalePx))
        }
    }

    // Airports
    val dotR = 1.8.dp.toPx()
    for ((icao, deg) in airportDots) {
        val pos = Offset(deg.x * scalePx, deg.y * scalePx)
        if (icao == selectedIcao) {
            // Crosshair ring with a pulse on the tuned-in airport
            val pulse = (clock * PAN_LOOP_MS / 2000f) % 1f
            val ringR = 6.dp.toPx()
            val tick = 5.dp.toPx()
            val gap = 2.dp.toPx()
            val cross = lineColor.copy(alpha = 0.85f)
            val strokeW = 1.dp.toPx()
            drawCircle(cross, radius = ringR, center = pos, style = Stroke(strokeW))
            for ((dx, dy) in listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f)) {
                drawLine(
                    cross,
                    Offset(pos.x + dx * (ringR + gap), pos.y + dy * (ringR + gap)),
                    Offset(pos.x + dx * (ringR + gap + tick), pos.y + dy * (ringR + gap + tick)),
                    strokeW
                )
            }
            drawCircle(
                lineColor.copy(alpha = 0.5f * (1f - pulse)),
                radius = ringR + pulse * 14.dp.toPx(),
                center = pos,
                style = Stroke(width = strokeW)
            )
            nativeCanvas.drawText(
                icao,
                pos.x + ringR + 4.dp.toPx(),
                pos.y - ringR,
                markerPaint
            )
        } else {
            drawCircle(lineColor.copy(alpha = 0.32f), radius = dotR, center = pos)
        }
    }

    // Traffic
    val trailSteps = 8
    for (flight in flights) {
        val progress = (clock * flight.cycles + flight.phase) % 1f
        val head = bezier(flight.start, flight.control, flight.end, progress)
        val headPx = Offset(head.x * scalePx, head.y * scalePx)
        // Fading trail behind the aircraft
        var prev = headPx
        for (s in 1..trailSteps) {
            val t = (progress - s * 0.015f).coerceAtLeast(0f)
            val p = bezier(flight.start, flight.control, flight.end, t)
            val px = Offset(p.x * scalePx, p.y * scalePx)
            drawLine(
                color = planeColor.copy(alpha = 0.5f * (1f - s.toFloat() / trailSteps)),
                start = prev,
                end = px,
                strokeWidth = 1.5.dp.toPx()
            )
            prev = px
            if (t == 0f) break
        }
        drawCircle(planeColor.copy(alpha = 0.75f), radius = 1.6.dp.toPx(), center = headPx)
    }
}
