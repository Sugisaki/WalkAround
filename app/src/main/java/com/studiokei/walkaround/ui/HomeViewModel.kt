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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * ホーム画面のUI状態を保持するデータクラス。
 */
data class HomeUiState(
    val isRunning: Boolean = false,
    val currentStepCount: Int = 0,
    val currentTrackPointCount: Int = 0,
    val todayStepCount: Int = 0,
    val currentAddress: String? = null,
    val currentFeatureName: String? = null, // 地点名称（建物名など）を個別に保持
    val showAddressDialog: Boolean = false,
    val sensorMode: SensorMode = SensorMode.UNAVAILABLE,
    val hasHealthConnectPermissions: Boolean = false,
    val sections: List<SectionSummary> = emptyList(),
    val displayUnit: String = "km"
)

/**
 * ホーム画面の状態を管理するViewModel。
 */
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
                _uiState.update { it.copy(sections = summaries) }
            }.launchIn(viewModelScope)

            // 本日の歩数の監視
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            database.sectionDao().getTodayTotalSteps(startOfDay).onEach { count ->
                _uiState.update { it.copy(todayStepCount = count ?: 0) }
            }.launchIn(viewModelScope)

            // 設定の監視（単位など）
            database.settingsDao().getSettings().onEach { settings ->
                _uiState.update { it.copy(displayUnit = settings?.displayUnit ?: "km") }
            }.launchIn(viewModelScope)

            // センサーモードとヘルスコネクト権限の初期確認
            val hasPermissions = healthConnectManager.hasPermissions()
            _uiState.update { 
                it.copy(
                    sensorMode = stepSensorManager.sensorMode,
                    hasHealthConnectPermissions = hasPermissions
                )
            }
        }
    }

    private fun observeService() {
        trackingService?.let { service ->
            service.isRunning.onEach { running ->
                _uiState.update { it.copy(isRunning = running) }
                if (!running) {
                    _uiState.update { it.copy(currentAddress = null, currentFeatureName = null) }
                }
            }.launchIn(viewModelScope)

            service.currentSteps.onEach { steps ->
                _uiState.update { it.copy(currentStepCount = steps) }
            }.launchIn(viewModelScope)

            service.currentTrackCount.onEach { count ->
                _uiState.update { it.copy(currentTrackPointCount = count) }
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

    /**
     * 現在地の住所を取得して表示用に更新し、ダイアログを表示します。
     * 住所と地点名称を個別に取得してUI状態にセットします。
     */
    fun fetchCurrentAddress() {
        _uiState.update { it.copy(
            currentAddress = "取得中...", 
            currentFeatureName = null,
            showAddressDialog = true 
        ) }

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
                    updateAddressState(address)
                } else {
                    _uiState.update { it.copy(currentAddress = "精度の高い位置情報がまだ記録されていません") }
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
                    updateAddressState(address)
                } else if (location != null) {
                    _uiState.update { it.copy(currentAddress = "位置情報の精度が不十分です (${location.accuracy}m)") }
                } else {
                    _uiState.update { it.copy(currentAddress = "現在地を取得できませんでした") }
                }
            }
        }
    }

    /**
     * 取得したAddressオブジェクトからUI状態を更新します。
     */
    private fun updateAddressState(address: android.location.Address?) {
        if (address != null) {
            val tempRecord = AddressRecord(address = address)
            val baseAddress = tempRecord.addressDisplay()
            
            // 名称（name）が住所本体の末尾と一致する場合は、個別表示の名称を null にする
            val featureName = if (tempRecord.name != null && baseAddress != null && !baseAddress.endsWith(tempRecord.name!!)) {
                tempRecord.name
            } else {
                null
            }

            _uiState.update { it.copy(
                currentAddress = baseAddress,
                currentFeatureName = featureName
            ) }
        } else {
            _uiState.update { it.copy(currentAddress = "住所を取得できませんでした") }
        }
    }

    fun dismissAddressDialog() {
        _uiState.update { it.copy(showAddressDialog = false) }
    }

    fun onPermissionsResult(granted: Boolean) {
        viewModelScope.launch {
            val hasPermissions = healthConnectManager.hasPermissions()
            _uiState.update { it.copy(hasHealthConnectPermissions = hasPermissions) }
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
