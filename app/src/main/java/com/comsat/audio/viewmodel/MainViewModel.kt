package com.comsat.audio.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.comsat.audio.data.model.Airport
import com.comsat.audio.data.model.SomaStation
import com.comsat.audio.data.model.StreamState
import com.comsat.audio.data.repository.AIRPORT_CATALOG
import com.comsat.audio.data.repository.LiveAtcRepository
import com.comsat.audio.data.repository.SettingsRepository
import com.comsat.audio.data.repository.SomaFmRepository
import com.comsat.audio.service.AudioService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val liveAtcRepo: LiveAtcRepository,
    private val somaRepo: SomaFmRepository,
    private val settingsRepo: SettingsRepository
) : AndroidViewModel(application) {

    private var audioService: AudioService? = null
    private var serviceBound = false
    private val collectJobs = mutableListOf<Job>()

    // Play requests (url to label) issued before the service connection completes
    private var pendingAtc: Pair<String, String>? = null
    private var pendingSoma: Pair<String, String>? = null

    // ─── Service binding ──────────────────────────────────────────────────────

    val atcState: StateFlow<StreamState> get() = _atcState
    val somaState: StateFlow<StreamState> get() = _somaState

    private val _atcState = MutableStateFlow(StreamState())
    private val _somaState = MutableStateFlow(StreamState())

    private val _somaNowPlaying = MutableStateFlow<String?>(null)
    val somaNowPlaying: StateFlow<String?> = _somaNowPlaying

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = (binder as AudioService.AudioBinder).getService()
            audioService = svc

            collectJobs.forEach { it.cancel() }
            collectJobs.clear()
            collectJobs += viewModelScope.launch { svc.atcState.collect { _atcState.value = it } }
            collectJobs += viewModelScope.launch { svc.somaState.collect { _somaState.value = it } }
            collectJobs += viewModelScope.launch { svc.somaNowPlaying.collect { _somaNowPlaying.value = it } }

            svc.setAtcVolume(_atcVolume.value)
            svc.setSomaVolume(_somaVolume.value)

            pendingAtc?.let { (url, label) -> svc.playAtc(url, label) }
            pendingAtc = null
            pendingSoma?.let { (url, label) -> svc.playSoma(url, label) }
            pendingSoma = null
        }

        override fun onServiceDisconnected(name: ComponentName) {
            audioService = null
        }
    }

    // ─── Airport state ────────────────────────────────────────────────────────

    private val _airports = MutableStateFlow<List<Airport>>(emptyList())
    val airports: StateFlow<List<Airport>> = _airports

    private val _airportsLoading = MutableStateFlow(false)
    val airportsLoading: StateFlow<Boolean> = _airportsLoading

    private val _selectedAirport = MutableStateFlow<Airport?>(null)
    val selectedAirport: StateFlow<Airport?> = _selectedAirport

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
        loadAirports()
        loadStations()
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
                    _selectedAirport.value = AIRPORT_CATALOG.find { it.icao == icao }
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

    fun loadAirports() {
        viewModelScope.launch {
            _airportsLoading.value = true
            _airports.value = liveAtcRepo.getAirportsWithStatus()
            _airportsLoading.value = false
        }
    }

    fun loadStations() {
        viewModelScope.launch {
            _stationsLoading.value = true
            _stationsError.value = null
            runCatching { somaRepo.getStations() }
                .onSuccess {
                    _stations.value = it
                    applyRestoredStation()
                }
                .onFailure { _stationsError.value = friendlyLoadError(it) }
            _stationsLoading.value = false
        }
    }

    // Raw exception messages are user-hostile; collapse into short HUD-style statuses
    private fun friendlyLoadError(t: Throwable): String = when (t) {
        is java.net.UnknownHostException,
        is java.net.ConnectException,
        is java.net.SocketTimeoutException -> "NO SIGNAL · CHECK CONNECTION"
        is java.io.IOException -> "NETWORK FAULT"
        else -> "STATION LIST UNAVAILABLE"
    }

    fun selectAirport(airport: Airport) {
        _selectedAirport.value = airport
        viewModelScope.launch { settingsRepo.setAirport(airport.icao) }
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, AudioService::class.java))
        // streamUrl is now a direct Icecast URL — no resolution step needed
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
        // streamUrl is a direct ice*.somafm.com URL — no PLS resolution needed
        val svc = audioService
        if (svc != null) svc.playSoma(station.streamUrl, station.title)
        else pendingSoma = station.streamUrl to station.title
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
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
        super.onCleared()
    }
}
