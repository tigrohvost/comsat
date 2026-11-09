package com.comsat.audio.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.comsat.audio.service.sleepTimeText
import com.comsat.audio.viewmodel.MainViewModel

@Composable
fun SettingsScreen(navController: NavController, viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val remaining by viewModel.sleepRemaining.collectAsStateWithLifecycle()
    val atc by viewModel.atcState.collectAsStateWithLifecycle()
    val soma by viewModel.somaState.collectAsStateWithLifecycle()
    var minutes by rememberSaveable { mutableStateOf("") }
    val customMinutes = minutes.toIntOrNull()?.takeIf { it in 1..1440 }
    Scaffold(topBar = { UtilityHeader("SETTINGS") { navController.popBackStack() } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("SLEEP TIMER", style = MaterialTheme.typography.titleMedium)
            Text(remaining?.let(::sleepTimeText) ?: "TIMER OFF")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (value in listOf(15, 30, 60)) {
                    OutlinedButton(onClick = { viewModel.setSleepTimer(value) },
                        enabled = atc.canPause || soma.canPause) { Text("$value MIN") }
                }
            }
            OutlinedTextField(value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                label = { Text("Custom minutes (1–1440)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { customMinutes?.let(viewModel::setSleepTimer) },
                    enabled = customMinutes != null && (atc.canPause || soma.canPause)) { Text("START TIMER") }
                TextButton(onClick = viewModel::cancelSleepTimer, enabled = remaining != null) { Text("CANCEL TIMER") }
            }
            Text("Both channels fade during the final 30 seconds, then stop.")
            HorizontalDivider()
            SettingSwitch("Lower music during ATC", settings.atcDuckingEnabled, viewModel::setAtcDucking)
            Text("REDUCTION", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (depth in listOf(0.25f, 0.5f, 0.75f)) {
                    FilterChip(selected = settings.atcDuckingDepth == depth,
                        onClick = { viewModel.setDuckingDepth(depth) },
                        label = { Text("${(depth * 100).toInt()}%") })
                }
            }
            HorizontalDivider()
            SettingSwitch("Animate background map", settings.mapMotionEnabled, viewModel::setMapMotion)
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
