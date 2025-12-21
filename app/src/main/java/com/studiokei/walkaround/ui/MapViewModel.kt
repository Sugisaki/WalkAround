package com.studiokei.walkaround.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.studiokei.walkaround.data.database.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MapViewModel(
    private val database: AppDatabase,
    private val locationManager: LocationManager
) : ViewModel() {

    private val _track = MutableStateFlow<List<LatLng>>(emptyList())
    val track: StateFlow<List<LatLng>> = _track.asStateFlow()

    private val _initialBounds = MutableStateFlow<LatLngBounds?>(null)
    val initialBounds: StateFlow<LatLngBounds?> = _initialBounds.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadLastTrack()
    }

    fun loadLastTrack() {
        viewModelScope.launch {
            _isLoading.value = true
            Log.d("MapViewModel", "Loading last track...")
            val lastSession = database.sectionDao().getLastSection()
            Log.d("MapViewModel", "Last session found: $lastSession")

            if (lastSession != null && lastSession.trackStartId != null && lastSession.trackEndId != null) {
                Log.d("MapViewModel", "Fetching points between ${lastSession.trackStartId} and ${lastSession.trackEndId}")
                val trackPoints = database.trackPointDao()
                    .getTrackPointsForSection(lastSession.trackStartId, lastSession.trackEndId)
                    .first() // Use .first() to get the current list and not collect forever

                Log.d("MapViewModel", "Collected ${trackPoints.size} track points.")
                val latLngs = trackPoints.map { LatLng(it.latitude, it.longitude) }
                if (latLngs.size > 1) {
                    val boundsBuilder = LatLngBounds.Builder()
                    latLngs.forEach { boundsBuilder.include(it) }
                    _initialBounds.value = boundsBuilder.build()
                    _track.value = latLngs
                } else {
                    Log.d("MapViewModel", "Not enough track points, loading fallback.")
                    loadFallbackLocation()
                }
            } else {
                Log.d("MapViewModel", "No valid last session, loading fallback.")
                loadFallbackLocation()
            }
            _isLoading.value = false
        }
    }

    private fun loadFallbackLocation() {
        viewModelScope.launch {
            try {
                val currentLocation = locationManager.requestLocationUpdates().first()
                val bounds = LatLngBounds.Builder()
                    .include(LatLng(currentLocation.latitude, currentLocation.longitude))
                    .build()
                _initialBounds.value = bounds
            } catch (e: Exception) {
                // Fallback to Tokyo Station
                val tokyo = LatLng(35.681236, 139.767125)
                _initialBounds.value = LatLngBounds.Builder().include(tokyo).build()
            }
        }
    }
}


