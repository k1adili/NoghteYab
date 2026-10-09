package ir.keyvanadili.noghteyab.service

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Simple in-memory holder for the latest GPS fix, satellite info and recording state. */
object LocationRepository {
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _currentTrackId = MutableStateFlow<Long?>(null)
    val currentTrackId: StateFlow<Long?> = _currentTrackId

    /** Satellites actually used in the current fix. */
    private val _satellitesUsed = MutableStateFlow(0)
    val satellitesUsed: StateFlow<Int> = _satellitesUsed

    /** Satellites currently visible to the GPS chip. */
    private val _satellitesTotal = MutableStateFlow(0)
    val satellitesTotal: StateFlow<Int> = _satellitesTotal

    fun update(location: Location) {
        _currentLocation.value = location
    }

    fun updateSatellites(used: Int, total: Int) {
        _satellitesUsed.value = used
        _satellitesTotal.value = total
    }

    fun setTracking(tracking: Boolean) {
        _isTracking.value = tracking
        if (!tracking) {
            // Service died/stopped: recording can no longer log points.
            _isRecording.value = false
            _currentTrackId.value = null
            _satellitesUsed.value = 0
            _satellitesTotal.value = 0
        }
    }

    fun startRecording(trackId: Long) {
        _currentTrackId.value = trackId
        _isRecording.value = true
    }

    fun stopRecording() {
        _isRecording.value = false
        _currentTrackId.value = null
    }
}
