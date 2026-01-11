package com.studiokei.walkaround.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studiokei.walkaround.R
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.AddressRecord
import com.studiokei.walkaround.data.model.SectionSummary
import com.studiokei.walkaround.service.TrackingService
import com.studiokei.walkaround.ui.StepSensorManager.SensorMode
import com.studiokei.walkaround.util.TextToSpeechHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
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
    val todayHealthConnectSteps: Long? = null, // ヘルスコネクトから取得した本日の歩数
    val isHealthConnectAvailable: Boolean = false, // ヘルスコネクトが利用可能か
    val currentAddress: String? = null,
    val currentFeatureName: String? = null,
    val showAddressDialog: Boolean = false,
    val sensorMode: SensorMode = SensorMode.UNAVAILABLE,
    val hasHealthConnectPermissions: Boolean = false,
    val sections: List<SectionSummary> = emptyList(),
    val displayUnit: String = "km",
    val isVoiceEnabled: Boolean = true, // 音声設定
    val showDeleteConfirmDialog: Boolean = false, // 削除確認ダイアログの表示状態
    val showDeleteDoneDialog: Boolean = false, // 削除完了ダイアログの表示状態
    val sectionToDeleteId: Long? = null, // 削除対象のセクションID
    val showGpsLostDialog: Boolean = false // GPSがオフになり停止した際のダイアログ表示
)

/**
 * ホーム画面の状態を管理するViewModel。
 */
class HomeViewModel(
    private val context: Context,
    private val database: AppDatabase,
    private val stepSensorManager: StepSensorManager,
    private val healthConnectManager: HealthConnectManager
) : ViewModel(), TextToSpeech.OnInitListener {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val locationManager = LocationManager(context)
    private var trackingService: TrackingService? = null
    private var isBound = false

    private val ttsHelper = TextToSpeechHelper(context)

    // GPSの状態を監視するBroadcastReceiver
    private val gpsStatusReceiver = GpsStatusReceiver { onGpsDisabled() }

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
            val startOfDayMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            database.sectionDao().getTodayTotalSteps(startOfDayMillis).onEach { count ->
                _uiState.update { it.copy(todayStepCount = count ?: 0) }
            }.launchIn(viewModelScope)

            // 設定の監視
            database.settingsDao().getSettings().onEach { settings ->
                _uiState.update { it.copy(
                    displayUnit = settings?.displayUnit ?: "km",
                    isVoiceEnabled = settings?.isVoiceEnabled ?: true
                ) }
            }.launchIn(viewModelScope)

            // センサーモード等の初期確認
            val isHCEnabled = healthConnectManager.isAvailable
            val hasPermissions = if (isHCEnabled) healthConnectManager.hasPermissions() else false
            _uiState.update { 
                it.copy(
                    sensorMode = stepSensorManager.sensorMode,
                    hasHealthConnectPermissions = hasPermissions,
                    isHealthConnectAvailable = isHCEnabled
                )
            }
            
            if (hasPermissions) {
                fetchHealthConnectSteps()
            }
        }
    }

    override fun onInit(status: Int) {
        // TextToSpeechHelperが内部で管理するため、ここでは特に行わない
    }

    private fun speakText(text: String) {
        // 設定が有効な場合のみ読み上げる
        if (_uiState.value.isVoiceEnabled) {
            ttsHelper.speak(text)
        }
    }

    private fun observeService() {
        trackingService?.let { service ->
            service.isRunning.onEach { running ->
                _uiState.update { it.copy(isRunning = running) }
                if (!running) {
                    _uiState.update { it.copy(currentAddress = null, currentFeatureName = null) }
                    // 計測終了時にヘルスコネクトの歩数を再取得
                    fetchHealthConnectSteps()
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

    /**
     * ヘルスコネクトから本日の歩数を取得してUI状態を更新します。
     */
    private fun fetchHealthConnectSteps() {
        viewModelScope.launch {
            if (!healthConnectManager.isAvailable || !healthConnectManager.hasPermissions()) {
                _uiState.update { it.copy(todayHealthConnectSteps = null) }
                return@launch
            }
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val now = Instant.now()
            try {
                val steps = healthConnectManager.readSteps(startOfDay, now)
                _uiState.update { it.copy(todayHealthConnectSteps = steps) }
            } catch (e: Exception) {
                _uiState.update { it.copy(todayHealthConnectSteps = null) }
            }
        }
    }

    fun startTracking() {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
        }
        context.startForegroundService(intent)
        // GPS監視を開始
        context.registerReceiver(gpsStatusReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
    }

    fun stopTracking() {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        context.startService(intent)
        // GPS監視を停止
        try {
            context.unregisterReceiver(gpsStatusReceiver)
        } catch (e: IllegalArgumentException) {
            // レシーバーがすでに登録解除されている場合に発生するが、無視してよい
        }
    }

    /**
     * GpsStatusReceiverから呼び出され、GPSが無効になった際の処理を行う。
     */
    private fun onGpsDisabled() {
        // トラッキングが実行中でない場合は何もしない
        if (!_uiState.value.isRunning) return

        // トラッキングを停止
        stopTracking()

        // UIに通知するためのダイアログ表示フラグを立てる
        _uiState.update { it.copy(showGpsLostDialog = true) }

        // ユーザーに状況を知らせるためのシステム通知を発行
        showTrackingStoppedNotification()
    }

    /**
     * 現在地の住所を取得してダイアログを表示し、音声で読み上げます。
     * GPSがオフになったらトラッキングが停止したことをユーザーに通知。
     */
    private fun showTrackingStoppedNotification() {
        val channelId = "gps_lost_notification_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8.0以上では通知チャネルの登録が必要
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "GPSステータス通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "GPSがオフになった際のトラッキング停止を通知します"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification) // 通知アイコン（要追加）
            .setContentTitle("記録を停止しました")
            .setContentText("GPSがオフになったため、記録を自動的に停止しました。")
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // 一意のIDで通知を表示
        notificationManager.notify(GPS_LOST_NOTIFICATION_ID, builder.build())
    }

    /**
     * GPS喪失ダイアログを閉じる。
     */
    fun dismissGpsLostDialog() {
        _uiState.update { it.copy(showGpsLostDialog = false) }
    }

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
                    updateAddressStateAndSpeak(address)
                } else {
                    val msg = "精度の高い位置情報がまだ記録されていません"
                    _uiState.update { it.copy(currentAddress = msg) }
                    speakText(msg)
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
                    updateAddressStateAndSpeak(address)
                } else if (location != null) {
                    val msg = "位置情報の精度が不十分です"
                    _uiState.update { it.copy(currentAddress = msg) }
                    speakText(msg)
                } else {
                    val msg = "現在地を取得できませんでした"
                    _uiState.update { it.copy(currentAddress = msg) }
                    speakText(msg)
                }
            }
        }
    }

    /**
     * 取得したAddressオブジェクトからUI状態を更新し、ヘルパー経由で読み上げを実行します。
     */
    private fun updateAddressStateAndSpeak(address: android.location.Address?) {
        if (address != null) {
            val tempRecord = AddressRecord(address = address)
            val baseAddress = tempRecord.addressDisplay()
            val featureName = tempRecord.featureNameDisplay()

            _uiState.update { it.copy(
                currentAddress = baseAddress,
                currentFeatureName = featureName
            ) }

            // 読み上げ用：市区町村以下の簡潔な住所 + 名称 を使用
            val speakContent = tempRecord.cityDisplayWithFeature() ?: ""
            speakText(speakContent)
        } else {
            val msg = "住所を取得できませんでした"
            _uiState.update { it.copy(currentAddress = msg) }
            speakText(msg)
        }
    }

    fun dismissAddressDialog() {
        _uiState.update { it.copy(showAddressDialog = false) }
        ttsHelper.stop()
    }

    fun onPermissionsResult(granted: Boolean) {
        viewModelScope.launch {
            val hasPermissions = healthConnectManager.hasPermissions()
            _uiState.update { it.copy(hasHealthConnectPermissions = hasPermissions) }
            if (hasPermissions) {
                fetchHealthConnectSteps()
            }
        }
    }

    /**
     * 指定されたセクションの削除を要求し、確認ダイアログを表示します。
     * @param sectionId 削除するセクションのID。
     */
    fun requestDeletion(sectionId: Long) {
        _uiState.update {
            it.copy(
                showDeleteConfirmDialog = true,
                sectionToDeleteId = sectionId
            )
        }
    }

    /**
     * セクションの削除を確定し、関連データを削除した後、完了ダイアログを表示します。
     */
    fun confirmDeletion() {
        val sectionId = _uiState.value.sectionToDeleteId ?: return

        viewModelScope.launch {
            // 1. 削除対象のセクション情報を取得
            val section = database.sectionDao().getSectionById(sectionId)

            if (section != null) {
                // 2. 関連するTrackPointの範囲を取得し、削除する
                val startId = section.trackStartId
                val endId = section.trackEndId
                if (startId != null && endId != null) {
                    database.trackPointDao().deleteByIdRange(startId, endId)
                }

                // 3. セクションを削除する (ON DELETE CASCADEにより、関連データも削除される)
                database.sectionDao().deleteSection(section)
            }

            // 4. UIの状態を更新して完了ダイアログを表示
            _uiState.update {
                it.copy(
                    showDeleteConfirmDialog = false,
                    showDeleteDoneDialog = true,
                    sectionToDeleteId = null
                )
            }
        }
    }

    /**
     * セクションの削除をキャンセルし、確認ダイアログを閉じます。
     */
    fun cancelDeletion() {
        _uiState.update {
            it.copy(
                showDeleteConfirmDialog = false,
                sectionToDeleteId = null
            )
        }
    }

    /**
     * 削除完了ダイアログを閉じます。
     */
    fun dismissDeleteDoneDialog() {
        _uiState.update {
            it.copy(showDeleteDoneDialog = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(connection)
            isBound = false
        }
        // ViewModelが破棄される際に、登録したレシーバーを解除
        try {
            context.unregisterReceiver(gpsStatusReceiver)
        } catch (e: IllegalArgumentException) {
            // 無視
        }
        ttsHelper.shutdown()
    }

    companion object {
        private const val GPS_LOST_NOTIFICATION_ID = 2
    }
}

/**
 * GPSプロバイダーの状態変化を受け取るBroadcastReceiver。
 * トラッキング中にGPSが無効になったことを検知するために使用します。
 */
private class GpsStatusReceiver(private val onGpsDisabled: () -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // GPSプロバイダーの状態変化アクションでなければ何もしない
        if (intent?.action != LocationManager.PROVIDERS_CHANGED_ACTION) {
            return
        }

        val locationManager = context?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        // LocationManagerが取得でき、かつGPSが無効になっている場合
        if (locationManager != null && !isGpsEnabled(locationManager)) {
            // コールバックを呼び出してGPSが無効になったことを通知
            onGpsDisabled()
        }
    }

    /**
     * GPSまたはネットワーク位置情報が有効かどうかを判定します。
     */
    private fun isGpsEnabled(locationManager: LocationManager): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}
