package com.studiokei.walkaround.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.Section
import com.studiokei.walkaround.data.model.StepSegment
import com.studiokei.walkaround.data.model.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isRunning: Boolean = false,
    val currentStepCount: Int = 0,
)

class HomeViewModel(private val database: AppDatabase) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentSessionId: Long? = null
    private var startTime: Long = 0L

    fun startTracking() {
        viewModelScope.launch {
            // スタート地点
            val startTrackPointId = database.trackPointDao().insertTrackPoint(
                TrackPoint(time = System.currentTimeMillis(), latitude = 0.0, longitude = 0.0, altitude = 0.0, speed = 0f, accuracy = 0f)
            )

            // Create a new Section and get its ID
            val newSection = Section(
                trackStartId = startTrackPointId,
                //trackEndId = endTrackPointId,
                distanceMeters = 0.0,
                durationSeconds = 0L,
                averageSpeedKmh = 0.0,
                createdAtTimestamp = System.currentTimeMillis()
            )
            currentSessionId = database.sectionDao().insertSection(newSection)
            startTime = System.currentTimeMillis()

            _uiState.value = HomeUiState(isRunning = true, currentStepCount = 0)
        }
    }

    fun stopTracking() {
        viewModelScope.launch {
            // ストップ地点
            val endTrackPointId = database.trackPointDao().insertTrackPoint(
                TrackPoint(time = System.currentTimeMillis(), latitude = 0.0, longitude = 0.0, altitude = 0.0, speed = 0f, accuracy = 0f)
            )
            currentSessionId?.let { sessionId ->
                // Create a StepSegment with the total steps
                val stepSegment = StepSegment(
                    sectionId = sessionId,
                    steps = _uiState.value.currentStepCount,
                    startTime = startTime,
                    endTime = System.currentTimeMillis()
                )
                database.stepSegmentDao().insertStepSegment(stepSegment)

                // Update the Section with final data
                val section = database.sectionDao().getSectionById(sessionId)
                section?.let {
                    val updatedSection = it.copy(
                        durationSeconds = (System.currentTimeMillis() - startTime) / 1000,
                        trackEndId = endTrackPointId,

                        // In a real scenario, you'd update distance, speed, etc.
                    )
                    database.sectionDao().updateSection(updatedSection)
                }
            }
            _uiState.value = _uiState.value.copy(isRunning = false)
            currentSessionId = null
        }
    }

    fun incrementStepCount() {
        if (_uiState.value.isRunning) {
            _uiState.value = _uiState.value.copy(
                currentStepCount = _uiState.value.currentStepCount + 1
            )
        }
    }
}
