package com.studiokei.walkaround.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.SectionSummary
import com.studiokei.walkaround.service.TrackingService
import com.studiokei.walkaround.ui.StepSensorManager.SensorMode
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
    val todayStepCount: Int = 0,
    val sensorMode: SensorMode = SensorMode.UNAVAILABLE,
    val hasHealthConnectPermissions: Boolean = false,
    val sections: List<SectionSummary> = emptyList()
)

class HomeViewModel(
    private val context: Context,
    private val database: AppDatabase,
    private val stepSensorManager: StepSensorManager,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var trackingService: TrackingService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TrackingService.LocalBinder
            trackingService = binder.getService()
            isBound = true
            observeService()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            trackingService = null
        }
    }

    init {
        // サービスのバインド
        Intent(context, TrackingService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

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

    private fun observeService() {
        trackingService?.let { service ->
            service.isRunning.onEach { running ->
                _uiState.value = _uiState.value.copy(isRunning = running)
            }.launchIn(viewModelScope)

            service.currentSteps.onEach { steps ->
                _uiState.value = _uiState.value.copy(currentStepCount = steps)
            }.launchIn(viewModelScope)

            service.currentTrackCount.onEach { count ->
                _uiState.value = _uiState.value.copy(currentTrackPointCount = count)
            }.launchIn(viewModelScope)
        }
    }
    fun startTracking() {
        // 位置情報のトラッキングを開始
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    fun stopTracking() {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun onPermissionsResult(granted: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                hasHealthConnectPermissions = healthConnectManager.hasPermissions()
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(connection)
            isBound = false
        }
    }
}
