package com.studiokei.walkaround.ui

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
    val sensorMode: SensorMode = SensorMode.UNAVAILABLE,
    val hasHealthConnectPermissions: Boolean = false,
)

class HomeViewModel(
    private val database: AppDatabase,
    private val stepSensorManager: StepSensorManager,
    private val healthConnectManager: HealthConnectManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentSessionId: Long? = null
    private var startTime: Long = 0L
    private var sensorJob: Job? = null

    init {
        viewModelScope.launch {
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

        _uiState.value = _uiState.value.copy(isRunning = true, currentStepCount = 0)
        startTime = System.currentTimeMillis()

        // センサーからの歩数データの収集（launch）をここで行う
        launchStepMeasurement()

        viewModelScope.launch {
            // スタート地点のダミーTrackPointを作成
            val startTrackPointId = database.trackPointDao().insertTrackPoint(
                TrackPoint(time = startTime, latitude = 0.0, longitude = 0.0, altitude = 0.0, speed = 0f, accuracy = 0f)
            )

            // 新しいセクションを作成
            val newSection = Section(
                trackStartId = startTrackPointId,
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

    fun stopTracking() {
        sensorJob?.cancel()
        sensorJob = null

        viewModelScope.launch {
            val endTime = System.currentTimeMillis()
            val endTrackPointId = database.trackPointDao().insertTrackPoint(
                TrackPoint(time = endTime, latitude = 0.0, longitude = 0.0, altitude = 0.0, speed = 0f, accuracy = 0f)
            )
            
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
                        trackEndId = endTrackPointId
                    )
                    database.sectionDao().updateSection(updatedSection)
                }
            }
            _uiState.value = _uiState.value.copy(isRunning = false)
            currentSessionId = null
        }
    }
}
