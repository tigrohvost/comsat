package com.comsat.audio

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.comsat.audio.data.repository.SettingsRepository
import com.comsat.audio.ui.navigation.ComsatNavGraph
import com.comsat.audio.ui.theme.ComsatTheme
import com.comsat.audio.ui.theme.ThemeMode
import com.comsat.audio.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var widgetUpdater: WidgetUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep Android's contrast protection for three-button navigation.
        enableEdgeToEdge()
        // Playback uses permission-exempt MediaSession notifications. Opening or
        // recreating the panel must not launch a notification permission activity.
        setContent {
            val themeModeFlow = remember(settingsRepo) {
                settingsRepo.settings.map { it.themeMode }
            }
            val themeMode by themeModeFlow
                .collectAsStateWithLifecycle(initialValue = ThemeMode.NORDIC)
            LaunchedEffect(themeMode) {
                // Both icon sets and the three-button navigation scrim follow
                // the app theme, which can differ from Android's system theme.
                val dark = themeMode != ThemeMode.LIGHT
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = 0xE6FFFFFF.toInt(),
                        darkScrim = 0x801B1B1B.toInt()
                    ) { dark }
                )
            }
            ComsatTheme(
                mode = themeMode,
                onSetTheme = { mode -> lifecycleScope.launch { settingsRepo.setTheme(mode) } }
            ) {
                val navController = rememberNavController()
                ComsatNavGraph(navController = navController)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Only a visible app may take a fresh location fix for the weather widget.
        widgetUpdater.requestWeather(foreground = true)
    }

}
