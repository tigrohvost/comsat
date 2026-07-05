package com.comsat.audio.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.comsat.audio.data.model.StreamStatus
import com.comsat.audio.ui.theme.AmberNeon
import com.comsat.audio.ui.theme.GreenNeon
import com.comsat.audio.ui.theme.RedError

// ─── Neon border card ─────────────────────────────────────────────────────────

@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    glowColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, glowColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .glowEffect(glowColor, radius = 8.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        content = content
    )
}

// ─── Glow modifier ────────────────────────────────────────────────────────────

fun Modifier.glowEffect(color: Color, radius: Dp = 6.dp): Modifier = drawBehind {
    drawIntoCanvas {
        val paint = androidx.compose.ui.graphics.Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer(radius.toPx(), 0f, 0f, color.copy(alpha = 0.6f).toArgb())
            }
        }
        it.drawRoundRect(
            left = 0f, top = 0f, right = size.width, bottom = size.height,
            radiusX = 4.dp.toPx(), radiusY = 4.dp.toPx(), paint = paint
        )
    }
}

// ─── Status dot ───────────────────────────────────────────────────────────────

@Composable
fun StatusDot(status: StreamStatus, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )

    val color = when (status) {
        StreamStatus.PLAYING -> GreenNeon
        StreamStatus.BUFFERING, StreamStatus.LOADING, StreamStatus.RECONNECTING -> AmberNeon
        StreamStatus.ERROR -> RedError
        StreamStatus.PAUSED, StreamStatus.IDLE -> MaterialTheme.colorScheme.outline
    }
    val alpha = when (status) {
        StreamStatus.PLAYING, StreamStatus.BUFFERING,
        StreamStatus.LOADING, StreamStatus.RECONNECTING -> pulseAlpha
        else -> 1f
    }

    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
            .glowEffect(color, radius = 4.dp)
    )
}

// ─── Status label ─────────────────────────────────────────────────────────────

@Composable
fun StatusLabel(status: StreamStatus) {
    val text = when (status) {
        StreamStatus.PLAYING     -> "LIVE"
        StreamStatus.BUFFERING   -> "BUFFERING"
        StreamStatus.LOADING     -> "CONNECTING"
        StreamStatus.PAUSED      -> "PAUSED"
        StreamStatus.RECONNECTING -> "RECONNECTING"
        StreamStatus.ERROR       -> "ERROR"
        StreamStatus.IDLE        -> "STANDBY"
    }
    val color = when (status) {
        StreamStatus.PLAYING -> GreenNeon
        StreamStatus.BUFFERING, StreamStatus.LOADING, StreamStatus.RECONNECTING -> AmberNeon
        StreamStatus.ERROR -> RedError
        StreamStatus.PAUSED, StreamStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

// ─── Neon slider ──────────────────────────────────────────────────────────────

@Composable
fun NeonSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = color,
            activeTrackColor = color,
            inactiveTrackColor = color.copy(alpha = 0.2f),
            activeTickColor = color,
            inactiveTickColor = color.copy(alpha = 0.1f)
        )
    )
}

// ─── Online indicator dot (for airport/station lists) ─────────────────────────

@Composable
fun OnlineDot(isOnline: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(if (isOnline) GreenNeon else RedError)
            .let { if (isOnline) it.glowEffect(GreenNeon, 4.dp) else it }
    )
}

// ─── Search field (collapsed search bar with clear button) ────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComsatSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = accentColor) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        expanded = false,
        onExpandedChange = {},
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        colors = SearchBarDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {}
}

// ─── Empty list message ───────────────────────────────────────────────────────

@Composable
fun EmptyListMessage(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Section header ───────────────────────────────────────────────────────────

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}
