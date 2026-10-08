package com.comsat.audio.widget

import com.comsat.audio.data.model.StreamStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackSnapshot(
    val atcStatus: StreamStatus = StreamStatus.IDLE,
    val somaStatus: StreamStatus = StreamStatus.IDLE,
    val atcPreparing: Boolean = false,
    val somaPreparing: Boolean = false
)

// Only the live process can report playback. Persisting PLAYING would leave a
// widget claiming that audio is active after Android has killed the service.
@Singleton
class PlaybackSnapshotRepository @Inject constructor() {
    private val mutableSnapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()

    fun updatePlayback(atc: StreamStatus, soma: StreamStatus) {
        mutableSnapshot.update { it.copy(atcStatus = atc, somaStatus = soma) }
    }

    fun setAtcPreparing(preparing: Boolean) {
        mutableSnapshot.update { it.copy(atcPreparing = preparing) }
    }

    fun setSomaPreparing(preparing: Boolean) {
        mutableSnapshot.update { it.copy(somaPreparing = preparing) }
    }

    fun clearPlayback() {
        mutableSnapshot.value = PlaybackSnapshot()
    }
}
