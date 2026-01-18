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
 * SectionProcessorを使用してデータの加工や距離の計算を行います。
 */
class MapViewModel(
    private val database: AppDatabase,
    private val locationManager: LocationManager,
    private val sectionProcessor: SectionProcessor
) : ViewModel() {

    companion object {
        private val TOKYO_STATION = LatLng(35.681236, 139.767125)
        val TOKYO_DEFAULT_BOUNDS: LatLngBounds = LatLngBounds.Builder()
            .include(LatLng(TOKYO_STATION.latitude + 0.01, TOKYO_STATION.longitude + 0.01))
            .include(LatLng(TOKYO_STATION.latitude - 0.01, TOKYO_STATION.longitude - 0.01))
            .build()
    }

    private val _track = MutableStateFlow<List<LatLng>>(emptyList())
    val track: StateFlow<List<LatLng>> = _track.asStateFlow()

    private val _initialBounds = MutableStateFlow(TOKYO_DEFAULT_BOUNDS)
    val initialBounds: StateFlow<LatLngBounds> = _initialBounds.asStateFlow()

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
                // SectionProcessor に住所補完、距離計算、平滑化を委譲
                val preparedTrack = sectionProcessor.prepareSectionAndGetTrack(section)

                if (preparedTrack.isNotEmpty()) {
                    _track.value = preparedTrack
                    val boundsBuilder = LatLngBounds.Builder()
                    preparedTrack.forEach { boundsBuilder.include(it) }

                    if (preparedTrack.size == 1) {
                        // 1点しかない場合、デフォルトズームのために少し範囲を広げる
                        val singlePoint = preparedTrack.first()
                        val northEast = LatLng(singlePoint.latitude + 0.005, singlePoint.longitude + 0.005)
                        val southWest = LatLng(singlePoint.latitude - 0.005, singlePoint.longitude - 0.005)
                        boundsBuilder.include(northEast).include(southWest)
                    }
                    _initialBounds.value = boundsBuilder.build()
                } else {
                    // 軌跡がない場合はフォールバック
                    _track.value = emptyList()
                    loadFallbackLocation()
                }
            } else {
                // セクションがない場合はフォールバック
                loadFallbackLocation()
            }
            _isLoading.value = false
        }
    }

    /**
     * 軌跡データがない場合に、現在地を試行し、失敗した場合はデフォルト（東京駅）のままにします。
     */
    private fun loadFallbackLocation() {
        viewModelScope.launch {
            try {
                // 現在地を試行
                val currentLocation = locationManager.requestLocationUpdates().first()
                val center = LatLng(currentLocation.latitude, currentLocation.longitude)
                val northEast = LatLng(center.latitude + 0.005, center.longitude + 0.005)
                val southWest = LatLng(center.latitude - 0.005, center.longitude - 0.005)

                _initialBounds.value = LatLngBounds.Builder()
                    .include(northEast)
                    .include(southWest)
                    .build()
            } catch (e: Exception) {
                // 現在地の取得に失敗した場合は、初期値の東京駅のままにするので何もしない
                Log.w("MapViewModel", "Failed to get current location, using default Tokyo bounds.", e)
            }
            _track.value = emptyList()
        }
    }
}
