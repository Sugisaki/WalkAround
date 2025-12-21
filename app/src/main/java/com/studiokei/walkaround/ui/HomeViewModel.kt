package com.studiokei.walkaround.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.Section
import com.studiokei.walkaround.data.model.SectionSummary
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
import java.time.LocalDate
import java.time.ZoneId

data class HomeUiState(
    val isRunning: Boolean = false,
    val currentStepCount: Int = 0,
    val currentTrackPointCount: Int = 0,
    val todayStepCount: Int = 0, // 本日の歩数
    val sensorMode: SensorMode = SensorMode.UNAVAILABLE,
    val hasHealthConnectPermissions: Boolean = false,
    val sections: List<SectionSummary> = emptyList()
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
    private var trackPointCounter = 0

    init {
        viewModelScope.launch {
            // セクション一覧の監視
            database.sectionDao().getSectionSummaries().onEach { summaries ->
                _uiState.value = _uiState.value.copy(sections = summaries)
            }.launchIn(viewModelScope)

            // 本日の歩数の監視
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            database.sectionDao().getTodayTotalSteps(startOfDay).onEach { count ->
                _uiState.value = _uiState.value.copy(todayStepCount = count ?: 0)
            }.launchIn(viewModelScope)

            _uiState.value = _uiState.value.copy(
                sensorMode = stepSensorManager.sensorMode,
                hasHealthConnectPermissions = healthConnectManager.hasPermissions()
            )
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                hasHealthConnectPermissions = healthConnectManager.hasPermissions()
            )
        }
    }

    fun startTracking() {
        if (_uiState.value.isRunning) return
        
        trackPointCounter = 0
        _uiState.value = _uiState.value.copy(
            isRunning = true, 
            currentStepCount = 0,
            currentTrackPointCount = 0
        )
        startTime = System.currentTimeMillis()

        launchStepMeasurement()
        launchLocationMeasurement()

        viewModelScope.launch {
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
        locationJob = locationManager.requestLocationUpdates().onEach { location ->
            val trackPoint = TrackPoint(
                time = System.currentTimeMillis(),
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                speed = location.speed,
                accuracy = location.accuracy
            )
            val insertedId = database.trackPointDao().insertTrackPoint(trackPoint)
            
            trackPointCounter++
            _uiState.value = _uiState.value.copy(currentTrackPointCount = trackPointCounter)

            currentSessionId?.let { sessionId ->
                val currentSection = database.sectionDao().getSectionById(sessionId)
                if (currentSection != null && currentSection.trackStartId == null) {
                    database.sectionDao().updateSection(
                        currentSection.copy(trackStartId = insertedId)
                    )
                }
            }
        }.launchIn(viewModelScope)
    }

    fun stopTracking() {
        sensorJob?.cancel()
        sensorJob = null
        locationJob?.cancel()
        locationJob = null

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
