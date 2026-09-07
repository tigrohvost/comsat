package com.comsat.audio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.comsat.audio.data.repository.SettingsRepository
import com.comsat.audio.ui.navigation.ComsatNavGraph
import com.comsat.audio.ui.theme.ComsatTheme
import com.comsat.audio.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepo: SettingsRepository

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep Android's contrast protection for three-button navigation.
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            val themeModeFlow = remember(settingsRepo) {
                settingsRepo.settings.map { it.themeMode }
            }
            val themeMode by themeModeFlow
                .collectAsState(initial = ThemeMode.NORDIC)
            LaunchedEffect(themeMode) {
                // The app theme is independent of Android's system theme.
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = themeMode == ThemeMode.LIGHT
                }
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

    // Android 13+: the foreground-service notification is invisible without this
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
