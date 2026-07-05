package com.comsat.audio.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.comsat.audio.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "comsat_settings")

data class AppSettings(
    val atcVolume: Float = 0.8f,
    val somaVolume: Float = 0.5f,
    val airportIcao: String? = null,
    val stationId: String? = null,
    val themeMode: ThemeMode = ThemeMode.DARK
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ATC_VOLUME = floatPreferencesKey("atc_volume")
        val SOMA_VOLUME = floatPreferencesKey("soma_volume")
        val AIRPORT_ICAO = stringPreferencesKey("airport_icao")
        val STATION_ID = stringPreferencesKey("station_id")
        val THEME_MODE = intPreferencesKey("theme_mode")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            atcVolume = prefs[Keys.ATC_VOLUME] ?: 0.8f,
            somaVolume = prefs[Keys.SOMA_VOLUME] ?: 0.5f,
            airportIcao = prefs[Keys.AIRPORT_ICAO],
            stationId = prefs[Keys.STATION_ID],
            themeMode = ThemeMode.entries.getOrElse(prefs[Keys.THEME_MODE] ?: 0) { ThemeMode.DARK }
        )
    }

    suspend fun setVolumes(atc: Float, soma: Float) {
        context.dataStore.edit {
            it[Keys.ATC_VOLUME] = atc
            it[Keys.SOMA_VOLUME] = soma
        }
    }

    suspend fun setAirport(icao: String) {
        context.dataStore.edit { it[Keys.AIRPORT_ICAO] = icao }
    }

    suspend fun setStation(id: String) {
        context.dataStore.edit { it[Keys.STATION_ID] = id }
    }

    suspend fun cycleTheme() {
        context.dataStore.edit {
            val current = it[Keys.THEME_MODE] ?: 0
            it[Keys.THEME_MODE] = (current + 1) % ThemeMode.entries.size
        }
    }
}
