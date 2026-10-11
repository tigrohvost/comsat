package com.comsat.audio.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.comsat.audio.data.model.WeatherPlace
import com.comsat.audio.data.model.WeatherState
import com.comsat.audio.data.repository.AirportCatalog
import com.comsat.audio.data.repository.AppSettings
import com.comsat.audio.data.repository.DeviceLocator
import com.comsat.audio.data.repository.SettingsRepository
import com.comsat.audio.data.repository.WeatherRepository
import com.comsat.audio.ui.screen.WeatherLocationScreen
import com.comsat.audio.ui.theme.ComsatTheme
import com.comsat.audio.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

// The weather widget's configuration screen; the widget's weather half opens it too.
// One location serves every weather widget.
@AndroidEntryPoint
class WeatherLocationActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var airportCatalog: AirportCatalog
    @Inject lateinit var weatherRepository: WeatherRepository
    @Inject lateinit var locator: DeviceLocator
    @Inject lateinit var widgetUpdater: WidgetUpdater

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var locating by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                locate()
            } else {
                message = "Location access was denied. Search for a city instead."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // A launcher discards a newly placed widget unless a location is confirmed.
        setResult(RESULT_CANCELED, result())
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            val weather by weatherRepository.state.collectAsState(initial = WeatherState())
            val airportFlow = remember(settingsRepository) {
                settingsRepository.settings.map { it.airportIcao?.let(airportCatalog::find) }
            }
            val airport by airportFlow.collectAsState(initial = null)
            LaunchedEffect(settings.themeMode) {
                val dark = settings.themeMode != ThemeMode.LIGHT
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = 0xE6FFFFFF.toInt(),
                        darkScrim = 0x801B1B1B.toInt()
                    ) { dark }
                )
            }
            ComsatTheme(mode = settings.themeMode) {
                WeatherLocationScreen(
                    state = weather,
                    airport = airport,
                    locating = locating,
                    message = message,
                    onBack = ::finish,
                    onCurrentLocation = ::useCurrentLocation,
                    onAirport = { save { weatherRepository.useAirport() } },
                    onPlace = { place -> save { weatherRepository.usePlace(place) } },
                    search = weatherRepository::search
                )
            }
        }
    }

    private fun useCurrentLocation() {
        message = null
        if (locator.hasPermission()) locate() else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun locate() {
        if (locating) return
        if (!locator.isEnabled()) {
            message = "Location is turned off. Turn it on or search for a city."
            return
        }
        locating = true
        lifecycleScope.launch {
            val place: WeatherPlace? = try {
                weatherRepository.locate()
            } finally {
                locating = false
            }
            if (place == null) {
                message = "Could not determine your location. Try again or search for a city."
            } else {
                save { weatherRepository.useCurrentLocation(place) }
            }
        }
    }

    private fun save(update: suspend () -> Unit) {
        lifecycleScope.launch {
            update()
            widgetUpdater.start()
            widgetUpdater.requestWeather(force = true)
            setResult(RESULT_OK, result())
            finish()
        }
    }

    private fun result() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}
