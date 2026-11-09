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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.comsat.audio.data.model.SomaStation
import com.comsat.audio.ui.components.ComsatSearchField
import com.comsat.audio.ui.components.EmptyListMessage
import com.comsat.audio.viewmodel.MainViewModel

@Composable
fun StationListScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val loading  by viewModel.stationsLoading.collectAsStateWithLifecycle()
    val error    by viewModel.stationsError.collectAsStateWithLifecycle()
    val selected by viewModel.selectedStation.collectAsStateWithLifecycle()

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
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
            Text(
                text = "SELECT STATION",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = viewModel::loadStations, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.tertiary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh stations",
                        tint = MaterialTheme.colorScheme.tertiary)
                }
            }
        }

        ComsatSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = "station / genre",
            accentColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        if (error != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = viewModel::loadStations, enabled = !loading) {
                    Text("RETRY", color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }

        if (loading && stations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
            }
        } else if (filtered.isEmpty()) {
            EmptyListMessage(if (query.isBlank()) "NO STATIONS" else "NO MATCHES")
        } else {
            LazyVerticalGrid(
                // Two columns on regular phones, one on very narrow windows
                // and more on tablets/foldables.
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val groups = listOf(
                    "FAVORITES" to filtered.filter { it.id in settings.favoriteStations },
                    "STATIONS" to filtered.filterNot { it.id in settings.favoriteStations })
                groups.forEach { (title, group) ->
                if (group.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(title, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary)
                }
                items(group, key = { it.id }) { station ->
                    StationCard(
                        station = station,
                        favorite = station.id in settings.favoriteStations,
                        onFavorite = { viewModel.toggleFavoriteStation(station.id) },
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
}

@Composable
private fun StationCard(station: SomaStation, favorite: Boolean, onFavorite: () -> Unit, isSelected: Boolean, onClick: () -> Unit) {
    val accentColor = if (isSelected) MaterialTheme.colorScheme.tertiary
    else MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .border(1.dp, accentColor.copy(alpha = 0.7f))
            .background(
                if (isSelected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .semantics { selected = isSelected }
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
                        color = if (isSelected) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = station.genre.lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onFavorite) {
                Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite station ${station.id}",
                    tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (station.listeners > 0) {
                Text(
                    text = "${station.listeners} listeners",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (station.description.isNotBlank()) {
                Text(
                    text = station.description.take(80).let {
                        if (station.description.length > 80) "$it…" else it
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
