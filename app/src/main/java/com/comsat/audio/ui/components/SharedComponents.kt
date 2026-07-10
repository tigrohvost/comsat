package com.comsat.audio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─── Glow modifier (status LEDs) ──────────────────────────────────────────────

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

// ─── Online indicator dot (for airport/station lists) ─────────────────────────

@Composable
fun OnlineDot(isOnline: Boolean, modifier: Modifier = Modifier) {
    val color = if (isOnline) MaterialTheme.colorScheme.tertiary
    else MaterialTheme.colorScheme.error
    Box(
        modifier = modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color)
            .let { if (isOnline) it.glowEffect(color, 4.dp) else it }
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
