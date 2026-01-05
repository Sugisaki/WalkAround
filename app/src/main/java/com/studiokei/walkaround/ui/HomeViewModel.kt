package com.studiokei.walkaround.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.AddressRecord
import com.studiokei.walkaround.data.model.SectionSummary
import com.studiokei.walkaround.service.TrackingService
import com.studiokei.walkaround.ui.StepSensorManager.SensorMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val currentAddress: String? = null,
    val sensorMode: SensorMode = SensorMode.UNAVAILABLE,
    val hasHealthConnectPermissions: Boolean = false,
    val sections: List<SectionSummary> = emptyList(),
    val displayUnit: String = "km"
)

class HomeViewModel(
    private val context: Context,
    private val database: AppDatabase,
    private val stepSensorManager: StepSensorManager,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val locationManager = LocationManager(context)
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

            // 設定の監視（単位など）
            database.settingsDao().getSettings().onEach { settings ->
                _uiState.value = _uiState.value.copy(displayUnit = settings?.displayUnit ?: "km")
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
                if (!running) {
                    _uiState.value = _uiState.value.copy(currentAddress = null)
                }
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

    fun fetchCurrentAddress() {
        // 先に「取得中...」と表示させて視覚的なフィードバックを返す
        _uiState.value = _uiState.value.copy(currentAddress = "取得中...")

        viewModelScope.launch {
            val settings = database.settingsDao().getSettings().first()
            val accuracyLimit = settings?.locationAccuracyLimit ?: 20.0f

            if (_uiState.value.isRunning) {
                // 走行中: DBから最後に保存された、精度の高い TrackPoint を取得
                val lastTrackPoint = database.trackPointDao().getLastAccurateTrackPoint(accuracyLimit)
                
                if (lastTrackPoint != null) {
                    // 保存されたロケール（あれば）を使用して住所を取得
                    val address = locationManager.getLocaleAddress(
                        lastTrackPoint.latitude,
                        lastTrackPoint.longitude
                    )
                    if (address != null) {
                        val tempRecord = AddressRecord(
                            address = address
                        )
                        _uiState.value = _uiState.value.copy(currentAddress = tempRecord.addressDisplay())
                    } else {
                        _uiState.value = _uiState.value.copy(currentAddress = "住所を取得できませんでした")
                    }
                } else {
                    _uiState.value = _uiState.value.copy(currentAddress = "精度の高い位置情報がまだ記録されていません")
                }
            } else {
                // 停止中: 現在の位置情報をリクエスト
                val location = locationManager.getCurrentLocation()
                // 停止中も精度をチェックするかどうかは要件次第だが、一貫性のためにチェックする
                if (location != null && location.accuracy <= accuracyLimit) {
                    // 停止中にボタンが押されたとき、ロケールを更新してから住所を取得
                    locationManager.updateCachedLocale(location.latitude, location.longitude)
                    val address = locationManager.getLocaleAddress(
                        location.latitude,
                        location.longitude
                    )
                    if (address != null) {
                        val tempRecord = AddressRecord(
                            address = address
                        )
                        _uiState.value = _uiState.value.copy(currentAddress = tempRecord.addressDisplay())
                    } else {
                        _uiState.value = _uiState.value.copy(currentAddress = "住所を取得できませんでした")
                    }
                    
                    // 【修正】住所をデータベースには保存しない（表示のみ）
                    /*
                    locationManager.saveAddressRecord(
                        lat = location.latitude,
                        lng = location.longitude
                    )
                    */
                } else if (location != null) {
                    _uiState.value = _uiState.value.copy(currentAddress = "位置情報の精度が不十分です (${location.accuracy}m)")
                } else {
                    _uiState.value = _uiState.value.copy(currentAddress = "現在地を取得できませんでした")
                }
            }
        }
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
