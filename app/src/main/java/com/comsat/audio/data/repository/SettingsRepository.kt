package com.comsat.audio.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.comsat.audio.ui.theme.ThemeMode
import com.comsat.audio.data.model.MixStorage
import com.comsat.audio.data.model.SavedMix
import com.comsat.audio.data.model.SomaStation
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
    val stationTitle: String? = null,
    val stationNetwork: String? = null,
    val themeMode: ThemeMode = ThemeMode.NORDIC,
    val stationSnapshot: SomaStation? = null,
    val favoriteAirports: Set<String> = emptySet(),
    val favoriteStations: Set<String> = emptySet(),
    val mixes: List<SavedMix> = emptyList(),
    val atcDuckingEnabled: Boolean = false,
    val atcDuckingDepth: Float = 0.5f,
    val mapMotionEnabled: Boolean = true
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
        val STATION_TITLE = stringPreferencesKey("station_title")
        val STATION_NETWORK = stringPreferencesKey("station_network")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val STATION_SNAPSHOT = stringPreferencesKey("station_snapshot")
        val FAVORITE_AIRPORTS = stringSetPreferencesKey("favorite_airports")
        val FAVORITE_STATIONS = stringSetPreferencesKey("favorite_stations")
        val MIXES = stringPreferencesKey("mixes")
        val ATC_DUCKING = booleanPreferencesKey("atc_ducking")
        val DUCKING_DEPTH = floatPreferencesKey("ducking_depth")
        val MAP_MOTION = booleanPreferencesKey("map_motion")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            atcVolume = prefs[Keys.ATC_VOLUME] ?: 0.8f,
            somaVolume = prefs[Keys.SOMA_VOLUME] ?: 0.5f,
            airportIcao = prefs[Keys.AIRPORT_ICAO],
            stationId = prefs[Keys.STATION_ID],
            stationTitle = prefs[Keys.STATION_TITLE],
            stationNetwork = prefs[Keys.STATION_NETWORK],
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { ThemeMode.entries.getOrNull(it) }
                ?: ThemeMode.NORDIC,
            stationSnapshot = MixStorage.station(prefs[Keys.STATION_SNAPSHOT]),
            favoriteAirports = prefs[Keys.FAVORITE_AIRPORTS].orEmpty(),
            favoriteStations = prefs[Keys.FAVORITE_STATIONS].orEmpty(),
            mixes = MixStorage.mixes(prefs[Keys.MIXES]),
            atcDuckingEnabled = prefs[Keys.ATC_DUCKING] ?: false,
            atcDuckingDepth = (prefs[Keys.DUCKING_DEPTH] ?: 0.5f).coerceIn(0f, 1f),
            mapMotionEnabled = prefs[Keys.MAP_MOTION] ?: true
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

    suspend fun clearAirport() {
        context.dataStore.edit { it.remove(Keys.AIRPORT_ICAO) }
    }

    suspend fun clearStation() {
        context.dataStore.edit {
            it.remove(Keys.STATION_ID)
            it.remove(Keys.STATION_TITLE)
            it.remove(Keys.STATION_NETWORK)
            it.remove(Keys.STATION_SNAPSHOT)
        }
    }

    suspend fun setStation(station: SomaStation) {
        // A widget must be able to name the selection without loading the
        // online station directory, including after the process is restarted.
        context.dataStore.edit {
            it[Keys.STATION_ID] = station.id
            it[Keys.STATION_TITLE] = station.title
            it[Keys.STATION_NETWORK] = station.network
            it[Keys.STATION_SNAPSHOT] = MixStorage.encode(station)
        }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.ordinal }
    }

    suspend fun toggleFavorite(id: String, airport: Boolean) {
        val key = if (airport) Keys.FAVORITE_AIRPORTS else Keys.FAVORITE_STATIONS
        context.dataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            prefs[key] = if (id in current) current - id else current + id
        }
    }

    suspend fun saveMix(mix: SavedMix) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MIXES] = MixStorage.encode(
                MixStorage.mixes(prefs[Keys.MIXES]).filterNot { it.id == mix.id } + mix)
        }
    }

    suspend fun deleteMix(id: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MIXES] = MixStorage.encode(MixStorage.mixes(prefs[Keys.MIXES]).filterNot { it.id == id })
        }
    }

    suspend fun setAtcDucking(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ATC_DUCKING] = enabled }
    }

    suspend fun setDuckingDepth(depth: Float) {
        context.dataStore.edit { it[Keys.DUCKING_DEPTH] = depth.coerceIn(0f, 1f) }
    }

    suspend fun setMapMotion(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MAP_MOTION] = enabled }
    }
}
