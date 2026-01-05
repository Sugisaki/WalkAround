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

/**
 * 地図画面の状態を管理するViewModel。
 * SectionServiceを使用してデータの加工や距離の計算を行います。
 */
class MapViewModel(
    private val database: AppDatabase,
    private val locationManager: LocationManager,
    private val sectionService: SectionService
) : ViewModel() {

    private val _track = MutableStateFlow<List<LatLng>>(emptyList())
    val track: StateFlow<List<LatLng>> = _track.asStateFlow()

    private val _initialBounds = MutableStateFlow<LatLngBounds?>(null)
    val initialBounds: StateFlow<LatLngBounds?> = _initialBounds.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * 指定されたセクションのデータをロードし、軌跡を表示可能な状態にします。
     * 
     * @param sectionId ロード対象のセクションID。nullの場合は最新のセクションを表示します。
     */
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

            if (section != null) {
                // SectionService に住所補完、距離計算、平滑化を委譲
                val preparedTrack = sectionService.prepareSectionAndGetTrack(section)
                
                if (preparedTrack.size > 1) {
                    val boundsBuilder = LatLngBounds.Builder()
                    preparedTrack.forEach { boundsBuilder.include(it) }
                    _initialBounds.value = boundsBuilder.build()
                    _track.value = preparedTrack
                } else {
                    loadFallbackLocation()
                }
            } else {
                loadFallbackLocation()
            }
            _isLoading.value = false
        }
    }

    /**
     * 軌跡データがない場合に、現在地またはデフォルト位置を表示します。
     */
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
