package com.comsat.audio.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.comsat.audio.BuildConfig
import com.comsat.audio.data.model.StreamState
import com.comsat.audio.ui.components.AtisReadout
import com.comsat.audio.ui.components.AvionicsModule
import com.comsat.audio.ui.components.FooterPlacard
import com.comsat.audio.ui.components.PlacardButton
import com.comsat.audio.ui.components.SpectrumBar
import com.comsat.audio.ui.components.SquareToggleButton
import com.comsat.audio.ui.components.TickFader
import com.comsat.audio.ui.components.WorldMapBackground
import com.comsat.audio.ui.navigation.Screen
import com.comsat.audio.ui.theme.LocalSetTheme
import com.comsat.audio.ui.theme.LocalThemeMode
import com.comsat.audio.ui.theme.ThemeMode
import com.comsat.audio.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val atcState     by viewModel.atcState.collectAsState()
    val somaState    by viewModel.somaState.collectAsState()
    val atcVolume    by viewModel.atcVolume.collectAsState()
    val somaVolume   by viewModel.somaVolume.collectAsState()
    val airport      by viewModel.selectedAirport.collectAsState()
    val station      by viewModel.selectedStation.collectAsState()
    val nowPlaying   by viewModel.somaNowPlaying.collectAsState()
    val atcSpectrum  by viewModel.atcSpectrum.collectAsState()
    val somaSpectrum by viewModel.somaSpectrum.collectAsState()
    val atisData     by viewModel.atisData.collectAsState()
    val netOnline    by viewModel.networkOnline.collectAsState()
    val airports     by viewModel.airports.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WorldMapBackground(
            airports = airports,
            selectedIcao = airport?.icao,
            modifier = Modifier.fillMaxSize()
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp)
        ) {
            // Fit the controls to the window; the spectra absorb spare height.
            val compact = maxHeight < 800.dp || LocalDensity.current.fontScale > 1.2f
            val sideBySide = maxWidth > maxHeight
            val panelSpacing = if (compact) 8.dp else 16.dp
            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(vertical = panelSpacing),
                verticalArrangement = Arrangement.spacedBy(panelSpacing)
            ) {
                PanelHeader()

                StreamPanels(
                    sideBySide = sideBySide,
                    spacing = panelSpacing,
                    modifier = Modifier.weight(1f)
                ) { moduleModifier ->
                    AvionicsModule(
                        title = "COMM 1 · ATC",
                        status = atcState.status,
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = moduleModifier,
                        fillHeight = true,
                        compact = compact
                    ) {
                        StreamModuleBody(
                            sourceName = airport?.let { "${it.icao} · ${it.name.uppercase()}" }
                                ?: "NO AIRPORT SELECTED",
                            subLabel = airport?.let { "${it.city}, ${it.country} — ${it.feed.label}".uppercase() },
                            state = atcState,
                            spectrum = atcSpectrum,
                            volume = atcVolume,
                            onVolumeChange = viewModel::setAtcVolume,
                            onToggle = {
                                if (airport == null) navController.navigate(Screen.Airports.route)
                                else viewModel.toggleAtcPlayback()
                            },
                            onSelectSource = { navController.navigate(Screen.Airports.route) },
                            accent = MaterialTheme.colorScheme.primary,
                            channel = "ATC",
                            hasSource = airport != null,
                            compact = compact,
                            cornerContent = {
                                AtisReadout(
                                    data = atisData,
                                    accent = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }

                    AvionicsModule(
                        title = "COMM 2 · AMBIENT",
                        status = somaState.status,
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = moduleModifier,
                        fillHeight = true,
                        compact = compact
                    ) {
                        StreamModuleBody(
                            sourceName = station?.title?.uppercase() ?: "NO STATION SELECTED",
                            subLabel = (nowPlaying ?: station?.let { "${it.network} — ${it.genre}" })?.uppercase(),
                            state = somaState,
                            spectrum = somaSpectrum,
                            volume = somaVolume,
                            onVolumeChange = viewModel::setSomaVolume,
                            onToggle = {
                                if (station == null) navController.navigate(Screen.Stations.route)
                                else viewModel.toggleSomaPlayback()
                            },
                            onSelectSource = { navController.navigate(Screen.Stations.route) },
                            accent = MaterialTheme.colorScheme.tertiary,
                            channel = "ambient",
                            hasSource = station != null,
                            compact = compact
                        )
                    }
                }

                FooterPlacard(
                    netOnline = netOnline,
                    activeStreams = listOf(atcState.canPause, somaState.canPause).count { it },
                    version = BuildConfig.VERSION_NAME,
                    compact = compact
                )
            }
        }
    }
}

@Composable
private fun StreamPanels(
    sideBySide: Boolean,
    spacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    if (sideBySide) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            content(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            content(Modifier.weight(1f).fillMaxWidth())
        }
    }
}

// ─── Header: brand plate + UTC clock + theme selector ─────────────────────────

@Composable
private fun PanelHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "COMSAT",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 26.sp),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "COMM AUDIO PANEL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            UtcClock()
            ThemeMenuButton()
        }
    }
}

@Composable
private fun UtcClock() {
    var utc by remember { mutableStateOf("--:--:--") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        while (true) {
            utc = fmt.format(Date())
            delay(1_000)
        }
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = "UTC $utc",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Z +0000",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeMenuButton() {
    val themeMode = LocalThemeMode.current
    val setTheme = LocalSetTheme.current
    var expanded by remember { mutableStateOf(false) }

    val currentIcon = when (themeMode) {
        ThemeMode.DARK   -> Icons.Default.DarkMode
        ThemeMode.LIGHT  -> Icons.Default.LightMode
        ThemeMode.NORDIC -> Icons.Default.AcUnit
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = currentIcon,
                contentDescription = "Select theme: ${themeMode.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                        setTheme(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ─── Stream module body: name, spectrum, fader, transport ─────────────────────

@Composable
private fun ColumnScope.StreamModuleBody(
    sourceName: String,
    subLabel: String?,
    state: StreamState,
    spectrum: FloatArray,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onToggle: () -> Unit,
    onSelectSource: () -> Unit,
    accent: Color,
    channel: String,
    hasSource: Boolean,
    compact: Boolean,
    cornerContent: (@Composable () -> Unit)? = null
) {
    // Station name + change button
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sourceName,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                color = accent,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        PlacardButton(text = "CHANGE", onClick = onSelectSource)
    }

    SpectrumBar(
        bands = spectrum,
        active = state.isActive,
        volume = volume,
        accent = accent,
        modifier = Modifier.weight(1f)
    )

    // Fader with digital readout
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "VOL",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TickFader(
            value = volume,
            onValueChange = onVolumeChange,
            accent = accent,
            description = "$channel volume",
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "%03d".format((volume * 100).toInt()),
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 40.dp)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        SquareToggleButton(
            active = state.canPause,
            accent = accent,
            onClick = onToggle,
            description = when {
                state.canPause -> "Pause $channel"
                hasSource -> "Play $channel"
                channel == "ATC" -> "Choose airport"
                else -> "Choose station"
            }
        )
        Spacer(modifier = Modifier.weight(1f))
        cornerContent?.invoke()
    }

    if (state.error != null) {
        Text(
            text = "ERR: ${state.error}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
