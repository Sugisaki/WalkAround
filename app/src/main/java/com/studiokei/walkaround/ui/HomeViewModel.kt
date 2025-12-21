package com.studiokei.walkaround.ui

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.Section
import com.studiokei.walkaround.data.model.StepSegment
import com.studiokei.walkaround.data.model.TrackPoint
import com.studiokei.walkaround.ui.StepSensorManager.SensorMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class HomeUiState(
    val isRunning: Boolean = false,
    val currentStepCount: Int = 0,
    val currentTrackPointCount: Int = 0,
    val sensorMode: SensorMode = SensorMode.UNAVAILABLE,
    val hasHealthConnectPermissions: Boolean = false,
)

class HomeViewModel(
    private val database: AppDatabase,
    private val stepSensorManager: StepSensorManager,
    private val healthConnectManager: HealthConnectManager,
    private val locationManager: LocationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentSessionId: Long? = null
    private var startTime: Long = 0L
    private var sensorJob: Job? = null
    private var locationJob: Job? = null

    init {
        viewModelScope.launch {
            database.trackPointDao().getTrackPointCount().onEach { count ->
                _uiState.value = _uiState.value.copy(currentTrackPointCount = count)
            }.launchIn(viewModelScope)

            _uiState.value = _uiState.value.copy(
                sensorMode = stepSensorManager.sensorMode,
                hasHealthConnectPermissions = healthConnectManager.hasPermissions()
            )
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        if (granted) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    hasHealthConnectPermissions = healthConnectManager.hasPermissions()
                )
            }
        }
    }

    fun startTracking() {
        if (_uiState.value.isRunning) return
        Log.d("HomeViewModel", "startTracking called")

        _uiState.value = _uiState.value.copy(isRunning = true, currentStepCount = 0)
        startTime = System.currentTimeMillis()

        // センサーからの歩数データの収集（launch）をここで行う
        launchStepMeasurement()
        // 位置情報データの収集をここで行う
        launchLocationMeasurement()

        viewModelScope.launch {
            // 新しいセクションを作成 (trackStartId は後で更新)
            val newSection = Section(
                trackStartId = null,
                distanceMeters = 0.0,
                durationSeconds = 0L,
                averageSpeedKmh = 0.0,
                createdAtTimestamp = startTime
            )
            currentSessionId = database.sectionDao().insertSection(newSection)
        }
    }

    private fun launchStepMeasurement() {
        sensorJob?.cancel()
        sensorJob = stepSensorManager.steps().onEach { steps ->
            _uiState.value = _uiState.value.copy(currentStepCount = steps)
        }.launchIn(viewModelScope)
    }

    private fun launchLocationMeasurement() {
        locationJob?.cancel()
        Log.d("HomeViewModel", "launchLocationMeasurement called")
        locationJob = locationManager.requestLocationUpdates().onEach { location ->
            Log.d("HomeViewModel", "Location collected: $location")
            val trackPoint = TrackPoint(
                time = System.currentTimeMillis(),
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                speed = location.speed,
                accuracy = location.accuracy
            )
            val insertedId = database.trackPointDao().insertTrackPoint(trackPoint)
            Log.d("HomeViewModel", "Inserted TrackPoint with id: $insertedId")

            // 最初のTrackPointであれば、SectionのtrackStartIdを更新
            currentSessionId?.let { sessionId ->
                val currentSection = database.sectionDao().getSectionById(sessionId)
                if (currentSection != null && currentSection.trackStartId == null) {
                    database.sectionDao().updateSection(
                        currentSection.copy(trackStartId = insertedId)
                    )
                    Log.d("HomeViewModel", "Updated Section $sessionId with startTrackId: $insertedId")
                }
            }
        }.launchIn(viewModelScope)
    }

    fun stopTracking() {
        Log.d("HomeViewModel", "stopTracking called")
        // センサーと位置情報のジョブをキャンセル
        sensorJob?.cancel()
        sensorJob = null
        locationJob?.cancel()
        locationJob = null

        // セッション終了処理
        viewModelScope.launch {
            val endTime = System.currentTimeMillis()
            val lastTrackPoint = database.trackPointDao().getLastTrackPoint()
            
            currentSessionId?.let { sessionId ->
                val stepSegment = StepSegment(
                    sectionId = sessionId,
                    steps = _uiState.value.currentStepCount,
                    startTime = startTime,
                    endTime = endTime
                )
                database.stepSegmentDao().insertStepSegment(stepSegment)

                val section = database.sectionDao().getSectionById(sessionId)
                section?.let {
                    val updatedSection = it.copy(
                        durationSeconds = (endTime - startTime) / 1000,
                        trackEndId = lastTrackPoint?.id
                    )
                    database.sectionDao().updateSection(updatedSection)
                }
            }
            _uiState.value = _uiState.value.copy(isRunning = false)
            currentSessionId = null
        }
    }
}

