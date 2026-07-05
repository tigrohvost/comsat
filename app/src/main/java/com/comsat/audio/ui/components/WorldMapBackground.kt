package com.comsat.audio.ui.components

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
import androidx.compose.ui.unit.dp
import com.comsat.audio.data.model.Airport
import kotlin.math.abs
import kotlin.math.max

// ─── World map background ─────────────────────────────────────────────────────
//
// Slow-drifting equirectangular contour map with airport markers and simulated
// air traffic, drawn behind the main screen. Everything is vector data driven
// by a single animation clock: no bitmaps, no extra dependencies.

// Latitude window of the map (75°N .. 60°S) — Antarctica is cropped out
private const val LAT_TOP = 75f
private const val LAT_SPAN = 135f
private const val LON_SPAN = 360f

// One full pan across the world takes 4 minutes
private const val PAN_LOOP_MS = 240_000

// ─── Coastlines ───────────────────────────────────────────────────────────────
// Hand-simplified outlines as (lon, lat) pairs; closed unless noted. Low-poly on
// purpose — it reads as a radar/HUD wireframe at background alpha.

private val COASTLINES: List<FloatArray> = listOf(
    // North America
    floatArrayOf(
        -165f, 66f, -168f, 60f, -158f, 58f, -152f, 60f, -140f, 59f, -134f, 56f, -130f, 54f,
        -125f, 49f, -124f, 43f, -122f, 37f, -117f, 33f, -110f, 24f, -105f, 20f, -97f, 16f,
        -94f, 16f, -90f, 14f, -86f, 12f, -82f, 9f, -80f, 9f, -82f, 13f, -86f, 16f, -90f, 19f,
        -95f, 19f, -97f, 24f, -94f, 29f, -89f, 29f, -84f, 30f, -81f, 26f, -80f, 27f, -81f, 31f,
        -78f, 34f, -75f, 37f, -74f, 40f, -70f, 42f, -66f, 44f, -60f, 46f, -56f, 50f, -60f, 54f,
        -64f, 58f, -70f, 60f, -78f, 62f, -84f, 64f, -92f, 66f, -102f, 68f, -115f, 69f,
        -128f, 70f, -140f, 70f, -152f, 71f, -160f, 69f, -165f, 66f
    ),
    // South America
    floatArrayOf(
        -80f, 9f, -77f, 8f, -75f, 11f, -71f, 12f, -64f, 10f, -60f, 8f, -55f, 6f, -50f, 3f,
        -44f, -2f, -38f, -5f, -35f, -8f, -39f, -14f, -41f, -22f, -46f, -24f, -48f, -28f,
        -53f, -34f, -58f, -38f, -62f, -40f, -65f, -45f, -68f, -50f, -71f, -54f, -73f, -50f,
        -73f, -44f, -72f, -38f, -71f, -32f, -70f, -25f, -70f, -18f, -76f, -14f, -81f, -6f,
        -80f, -1f, -77f, 3f, -78f, 7f, -80f, 9f
    ),
    // Greenland
    floatArrayOf(
        -45f, 60f, -42f, 63f, -38f, 65f, -32f, 68f, -25f, 70f, -21f, 74f, -28f, 78f, -38f, 80f,
        -50f, 80f, -58f, 76f, -60f, 73f, -55f, 69f, -52f, 65f, -48f, 61f, -45f, 60f
    ),
    // Eurasia
    floatArrayOf(
        -9f, 38f, -9f, 43f, -4f, 44f, -4f, 48f, 0f, 49f, 3f, 51f, 6f, 53f, 8f, 55f, 8f, 57f,
        10f, 54f, 13f, 54f, 18f, 55f, 24f, 57f, 28f, 60f, 25f, 62f, 24f, 65f, 22f, 66f,
        18f, 63f, 18f, 59f, 13f, 56f, 11f, 58f, 6f, 58f, 5f, 61f, 7f, 63f, 12f, 66f, 16f, 68f,
        20f, 70f, 26f, 71f, 30f, 70f, 35f, 68f, 40f, 67f, 45f, 68f, 55f, 69f, 65f, 70f,
        75f, 73f, 85f, 75f, 95f, 76f, 105f, 77f, 115f, 75f, 125f, 73f, 135f, 72f, 145f, 70f,
        155f, 69f, 163f, 68f, 170f, 66f, 178f, 65f, 174f, 62f, 170f, 60f, 163f, 60f, 160f, 53f,
        156f, 51f, 158f, 56f, 155f, 60f, 152f, 59f, 147f, 55f, 142f, 54f, 138f, 49f, 135f, 44f,
        131f, 43f, 129f, 40f, 127f, 35f, 125f, 38f, 122f, 40f, 118f, 39f, 120f, 35f, 122f, 31f,
        120f, 27f, 117f, 24f, 114f, 22f, 110f, 20f, 109f, 13f, 105f, 9f, 100f, 13f, 102f, 8f,
        104f, 1f, 100f, 6f, 98f, 11f, 95f, 16f, 92f, 21f, 89f, 22f, 86f, 20f, 82f, 16f,
        80f, 12f, 77f, 8f, 73f, 16f, 70f, 21f, 66f, 25f, 61f, 25f, 57f, 27f, 51f, 29f, 48f, 30f,
        50f, 26f, 54f, 25f, 58f, 23f, 57f, 19f, 53f, 17f, 49f, 14f, 45f, 12f, 43f, 13f, 41f, 17f,
        38f, 22f, 35f, 28f, 33f, 30f, 34f, 32f, 36f, 35f, 32f, 36f, 28f, 37f, 24f, 39f, 23f, 38f,
        21f, 37f, 20f, 40f, 19f, 42f, 16f, 44f, 14f, 45f, 15f, 42f, 18f, 40f, 16f, 38f, 13f, 41f,
        11f, 44f, 8f, 44f, 4f, 43f, 3f, 42f, 0f, 40f, -1f, 38f, -5f, 36f, -9f, 38f
    ),
    // Great Britain
    floatArrayOf(-5f, 50f, -4f, 53f, -6f, 56f, -4f, 58f, -2f, 57f, -1f, 54f, 1f, 52f, 0f, 51f, -5f, 50f),
    // Ireland
    floatArrayOf(-10f, 52f, -8f, 55f, -6f, 54f, -6f, 52f, -10f, 52f),
    // Iceland
    floatArrayOf(-22f, 64f, -17f, 63f, -14f, 65f, -19f, 66f, -22f, 64f),
    // Japan (open polyline)
    floatArrayOf(130f, 31f, 131f, 33f, 134f, 34f, 137f, 35f, 140f, 36f, 141f, 39f, 141f, 42f, 143f, 43f, 145f, 44f),
    // Africa
    floatArrayOf(
        -6f, 35f, -10f, 31f, -15f, 26f, -17f, 21f, -17f, 15f, -15f, 11f, -11f, 7f, -6f, 5f,
        0f, 6f, 5f, 6f, 9f, 4f, 10f, -1f, 12f, -5f, 13f, -10f, 13f, -17f, 14f, -23f, 16f, -29f,
        18f, -34f, 23f, -34f, 28f, -32f, 32f, -28f, 35f, -22f, 38f, -17f, 40f, -11f, 40f, -3f,
        44f, 3f, 48f, 7f, 51f, 11f, 45f, 11f, 43f, 12f, 40f, 15f, 38f, 19f, 35f, 24f, 33f, 28f,
        32f, 30f, 29f, 31f, 25f, 32f, 20f, 31f, 15f, 33f, 10f, 37f, 5f, 36f, 0f, 36f, -3f, 35f, -6f, 35f
    ),
    // Madagascar
    floatArrayOf(44f, -25f, 48f, -25f, 50f, -19f, 49f, -13f, 46f, -16f, 44f, -20f, 44f, -25f),
    // Australia
    floatArrayOf(
        114f, -22f, 113f, -26f, 115f, -33f, 119f, -35f, 124f, -33f, 130f, -32f, 134f, -33f,
        137f, -35f, 140f, -38f, 145f, -39f, 148f, -38f, 150f, -37f, 153f, -33f, 153f, -28f,
        152f, -25f, 149f, -21f, 146f, -19f, 143f, -14f, 142f, -11f, 140f, -17f, 136f, -15f,
        132f, -12f, 129f, -14f, 125f, -15f, 122f, -18f, 118f, -20f, 114f, -22f
    ),
    // New Zealand (open polyline)
    floatArrayOf(167f, -46f, 170f, -43f, 172f, -41f, 174f, -39f, 176f, -38f, 178f, -37f),
    // Borneo
    floatArrayOf(109f, 1f, 111f, 4f, 115f, 6f, 118f, 5f, 119f, 1f, 116f, -2f, 112f, -3f, 109f, 1f),
    // Sumatra + Java (open polyline)
    floatArrayOf(95f, 5f, 98f, 2f, 102f, -2f, 105f, -6f, 108f, -7f, 112f, -8f, 115f, -8f),
    // New Guinea
    floatArrayOf(131f, -1f, 135f, -2f, 140f, -3f, 145f, -5f, 150f, -9f, 147f, -9f, 143f, -8f, 138f, -7f, 134f, -4f, 131f, -1f),
    // Cuba (open polyline)
    floatArrayOf(-84f, 22f, -80f, 22f, -76f, 20f),
)

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

    // Coastline paths in degree space (x: 0..360, y: 0..135), built once
    val coastPaths = remember {
        COASTLINES.map { pts ->
            Path().apply {
                moveTo(pts[0], pts[1])
                for (i in 2 until pts.size step 2) lineTo(pts[i], pts[i + 1])
            }
        }
    }
    val flights = remember(airports) { buildFlights(airports) }
    val airportDots = remember(airports) {
        airports.map { Triple(it.icao, toDegSpace(it.lat, it.lon), it) }
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
        val scale = size.height / LAT_SPAN        // px per degree
        val mapW = LON_SPAN * scale
        val panX = clock * mapW

        // Draw three adjacent copies so the pan wraps seamlessly
        for (copy in -1..1) {
            val offsetX = -panX + copy * mapW
            if (offsetX > size.width || offsetX + mapW < -mapW * 0.5f) continue
            translate(left = offsetX) {
                drawWorld(coastPaths, airportDots, flights, selectedIcao, scale, clock, lineColor, planeColor)
            }
        }
    }
}

private fun DrawScope.drawWorld(
    coastPaths: List<Path>,
    airportDots: List<Triple<String, Offset, Airport>>,
    flights: List<Flight>,
    selectedIcao: String?,
    scale: Float,
    clock: Float,
    lineColor: Color,
    planeColor: Color
) {
    val stroke = Stroke(width = max(1f, 1.2.dp.toPx()))

    // Coastlines
    scale(scale, scale, pivot = Offset.Zero) {
        for (path in coastPaths) {
            drawPath(path, lineColor.copy(alpha = 0.14f), style = Stroke(width = stroke.width / scale))
        }
    }

    // Airports
    val dotR = 1.8.dp.toPx()
    for ((icao, deg, _) in airportDots) {
        val pos = Offset(deg.x * scale, deg.y * scale)
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
        val headPx = Offset(head.x * scale, head.y * scale)
        // Fading trail behind the aircraft
        var prev = headPx
        for (s in 1..trailSteps) {
            val t = (progress - s * 0.015f).coerceAtLeast(0f)
            val p = bezier(flight.start, flight.control, flight.end, t)
            val px = Offset(p.x * scale, p.y * scale)
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
