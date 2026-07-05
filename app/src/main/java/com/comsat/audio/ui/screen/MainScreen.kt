package com.comsat.audio.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.comsat.audio.data.model.StreamState
import com.comsat.audio.data.model.StreamStatus
import com.comsat.audio.data.repository.AIRPORT_CATALOG
import com.comsat.audio.ui.components.NeonCard
import com.comsat.audio.ui.components.NeonSlider
import com.comsat.audio.ui.components.SectionHeader
import com.comsat.audio.ui.components.StatusDot
import com.comsat.audio.ui.components.StatusLabel
import com.comsat.audio.ui.components.WorldMapBackground
import com.comsat.audio.ui.navigation.Screen
import com.comsat.audio.ui.theme.CyanNeon
import com.comsat.audio.ui.theme.LocalSetTheme
import com.comsat.audio.ui.theme.LocalThemeMode
import com.comsat.audio.ui.theme.ThemeMode
import com.comsat.audio.ui.theme.MagentaNeon
import com.comsat.audio.viewmodel.MainViewModel

@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val atcState   by viewModel.atcState.collectAsState()
    val somaState  by viewModel.somaState.collectAsState()
    val atcVolume  by viewModel.atcVolume.collectAsState()
    val somaVolume by viewModel.somaVolume.collectAsState()
    val airport    by viewModel.selectedAirport.collectAsState()
    val station    by viewModel.selectedStation.collectAsState()
    val nowPlaying by viewModel.somaNowPlaying.collectAsState()
    val themeMode = LocalThemeMode.current
    val setTheme  = LocalSetTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WorldMapBackground(
            airports = AIRPORT_CATALOG,
            selectedIcao = airport?.icao,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── App bar ──────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "COMSAT",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                ThemeMenuButton(themeMode = themeMode, onSetTheme = setTheme)
            }

            // ── ATC stream card ──────────────────────────────────────────────────
            StreamCard(
                label = "ATC FEED",
                sourceName = airport?.let { "${it.icao} · ${it.name}" } ?: "NO AIRPORT SELECTED",
                subLabel = airport?.let { "${it.city}, ${it.country}" },
                state = atcState,
                volume = atcVolume,
                onVolumeChange = viewModel::setAtcVolume,
                onToggle = viewModel::toggleAtcPlayback,
                onSelectSource = { navController.navigate(Screen.Airports.route) },
                accentColor = CyanNeon,
                icon = { Icon(Icons.Default.Radar, contentDescription = null) }
            )

            // ── Soma.fm stream card ──────────────────────────────────────────────
            StreamCard(
                label = "AMBIENT",
                sourceName = station?.title ?: "NO STATION SELECTED",
                subLabel = nowPlaying ?: station?.genre?.lowercase(),
                state = somaState,
                volume = somaVolume,
                onVolumeChange = viewModel::setSomaVolume,
                onToggle = viewModel::toggleSomaPlayback,
                onSelectSource = { navController.navigate(Screen.Stations.route) },
                accentColor = MagentaNeon,
                icon = { Icon(Icons.Default.Tune, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun ThemeMenuButton(
    themeMode: ThemeMode,
    onSetTheme: (ThemeMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // The button shows the CURRENT theme; the menu lists all three explicitly
    val currentIcon = when (themeMode) {
        ThemeMode.DARK   -> Icons.Default.DarkMode
        ThemeMode.LIGHT  -> Icons.Default.LightMode
        ThemeMode.NORDIC -> Icons.Default.AcUnit
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = currentIcon,
                contentDescription = "Select theme",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { mode ->
                val (icon, title) = when (mode) {
                    ThemeMode.DARK   -> Icons.Default.DarkMode to "DARK"
                    ThemeMode.LIGHT  -> Icons.Default.LightMode to "LIGHT"
                    ThemeMode.NORDIC -> Icons.Default.AcUnit to "NORDIC"
                }
                val selectedTint = if (mode == themeMode) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                DropdownMenuItem(
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = selectedTint
                        )
                    },
                    leadingIcon = { Icon(icon, contentDescription = null, tint = selectedTint) },
                    onClick = {
                        onSetTheme(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun StreamCard(
    label: String,
    sourceName: String,
    subLabel: String?,
    state: StreamState,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onToggle: () -> Unit,
    onSelectSource: () -> Unit,
    accentColor: Color,
    icon: @Composable () -> Unit
) {
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = if (state.isActive) accentColor else MaterialTheme.colorScheme.outline
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusDot(state.status)
                    SectionHeader(text = label)
                }
                StatusLabel(state.status)
            }

            // Source info + change button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sourceName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subLabel != null) {
                        Text(
                            text = subLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onSelectSource) {
                    icon()
                    Text(
                        text = " CHANGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor
                    )
                }
            }

            // Volume
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "VOL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NeonSlider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    color = accentColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    textAlign = TextAlign.End,
                    // Reserve room for "100%" so the slider doesn't resize mid-drag
                    modifier = Modifier.widthIn(min = 40.dp)
                )
            }

            // Play / pause
            val playTint by animateColorAsState(
                targetValue = if (state.isActive) accentColor
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(300),
                label = "playTint"
            )
            FilledTonalIconButton(
                onClick = onToggle,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = accentColor.copy(alpha = 0.12f),
                    contentColor = playTint
                )
            ) {
                Icon(
                    imageVector = if (state.isActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isActive) "Pause $label" else "Play $label",
                    modifier = Modifier.size(32.dp)
                )
            }

            // Error message
            if (state.error != null) {
                Text(
                    text = "ERR: ${state.error}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
