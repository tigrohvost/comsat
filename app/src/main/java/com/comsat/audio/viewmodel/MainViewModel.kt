package com.comsat.audio.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.comsat.audio.data.model.Airport
import com.comsat.audio.data.model.AtisData
import com.comsat.audio.data.model.SomaStation
import com.comsat.audio.data.model.StreamState
import com.comsat.audio.data.repository.AirportCatalog
import com.comsat.audio.data.repository.LiveAtcRepository
import com.comsat.audio.data.repository.MetarRepository
import com.comsat.audio.data.repository.SettingsRepository
import com.comsat.audio.data.repository.SomaFmRepository
import com.comsat.audio.service.AudioService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val airportCatalog: AirportCatalog,
    application: Application,
    private val liveAtcRepo: LiveAtcRepository,
    private val somaRepo: SomaFmRepository,
    private val settingsRepo: SettingsRepository,
    private val metarRepo: MetarRepository
) : AndroidViewModel(application) {

    private var audioService: AudioService? = null
    private var serviceBound = false
    private val collectJobs = mutableListOf<Job>()

    // Play requests issued before the service connection completes
    private var pendingAtc: Pair<String, String>? = null
    private var pendingSomaStation: SomaStation? = null

    // ─── Service binding ──────────────────────────────────────────────────────

    val atcState: StateFlow<StreamState> get() = _atcState
    val somaState: StateFlow<StreamState> get() = _somaState

    private val _atcState = MutableStateFlow(StreamState())
    private val _somaState = MutableStateFlow(StreamState())

    private val _somaNowPlaying = MutableStateFlow<String?>(null)
    val somaNowPlaying: StateFlow<String?> = _somaNowPlaying

    // Live FFT bands per stream, forwarded from the service
    private val _atcSpectrum = MutableStateFlow(FloatArray(0))
    val atcSpectrum: StateFlow<FloatArray> = _atcSpectrum

    private val _somaSpectrum = MutableStateFlow(FloatArray(0))
    val somaSpectrum: StateFlow<FloatArray> = _somaSpectrum

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = (binder as AudioService.AudioBinder).getService()
            audioService = svc

            collectJobs.forEach { it.cancel() }
            collectJobs.clear()
            collectJobs += viewModelScope.launch { svc.atcState.collect { _atcState.value = it } }
            collectJobs += viewModelScope.launch { svc.somaState.collect { _somaState.value = it } }
            collectJobs += viewModelScope.launch { svc.somaNowPlaying.collect { _somaNowPlaying.value = it } }
            collectJobs += viewModelScope.launch { svc.atcSpectrum.collect { _atcSpectrum.value = it } }
            collectJobs += viewModelScope.launch { svc.somaSpectrum.collect { _somaSpectrum.value = it } }

            svc.setAtcVolume(_atcVolume.value)
            svc.setSomaVolume(_somaVolume.value)

            pendingAtc?.let { (url, label) -> svc.playAtc(url, label) }
            pendingAtc = null
            pendingSomaStation?.let { startSoma(svc, it) }
            pendingSomaStation = null
        }

        override fun onServiceDisconnected(name: ComponentName) {
            audioService = null
        }
    }

    // ─── Airport state ────────────────────────────────────────────────────────

    // Starts as the bundled catalog (map + list render immediately); the status
    // probe swaps in copies with activeFeed set.
    private val _airports = MutableStateFlow(airportCatalog.airports)
    val airports: StateFlow<List<Airport>> = _airports

    private val _airportsLoading = MutableStateFlow(false)
    val airportsLoading: StateFlow<Boolean> = _airportsLoading

    private val _selectedAirport = MutableStateFlow<Airport?>(null)
    val selectedAirport: StateFlow<Airport?> = _selectedAirport

    // ─── ATIS / METAR state ───────────────────────────────────────────────────

    private val _atisData = MutableStateFlow<AtisData?>(null)
    val atisData: StateFlow<AtisData?> = _atisData

    // ─── Network status (footer telemetry) ────────────────────────────────────

    private val _networkOnline = MutableStateFlow(true)
    val networkOnline: StateFlow<Boolean> = _networkOnline

    private val connectivityManager: ConnectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateNetworkStatus()

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            _networkOnline.value = networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
        }

        override fun onLost(network: Network) = updateNetworkStatus()
    }

    // ─── Station state ────────────────────────────────────────────────────────

    private val _stations = MutableStateFlow<List<SomaStation>>(emptyList())
    val stations: StateFlow<List<SomaStation>> = _stations

    private val _stationsLoading = MutableStateFlow(false)
    val stationsLoading: StateFlow<Boolean> = _stationsLoading

    private val _stationsError = MutableStateFlow<String?>(null)
    val stationsError: StateFlow<String?> = _stationsError

    private val _selectedStation = MutableStateFlow<SomaStation?>(null)
    val selectedStation: StateFlow<SomaStation?> = _selectedStation

    // ─── Volume state ─────────────────────────────────────────────────────────

    private val _atcVolume = MutableStateFlow(0.8f)
    val atcVolume: StateFlow<Float> = _atcVolume

    private val _somaVolume = MutableStateFlow(0.5f)
    val somaVolume: StateFlow<Float> = _somaVolume

    // ─── Init ─────────────────────────────────────────────────────────────────

    private var restoredStationId: String? = null
    private var persistVolumesJob: Job? = null

    init {
        bindService()
        restoreSettings()
        applyCachedAirportStatus()
        loadStations()
        observeAtis()
        registerNetworkCallback()
    }

    private fun restoreSettings() {
        viewModelScope.launch {
            val s = settingsRepo.settings.first()
            _atcVolume.value = s.atcVolume
            _somaVolume.value = s.somaVolume
            // Service may connect later; onServiceConnected re-applies current values
            audioService?.setAtcVolume(s.atcVolume)
            audioService?.setSomaVolume(s.somaVolume)
            s.airportIcao?.let { icao ->
                if (_selectedAirport.value == null) {
                    _selectedAirport.value = _airports.value.find { it.icao == icao }
                }
            }
            restoredStationId = s.stationId
            applyRestoredStation()
        }
    }

    private fun applyRestoredStation() {
        val id = restoredStationId ?: return
        if (_selectedStation.value != null) return
        _stations.value.find { it.id == id }?.let {
            _selectedStation.value = it
            restoredStationId = null
        }
    }

    private fun bindService() {
        val ctx = getApplication<Application>()
        serviceBound = ctx.bindService(
            Intent(ctx, AudioService::class.java), serviceConnection, Context.BIND_AUTO_CREATE
        )
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    // Launch never sweeps the whole catalog: that is 36+ stream connections
    // to LiveATC every time the app opens, the pattern that gets an IP
    // rate-limited. Show what the cache still knows and probe only the
    // airport that is actually about to play.
    private fun applyCachedAirportStatus() {
        viewModelScope.launch {
            runCatching { liveAtcRepo.getAirportsFromCache() }
                .getOrNull()?.let { _airports.value = it }
        }
    }

    private var airportsJob: Job? = null

    // Full sweep. force = false only probes airports whose cached status has
    // expired; the refresh button passes force = true.
    fun loadAirports(force: Boolean = false) {
        if (airportsJob?.isActive == true) return
        airportsJob = viewModelScope.launch {
            _airportsLoading.value = true
            try {
                updateAirports(liveAtcRepo.getAirportsWithStatus(force))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The catalog itself is bundled; only online probes are lost.
            } finally {
                _airportsLoading.value = false
            }
        }
    }

    // Called when the airport list opens: cheap if the cache is fresh.
    fun ensureAirportsProbed() {
        if (_airports.value.all { it.probed }) return
        loadAirports(force = false)
    }

    private fun updateAirports(probed: List<Airport>) {
        _airports.value = probed
        // Keep the selection in step so its label/feed reflect the probe,
        // without touching whatever is already playing.
        _selectedAirport.value?.let { sel ->
            probed.find { it.icao == sel.icao }?.let { _selectedAirport.value = it }
        }
    }

    private fun updateAirport(probed: Airport) {
        _airports.value = _airports.value.map { if (it.icao == probed.icao) probed else it }
        if (_selectedAirport.value?.icao == probed.icao) _selectedAirport.value = probed
    }

    fun loadStations() {
        viewModelScope.launch {
            _stationsLoading.value = true
            _stationsError.value = null
            try {
                _stations.value = somaRepo.getStations()
                applyRestoredStation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Rain's own stations don't depend on the SomaFM API — keep
                // them selectable even when the directory is down.
                _stations.value = somaRepo.customStations
                applyRestoredStation()
                _stationsError.value = friendlyLoadError(e)
            } finally {
                _stationsLoading.value = false
            }
        }
    }

    // METAR updates roughly twice an hour; refetch on airport change, then poll.
    // Failed fetches keep the last value — collectLatest resets it per airport.
    private fun observeAtis() {
        viewModelScope.launch {
            _selectedAirport.collectLatest { airport ->
                _atisData.value = null
                if (airport == null) return@collectLatest
                while (true) {
                    metarRepo.fetchMetar(airport.icao)?.let { _atisData.value = it }
                    delay(10 * 60 * 1000L)
                }
            }
        }
    }

    private fun registerNetworkCallback() {
        updateNetworkStatus()
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    private fun updateNetworkStatus() {
        val active = connectivityManager.activeNetwork
        val capabilities = active?.let(connectivityManager::getNetworkCapabilities)
        _networkOnline.value = capabilities?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_VALIDATED
        ) == true
    }

    // Raw exception messages are user-hostile; collapse into short HUD-style statuses
    private fun friendlyLoadError(t: Throwable): String = when (t) {
        is java.net.UnknownHostException,
        is java.net.ConnectException,
        is java.net.SocketTimeoutException -> "NO SIGNAL · CHECK CONNECTION"
        is java.io.IOException -> "NETWORK FAULT"
        else -> "STATION LIST UNAVAILABLE"
    }

    private var selectJob: Job? = null

    fun selectAirport(airport: Airport) {
        _selectedAirport.value = airport
        selectJob?.cancel()
        selectJob = viewModelScope.launch {
            settingsRepo.setAirport(airport.icao)
            // Find out which feed is streaming before starting playback, so a
            // dead preferred mount does not put the player into an error loop.
            // Instant when the status cache is fresh; a second or two otherwise.
            val resolved = runCatching { liveAtcRepo.probeAirport(airport) }.getOrDefault(airport)
            updateAirport(resolved)
            playAtc(resolved)
        }
    }

    private fun playAtc(airport: Airport) {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, AudioService::class.java))
        val label = "${airport.icao} ${airport.name}"
        val svc = audioService
        if (svc != null) svc.playAtc(airport.streamUrl, label)
        else pendingAtc = airport.streamUrl to label
    }

    fun toggleAtcPlayback() {
        if (_atcState.value.isActive) audioService?.stopAtc()
        else _selectedAirport.value?.let { selectAirport(it) }
    }

    fun selectStation(station: SomaStation) {
        _selectedStation.value = station
        viewModelScope.launch { settingsRepo.setStation(station.id) }
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, AudioService::class.java))
        val svc = audioService
        if (svc != null) startSoma(svc, station)
        else pendingSomaStation = station
    }

    private fun startSoma(svc: AudioService, station: SomaStation) {
        val apiBase = station.nextTrackApiBase
        // Direct stations carry a plain stream URL; chained ones (Rain Radio)
        // resolve tracks through their /next-track API inside the service.
        if (apiBase != null) svc.playSomaChained(apiBase, station.title)
        else svc.playSoma(station.streamUrl, station.title)
    }

    fun toggleSomaPlayback() {
        if (_somaState.value.isActive) audioService?.stopSoma()
        else _selectedStation.value?.let { selectStation(it) }
    }

    fun setAtcVolume(v: Float) {
        _atcVolume.value = v
        audioService?.setAtcVolume(v)
        persistVolumesDebounced()
    }

    fun setSomaVolume(v: Float) {
        _somaVolume.value = v
        audioService?.setSomaVolume(v)
        persistVolumesDebounced()
    }

    // Slider drags fire per-pixel; coalesce DataStore writes
    private fun persistVolumesDebounced() {
        persistVolumesJob?.cancel()
        persistVolumesJob = viewModelScope.launch {
            delay(300)
            settingsRepo.setVolumes(_atcVolume.value, _somaVolume.value)
        }
    }

    override fun onCleared() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
        super.onCleared()
    }
}
