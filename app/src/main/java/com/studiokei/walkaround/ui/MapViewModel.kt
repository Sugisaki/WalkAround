package com.studiokei.walkaround.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.Section
import com.studiokei.walkaround.util.applyMedianFilter
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

    fun loadSection(sectionId: Long?) {
        viewModelScope.launch {
            _isLoading.value = true
            Log.d("MapViewModel", "Loading section: $sectionId")
            
            val section = if (sectionId != null) {
                database.sectionDao().getSectionById(sectionId)
            } else {
                database.sectionDao().getLastSection()
            }
            
            Log.d("MapViewModel", "Section found: $section")

            if (section != null && section.trackStartId != null && section.trackEndId != null) {
                // 設定から許容精度と窓サイズを取得
                val settings = database.settingsDao().getSettings().first()
                val accuracyLimit = settings?.locationAccuracyLimit ?: 20.0f
                val windowSize = settings?.medianWindowSize ?: 7

                // データベースから精度でフィルタリングされた地点を直接取得
                val accurateTrackPoints = database.trackPointDao()
                    .getAccurateTrackPointsForSection(
                        section.trackStartId,
                        section.trackEndId,
                        accuracyLimit
                    )
                    .first()

                Log.d("MapViewModel", "Track stats for Section ${section.sectionId}: Accurate Size =${accurateTrackPoints.size} (Limit=${accuracyLimit})")

                // 最初と最後の地点の住所が保存されているか個別に確認し、なければ保存
                if (accurateTrackPoints.isNotEmpty()) {
                    val firstPoint = accurateTrackPoints.first()
                    val firstAddress = database.addressDao().getAddressBySectionAndTrack(section.sectionId, firstPoint.id)
                    if (firstAddress == null) {
                        Log.d("MapViewModel", "No start address record found for section ${section.sectionId}. Fetching...")
                        locationManager.saveAddressRecord(
                            lat = firstPoint.latitude,
                            lng = firstPoint.longitude,
                            sectionId = section.sectionId,
                            trackId = firstPoint.id
                        )
                    }

                    if (accurateTrackPoints.size > 1) {
                        val lastPoint = accurateTrackPoints.last()
                        val lastAddress = database.addressDao().getAddressBySectionAndTrack(section.sectionId, lastPoint.id)
                        if (lastAddress == null) {
                            Log.d("MapViewModel", "No end address record found for section ${section.sectionId}. Fetching...")
                            locationManager.saveAddressRecord(
                                lat = lastPoint.latitude,
                                lng = lastPoint.longitude,
                                sectionId = section.sectionId,
                                trackId = lastPoint.id
                            )
                        }
                    }
                }

                val rawLatLngs = accurateTrackPoints.map { LatLng(it.latitude, it.longitude) }
                if (rawLatLngs.size > 1) {
                    // 窓サイズが0より大きい場合のみフィルタを適用
                    val filteredLatLngs = if (windowSize > 0) {
                        applyMedianFilter(rawLatLngs, windowSize = windowSize)
                    } else {
                        rawLatLngs
                    }
                    
                    val boundsBuilder = LatLngBounds.Builder()
                    filteredLatLngs.forEach { boundsBuilder.include(it) }
                    _initialBounds.value = boundsBuilder.build()
                    _track.value = filteredLatLngs
                } else {
                    loadFallbackLocation()
                }
            } else {
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
                _track.value = emptyList()
            } catch (e: Exception) {
                val tokyo = LatLng(35.681236, 139.767125)
                _initialBounds.value = LatLngBounds.Builder().include(tokyo).build()
                _track.value = emptyList()
            }
        }
    }
}
