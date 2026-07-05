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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
                drawWorld(coastPaths, airportDots, flights, selectedIcao, scalePx, clock, lineColor, planeColor)
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
    planeColor: Color
) {
    val strokePx = max(1f, 1.2.dp.toPx())

    // Coastlines
    scale(scalePx, scalePx, pivot = Offset.Zero) {
        for (path in coastPaths) {
            drawPath(path, lineColor.copy(alpha = 0.14f), style = Stroke(width = strokePx / scalePx))
        }
    }

    // Airports
    val dotR = 1.8.dp.toPx()
    for ((icao, deg) in airportDots) {
        val pos = Offset(deg.x * scalePx, deg.y * scalePx)
        if (icao == selectedIcao) {
            // Pulsing ring on the tuned-in airport
            val pulse = (clock * PAN_LOOP_MS / 2000f) % 1f
            drawCircle(lineColor.copy(alpha = 0.9f), radius = dotR * 1.3f, center = pos)
            drawCircle(
                lineColor.copy(alpha = 0.6f * (1f - pulse)),
                radius = dotR * (1.5f + pulse * 4f),
                center = pos,
                style = Stroke(width = 1.dp.toPx())
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
