package com.comsat.audio.data.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.comsat.audio.R
import com.comsat.audio.data.api.MetNorwayApi
import com.comsat.audio.data.api.OpenMeteoApi
import com.comsat.audio.data.model.WeatherCondition
import com.comsat.audio.data.model.WeatherPlace
import com.comsat.audio.data.model.WeatherReport
import com.comsat.audio.data.model.WeatherSource
import com.comsat.audio.data.model.WeatherState
import com.comsat.audio.data.model.decodeHourly
import com.comsat.audio.data.model.encodeHourly
import com.comsat.audio.data.model.metCoordinate
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// Kept apart from comsat_settings so backups can leave location data out.
private val Context.weatherStore by preferencesDataStore(name = "comsat_weather")

@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    // Lazy: widget broadcasts build this repository long before any request.
    private val openMeteo: Lazy<OpenMeteoApi>,
    private val metNorway: Lazy<MetNorwayApi>,
    private val locator: DeviceLocator,
    private val settingsRepository: SettingsRepository,
    private val airportCatalog: AirportCatalog
) {
    private object Keys {
        val SOURCE = stringPreferencesKey("source")
        val PLACE_NAME = stringPreferencesKey("place_name")
        val PLACE_LAT = doublePreferencesKey("place_lat")
        val PLACE_LON = doublePreferencesKey("place_lon")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val DEVICE_LAT = doublePreferencesKey("device_lat")
        val DEVICE_LON = doublePreferencesKey("device_lon")
        val REPORT_TEMP = doublePreferencesKey("report_temp")
        val REPORT_CONDITION = stringPreferencesKey("report_condition")
        val REPORT_DAY = booleanPreferencesKey("report_day")
        val REPORT_LAT = doublePreferencesKey("report_lat")
        val REPORT_LON = doublePreferencesKey("report_lon")
        val REPORT_TIME = longPreferencesKey("report_time")
        val REPORT_HOURLY = stringPreferencesKey("report_hourly")
        val PROVIDER = stringPreferencesKey("provider")
    }

    // Each is a fallback for the other: some networks cannot reach one of them
    // (seen: api.open-meteo.com timing out on a home connection in Russia while
    // api.met.no answered).
    private enum class Provider { OPEN_METEO, MET_NORWAY }

    val state: Flow<WeatherState> = context.weatherStore.data.map { readState(it) }

    private val refreshMutex = Mutex()
    // Both guarded by refreshMutex
    private var lastAttempt: Pair<WeatherPlace, Long>? = null
    private var lastFixAt: Long? = null

    private suspend fun selectedAirport() = settingsRepository.settings.first().airportIcao?.let(airportCatalog::find)

    suspend fun useCurrentLocation(location: WeatherPlace) {
        context.weatherStore.edit {
            it[Keys.SOURCE] = WeatherSource.CURRENT_LOCATION.name
            it.putPlace(location, Keys.DEVICE_NAME, Keys.DEVICE_LAT, Keys.DEVICE_LON)
        }
    }

    suspend fun useAirport() {
        context.weatherStore.edit { it[Keys.SOURCE] = WeatherSource.AIRPORT.name }
    }

    suspend fun usePlace(place: WeatherPlace) {
        context.weatherStore.edit {
            it[Keys.SOURCE] = WeatherSource.PLACE.name
            it.putPlace(place, Keys.PLACE_NAME, Keys.PLACE_LAT, Keys.PLACE_LON)
        }
    }

    // Throws on network failure so the picker can tell it from "no matches".
    suspend fun search(query: String): List<WeatherPlace> =
        openMeteo.get().searchPlaces(query.trim(), Locale.getDefault().language)
            .results.orEmpty()
            .mapNotNull { it.toPlace() }

    // Foreground only: Android withholds fresh fixes from background apps.
    suspend fun locate(): WeatherPlace? {
        val location = locator.currentLocation() ?: return null
        return describe(location.latitude, location.longitude)
    }

    /**
     * Updates the device location for [WeatherSource.CURRENT_LOCATION], then
     * fetches weather for the selected place unless a recent report covers it.
     * Failures keep the previous report; one place is retried at most every few minutes.
     */
    suspend fun refresh(force: Boolean = false, foreground: Boolean = false): Unit = refreshMutex.withLock {
        var current = state.first()
        val now = System.currentTimeMillis()
        val airport = selectedAirport()
        if (current.source == WeatherSource.CURRENT_LOCATION) {
            val reportFresh = current.target(airport)?.let { current.report?.isUsable(it, now, REPORT_TTL_MS) } == true
            if (updateDeviceLocation(current.deviceLocation, foreground, checkLastKnown = !reportFresh)) {
                current = state.first()
            }
        }
        val target = current.target(airport) ?: return@withLock
        if (!force) {
            // Reports saved before 1.6.0 have no hourly forecast; replace them early.
            val report = current.report?.takeIf { it.hourly.isNotEmpty() }
            if (report?.isUsable(target, now, REPORT_TTL_MS) == true) return@withLock
            val attempt = lastAttempt
            if (attempt != null && attempt.first.isNear(target.latitude, target.longitude) &&
                now - attempt.second in 0 until RETRY_INTERVAL_MS
            ) return@withLock
        }
        lastAttempt = target to now
        // The provider that last answered goes first, so a blocked one costs
        // a timeout once rather than on every update.
        val preferred = context.weatherStore.data.first()[Keys.PROVIDER]
        val providers = Provider.entries.sortedByDescending { it.name == preferred }
        val (provider, report) = providers.firstNotNullOfOrNull { candidate ->
            fetch(candidate, target, now)?.let { candidate to it }
        } ?: return@withLock
        context.weatherStore.edit {
            it[Keys.PROVIDER] = provider.name
            it[Keys.REPORT_TEMP] = report.tempC
            it[Keys.REPORT_CONDITION] = report.condition.name
            it[Keys.REPORT_DAY] = report.isDay
            it[Keys.REPORT_LAT] = report.latitude
            it[Keys.REPORT_LON] = report.longitude
            it[Keys.REPORT_TIME] = report.fetchedAtMillis
            it[Keys.REPORT_HOURLY] = encodeHourly(report.hourly)
        }
    }

    private suspend fun fetch(provider: Provider, place: WeatherPlace, now: Long): WeatherReport? = try {
        withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            when (provider) {
                Provider.OPEN_METEO ->
                    openMeteo.get().getForecast(place.latitude, place.longitude).toReport(place, now)
                Provider.MET_NORWAY ->
                    metNorway.get().getForecast(metCoordinate(place.latitude), metCoordinate(place.longitude))
                        .toReport(place, now)
            }
        }.also { if (it == null) Log.w(TAG, "No weather from $provider") }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // Weather is auxiliary; keep showing the previous reading.
        Log.w(TAG, "Could not fetch weather from $provider", error)
        null
    }

    private suspend fun updateDeviceLocation(
        previous: WeatherPlace?,
        foreground: Boolean,
        checkLastKnown: Boolean
    ): Boolean {
        val location = when {
            foreground -> {
                val elapsed = SystemClock.elapsedRealtime()
                val lastFix = lastFixAt
                if (previous != null && lastFix != null && elapsed - lastFix < FIX_INTERVAL_MS) return false
                lastFixAt = elapsed
                locator.currentLocation()
            }
            checkLastKnown -> locator.lastKnownLocation()
            else -> null
        } ?: return false
        if (previous?.isNear(location.latitude, location.longitude) == true) return false
        val place = describe(location.latitude, location.longitude)
        context.weatherStore.edit { it.putPlace(place, Keys.DEVICE_NAME, Keys.DEVICE_LAT, Keys.DEVICE_LON) }
        return true
    }

    private suspend fun describe(latitude: Double, longitude: Double) = WeatherPlace(
        name = locator.placeName(latitude, longitude) ?: context.getString(R.string.weather_current_location),
        latitude = latitude,
        longitude = longitude
    )

    private fun readState(prefs: Preferences): WeatherState {
        fun place(
            name: Preferences.Key<String>, lat: Preferences.Key<Double>, lon: Preferences.Key<Double>
        ): WeatherPlace? = WeatherPlace(prefs[name] ?: return null, prefs[lat] ?: return null, prefs[lon] ?: return null)

        val report = run {
            WeatherReport(
                tempC = prefs[Keys.REPORT_TEMP] ?: return@run null,
                condition = prefs[Keys.REPORT_CONDITION]
                    ?.let { name -> WeatherCondition.entries.find { it.name == name } }
                    ?: return@run null,
                isDay = prefs[Keys.REPORT_DAY] ?: true,
                latitude = prefs[Keys.REPORT_LAT] ?: return@run null,
                longitude = prefs[Keys.REPORT_LON] ?: return@run null,
                fetchedAtMillis = prefs[Keys.REPORT_TIME] ?: return@run null,
                hourly = decodeHourly(prefs[Keys.REPORT_HOURLY])
            )
        }
        return WeatherState(
            source = prefs[Keys.SOURCE]?.let { name -> WeatherSource.entries.find { it.name == name } },
            place = place(Keys.PLACE_NAME, Keys.PLACE_LAT, Keys.PLACE_LON),
            deviceLocation = place(Keys.DEVICE_NAME, Keys.DEVICE_LAT, Keys.DEVICE_LON),
            report = report
        )
    }

    private fun MutablePreferences.putPlace(
        place: WeatherPlace,
        name: Preferences.Key<String>,
        lat: Preferences.Key<Double>,
        lon: Preferences.Key<Double>
    ) {
        this[name] = place.name
        this[lat] = place.latitude
        this[lon] = place.longitude
    }

    private companion object {
        const val TAG = "ComsatWeather"
        const val REPORT_TTL_MS = 30 * 60_000L
        const val RETRY_INTERVAL_MS = 5 * 60_000L
        const val FIX_INTERVAL_MS = 15 * 60_000L
        // Per provider; both together stay within WidgetUpdater's 20 s budget.
        const val FETCH_TIMEOUT_MS = 6_000L
    }
}
