package ir.keyvanadili.noghteyab.service

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Simple in-memory holder for the latest GPS fix, shared across the app. */
object LocationRepository {
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    fun update(location: Location) {
        _currentLocation.value = location
    }

    fun setTracking(tracking: Boolean) {
        _isTracking.value = tracking
    }
}
