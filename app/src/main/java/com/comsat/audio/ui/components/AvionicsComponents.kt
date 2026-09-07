package com.comsat.audio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.comsat.audio.data.model.AtisData
import com.comsat.audio.data.model.StreamStatus
import com.comsat.audio.ui.theme.NordYellow

// ─── Avionics module: bordered panel with LED header strip ───────────────────

@Composable
fun AvionicsModule(
    title: String,
    status: StreamStatus,
    accent: Color,
    modifier: Modifier = Modifier,
    // When the module itself is given a height (e.g. a Column weight), let the
    // body fill it so a weighted child inside — the spectrum — can stretch.
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val outline = MaterialTheme.colorScheme.outline
    Column(
        modifier = modifier
            .border(1.dp, outline)
            // Translucent bezel: the map chart ghosts through the panel
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModuleLed(status = status, accent = accent)
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            StatusPlacard(status)
        }
        HorizontalDivider(color = outline)
        Column(
            modifier = Modifier
                .then(if (fillHeight) Modifier.weight(1f) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

// ─── Status LED ───────────────────────────────────────────────────────────────

@Composable
fun ModuleLed(status: StreamStatus, accent: Color, modifier: Modifier = Modifier) {
    val color = when (status) {
        StreamStatus.PLAYING -> accent
        StreamStatus.BUFFERING, StreamStatus.LOADING, StreamStatus.RECONNECTING -> NordYellow
        StreamStatus.ERROR -> MaterialTheme.colorScheme.error
        StreamStatus.PAUSED, StreamStatus.IDLE -> MaterialTheme.colorScheme.outline
    }
    val lit = status !in setOf(StreamStatus.PAUSED, StreamStatus.IDLE)
    Box(
        modifier = modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(color)
            .let { if (lit) it.glowEffect(color, 5.dp) else it }
    )
}

// ─── Status placard: bordered status box (LIVE / STBY / …) ───────────────────

@Composable
fun StatusPlacard(status: StreamStatus, modifier: Modifier = Modifier) {
    val text = when (status) {
        StreamStatus.PLAYING      -> "LIVE"
        StreamStatus.BUFFERING    -> "BUFFER"
        StreamStatus.LOADING      -> "TUNING"
        StreamStatus.PAUSED       -> "HOLD"
        StreamStatus.RECONNECTING -> "RECONN"
        StreamStatus.ERROR        -> "FAULT"
        StreamStatus.IDLE         -> "STBY"
    }
    val color = when (status) {
        StreamStatus.PLAYING -> MaterialTheme.colorScheme.tertiary
        StreamStatus.BUFFERING, StreamStatus.LOADING, StreamStatus.RECONNECTING -> NordYellow
        StreamStatus.ERROR -> MaterialTheme.colorScheme.error
        StreamStatus.PAUSED, StreamStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = when (status) {
        StreamStatus.PAUSED, StreamStatus.IDLE -> MaterialTheme.colorScheme.outline
        else -> color.copy(alpha = 0.55f)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .border(1.dp, borderColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

// ─── Tick fader: instrument slider with tick marks and rectangular thumb ─────

@Composable
fun TickFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    accent: Color,
    description: String,
    modifier: Modifier = Modifier
) {
    val outline = MaterialTheme.colorScheme.outline
    val thumbFill = MaterialTheme.colorScheme.surfaceVariant
    val changeValue by rememberUpdatedState(onValueChange)

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .height(48.dp)
            .progressSemantics(value)
            .semantics {
                contentDescription = description
                setProgress { requested ->
                    val next = requested.coerceIn(0f, 1f)
                    if (next == value) false else {
                        changeValue(next)
                        true
                    }
                }
            }
            .onKeyEvent { event ->
                val step = when (event.key) {
                    Key.DirectionRight, Key.DirectionUp -> 0.05f
                    Key.DirectionLeft, Key.DirectionDown -> -0.05f
                    else -> return@onKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) {
                    changeValue((value + step).coerceIn(0f, 1f))
                }
                true
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures { pos -> changeValue((pos.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    changeValue((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        val cy = size.height / 2f
        val tickH = 8.dp.toPx()
        // Tick marks every 10%
        for (i in 0..10) {
            val x = size.width * i / 10f
            drawLine(
                color = outline,
                start = Offset(x, cy - tickH / 2),
                end = Offset(x, cy + tickH / 2),
                strokeWidth = 1.dp.toPx()
            )
        }
        // Baseline + filled portion
        drawLine(outline, Offset(0f, cy), Offset(size.width, cy), 1.dp.toPx())
        drawLine(accent, Offset(0f, cy), Offset(size.width * value, cy), 2.dp.toPx())
        // Rectangular thumb with an accent stripe
        val thumbW = 10.dp.toPx()
        val thumbH = 20.dp.toPx()
        val tx = (size.width * value - thumbW / 2).coerceIn(0f, size.width - thumbW)
        drawRoundRect(
            color = thumbFill,
            topLeft = Offset(tx, cy - thumbH / 2),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(1.dp.toPx())
        )
        drawRect(
            color = outline,
            topLeft = Offset(tx, cy - thumbH / 2),
            size = Size(thumbW, 1.dp.toPx())
        )
        drawRect(
            color = accent,
            topLeft = Offset(tx, cy - 1.dp.toPx()),
            size = Size(thumbW, 2.dp.toPx())
        )
    }
}

// ─── Square transport button ──────────────────────────────────────────────────

@Composable
fun SquareToggleButton(
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier
) {
    val borderColor = if (active) accent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 48.dp)
            .border(1.dp, borderColor)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (active) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ─── Placard button: bordered text button (CHANGE / RETRY) ───────────────────

@Composable
fun PlacardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ─── Footer placard: live panel telemetry ─────────────────────────────────────

@Composable
fun FooterPlacard(
    netOnline: Boolean,
    activeStreams: Int,
    version: String,
    modifier: Modifier = Modifier
) {
    val dim = MaterialTheme.colorScheme.outline
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = dim)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (netOnline) "NET ● ONLINE" else "NET ○ OFFLINE",
                style = MaterialTheme.typography.labelSmall,
                color = if (netOnline) dim else NordYellow
            )
            Text(
                text = "COMM $activeStreams/2",
                style = MaterialTheme.typography.labelSmall,
                color = dim
            )
            Text(
                text = "VER $version",
                style = MaterialTheme.typography.labelSmall,
                color = dim
            )
        }
    }
}

// ─── ATIS readout: METAR-derived weather beside the transport button ─────────

@Composable
fun AtisReadout(
    data: AtisData?,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        if (data == null) {
            Text(
                text = "ATIS ---",
                style = MaterialTheme.typography.labelSmall,
                color = dim
            )
        } else {
            AtisLine(accent, "TEMP" to data.tempText, "DEW" to data.dewText)
            AtisLine(accent, "RH" to data.rhText, "" to data.qnhText)
            AtisLine(accent, "WND" to data.windText)
        }
    }
}

@Composable
private fun AtisLine(accent: Color, vararg parts: Pair<String, String>) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            parts.forEachIndexed { i, (label, value) ->
                if (i > 0) append("  ")
                if (label.isNotEmpty()) {
                    withStyle(SpanStyle(color = dim)) { append(label) }
                    append(" ")
                }
                withStyle(SpanStyle(color = accent)) { append(value) }
            }
        },
        style = MaterialTheme.typography.labelSmall
    )
}
