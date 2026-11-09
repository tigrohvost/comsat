package com.comsat.audio.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.comsat.audio.viewmodel.MainViewModel

@Composable
fun MixesScreen(navController: NavController, viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val airport by viewModel.selectedAirport.collectAsStateWithLifecycle()
    val station by viewModel.selectedStation.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = { UtilityHeader("SAVED MIXES") { navController.popBackStack() } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Save the selected sources and channel volumes.")
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) },
                    label = { Text("Mix name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { viewModel.saveMix(name); name = "" },
                    enabled = name.isNotBlank() && (airport != null || station != null)) {
                    Text("SAVE CURRENT MIX")
                }
            }
            if (settings.mixes.isEmpty()) item { Text("NO SAVED MIXES") }
            items(settings.mixes, key = { it.id }) { mix ->
                OutlinedCard(onClick = { viewModel.playMix(mix); navController.popBackStack() }) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(mix.name, style = MaterialTheme.typography.titleMedium)
                            Text(listOfNotNull(mix.airportIcao, mix.station?.title).joinToString(" + "))
                            Text("ATC ${(mix.atcVolume * 100).toInt()} · AMBIENT ${(mix.somaVolume * 100).toInt()}",
                                style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { viewModel.deleteMix(mix.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete mix ${mix.name}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun UtilityHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}
