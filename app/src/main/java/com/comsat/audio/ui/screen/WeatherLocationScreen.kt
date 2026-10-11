package com.comsat.audio.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalAirport
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.comsat.audio.data.model.Airport
import com.comsat.audio.data.model.WeatherPlace
import com.comsat.audio.data.model.WeatherSource
import com.comsat.audio.data.model.WeatherState
import com.comsat.audio.ui.components.ComsatSearchField
import com.comsat.audio.ui.components.EmptyListMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun WeatherLocationScreen(
    state: WeatherState,
    airport: Airport?,
    locating: Boolean,
    message: String?,
    onBack: () -> Unit,
    onCurrentLocation: () -> Unit,
    onAirport: () -> Unit,
    onPlace: (WeatherPlace) -> Unit,
    search: suspend (String) -> List<WeatherPlace>
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<WeatherPlace>()) }
    var searching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val name = query.trim()
        searchFailed = false
        if (name.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(400) // wait for typing to pause
        searching = true
        try {
            results = search(name)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            results = emptyList()
            searchFailed = true
        } finally {
            searching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "WEATHER LOCATION",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionLabel("SOURCE") }
            item {
                LocationOption(
                    icon = Icons.Default.MyLocation,
                    title = "Current location",
                    detail = state.deviceLocation?.takeIf { state.source == WeatherSource.CURRENT_LOCATION }?.name
                        ?: "Approximate device location",
                    selected = state.source == WeatherSource.CURRENT_LOCATION,
                    busy = locating,
                    onClick = onCurrentLocation
                )
            }
            item {
                LocationOption(
                    icon = Icons.Default.LocalAirport,
                    title = "Selected airport",
                    detail = airport?.let { "${it.icao} · ${it.city}" } ?: "Choose an airport in COMSAT first",
                    selected = state.source == WeatherSource.AIRPORT,
                    enabled = airport != null,
                    onClick = onAirport
                )
            }
            state.place?.let { place ->
                item {
                    LocationOption(
                        icon = Icons.Default.Place,
                        title = place.name,
                        detail = "Chosen city",
                        selected = state.source == WeatherSource.PLACE,
                        onClick = { onPlace(place) }
                    )
                }
            }
            message?.let {
                item {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
            item { SectionLabel("CITY") }
            item {
                ComsatSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search city",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
            when {
                searching -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                searchFailed -> item { EmptyListMessage("SEARCH UNAVAILABLE") }
                query.trim().length >= 2 && results.isEmpty() -> item { EmptyListMessage("NO MATCHES") }
            }
            items(results, key = { "${it.name}/${it.latitude}/${it.longitude}" }) { place ->
                LocationOption(
                    icon = Icons.Default.Place,
                    title = place.name,
                    detail = place.region,
                    selected = false,
                    onClick = { onPlace(place) }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun LocationOption(
    icon: ImageVector,
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    busy: Boolean = false
) {
    val contentAlpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.background
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = (if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    .copy(alpha = contentAlpha)
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
        }
        when {
            busy -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
            selected -> Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}
