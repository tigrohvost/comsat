package com.comsat.audio.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.comsat.audio.data.model.SomaStation
import com.comsat.audio.ui.components.ComsatSearchField
import com.comsat.audio.ui.components.EmptyListMessage
import com.comsat.audio.ui.components.glowEffect
import com.comsat.audio.ui.theme.MagentaNeon
import com.comsat.audio.viewmodel.MainViewModel

@Composable
fun StationListScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val stations by viewModel.stations.collectAsState()
    val loading  by viewModel.stationsLoading.collectAsState()
    val error    by viewModel.stationsError.collectAsState()
    val selected by viewModel.selectedStation.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }

    val filtered = stations.filter { s ->
        query.isBlank() ||
            s.title.contains(query, ignoreCase = true) ||
            s.genre.contains(query, ignoreCase = true) ||
            s.description.contains(query, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = "SELECT STATION",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        ComsatSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = "station / genre",
            accentColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
            }
        } else if (error != null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ERR: $error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    TextButton(onClick = { viewModel.loadStations() }) {
                        Text(
                            text = "RETRY",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        } else if (filtered.isEmpty()) {
            EmptyListMessage(if (query.isBlank()) "NO STATIONS" else "NO MATCHES")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.id }) { station ->
                    StationCard(
                        station = station,
                        isSelected = station.id == selected?.id,
                        onClick = {
                            viewModel.selectStation(station)
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StationCard(station: SomaStation, isSelected: Boolean, onClick: () -> Unit) {
    val accentColor = if (isSelected) MaterialTheme.colorScheme.secondary
    else MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .let { if (isSelected) it.glowEffect(MagentaNeon, 8.dp) else it }
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (station.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = station.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .border(
                                1.dp,
                                accentColor.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
                Column {
                    Text(
                        text = station.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = station.genre.lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (station.listeners > 0) {
                Text(
                    text = "${station.listeners} listeners",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            if (station.description.isNotBlank()) {
                Text(
                    text = station.description.take(80).let {
                        if (station.description.length > 80) "$it…" else it
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
