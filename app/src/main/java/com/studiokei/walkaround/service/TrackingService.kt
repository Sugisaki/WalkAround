package com.studiokei.walkaround.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.studiokei.walkaround.R
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.Section
import com.studiokei.walkaround.data.model.StepSegment
import com.studiokei.walkaround.data.model.TrackPoint
import com.studiokei.walkaround.ui.HealthConnectManager
import com.studiokei.walkaround.ui.LocationManager
import com.studiokei.walkaround.ui.StepSensorManager
import com.studiokei.walkaround.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class TrackingService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private lateinit var database: AppDatabase
    private lateinit var stepSensorManager: StepSensorManager
    private lateinit var locationManager: LocationManager

    private var currentSessionId: Long? = null
    private var startTime: Long = 0L
    private var sensorJob: Job? = null
    private var locationJob: Job? = null
    private var trackPointCounter = 0
    private var isStartAddressSaved = false
    
    // 住所の動的保存用
    private var lastThoroughfareKey: String? = null
    private var lastAddressProcessTime: Long = 0L
    private var accuracyLimit: Float = 20.0f

    private val _currentSteps = MutableStateFlow(0)
    val currentSteps: StateFlow<Int> = _currentSteps.asStateFlow()

    private val _currentTrackCount = MutableStateFlow(0)
    val currentTrackCount: StateFlow<Int> = _currentTrackCount.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): TrackingService = this@TrackingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        val healthConnectManager = HealthConnectManager(this)
        stepSensorManager = StepSensorManager(this, healthConnectManager)
        locationManager = LocationManager(this)

        createNotificationChannel()

        // 設定から精度制限を取得して監視
        serviceScope.launch {
            database.settingsDao().getSettings().collect { settings ->
                accuracyLimit = settings?.locationAccuracyLimit ?: 20.0f
                Log.d("TrackingService", "Accuracy limit updated to: $accuracyLimit")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (_isRunning.value) return

        _isRunning.value = true
        _currentSteps.value = 0
        _currentTrackCount.value = 0
        trackPointCounter = 0
        isStartAddressSaved = false
        lastThoroughfareKey = null
        lastAddressProcessTime = 0L
        startTime = System.currentTimeMillis()

        // 権限の状態を確認して、フォアグラウンドサービスのタイプを決定する
        val hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        val hasActivityPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        var serviceType = 0
        if (hasLocationPermission) {
            serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (hasActivityPermission) {
            serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        }

        // API 29以降では、タイプを指定して開始する
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (serviceType != 0) {
                startForeground(NOTIFICATION_ID, createNotification("トラッキング中..."), serviceType)
            } else {
                startForeground(NOTIFICATION_ID, createNotification("トラッキング中..."))
            }
        } else {
            startForeground(NOTIFICATION_ID, createNotification("トラッキング中..."))
        }

        launchStepMeasurement()
        launchLocationMeasurement()

        serviceScope.launch {
            val newSection = Section(
                trackStartId = null,
                createdAtTimestamp = startTime
            )
            currentSessionId = database.sectionDao().insertSection(newSection)
        }
    }

    private fun launchStepMeasurement() {
        sensorJob?.cancel()
        sensorJob = stepSensorManager.steps().onEach { steps ->
            _currentSteps.value = steps
            updateNotification("歩数: $steps, 位置情報: ${trackPointCounter}件")
        }.launchIn(serviceScope)
    }

    private fun launchLocationMeasurement() {
        locationJob?.cancel()
        locationJob = locationManager.requestLocationUpdates().onEach { location ->
            val currentTime = System.currentTimeMillis()
            val trackPoint = TrackPoint(
                time = currentTime,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                speed = location.speed,
                accuracy = location.accuracy
            )
            val insertedId = database.trackPointDao().insertTrackPoint(trackPoint)
            val sessionId = currentSessionId

            // 住所保存のロジック (設定された精度制限を使用)
            if (location.accuracy <= accuracyLimit) {
                if (!isStartAddressSaved) {
                    // 最初の住所保存
                    isStartAddressSaved = true
                    lastAddressProcessTime = currentTime
                    serviceScope.launch {
                        locationManager.updateCachedLocale(location.latitude, location.longitude)
                        val address = locationManager.getLocaleAddress(location.latitude, location.longitude)
                        lastThoroughfareKey = locationManager.getAddressKey(address)
                        locationManager.saveAddressRecord(
                            lat = location.latitude,
                            lng = location.longitude,
                            sectionId = sessionId,
                            trackId = insertedId,
                            timestamp = currentTime
                        )
                    }
                } else if (currentTime - lastAddressProcessTime >= Constants.ADDRESS_PROCESS_INTERVAL_MS) {
                    // 一定時間経過後の丁目変化チェック (共通メソッドを使用)
                    serviceScope.launch {
                        val newKey = locationManager.saveAddressIfThoroughfareChanged(
                            lat = location.latitude,
                            lng = location.longitude,
                            sectionId = sessionId,
                            trackId = insertedId,
                            timestamp = currentTime,
                            lastAddressKey = lastThoroughfareKey
                        )
                        if (newKey != null) {
                            lastThoroughfareKey = newKey
                        }
                        // 住所取得を試みた時間を更新（頻度抑制）
                        lastAddressProcessTime = System.currentTimeMillis()
                    }
                }
            }
            
            trackPointCounter++
            _currentTrackCount.value = trackPointCounter
            updateNotification("歩数: ${_currentSteps.value}, 位置情報: ${trackPointCounter}件")

            sessionId?.let { id ->
                val currentSection = database.sectionDao().getSectionById(id)
                if (currentSection != null && currentSection.trackStartId == null) {
                    database.sectionDao().updateSection(
                        currentSection.copy(trackStartId = insertedId)
                    )
                }
            }
        }.launchIn(serviceScope)
    }

    private fun stopTracking() {
        if (!_isRunning.value) return
        
        sensorJob?.cancel()
        locationJob?.cancel()
        _isRunning.value = false

        val sessionId = currentSessionId
        val steps = _currentSteps.value
        val startT = startTime

        serviceScope.launch {
            val endTime = System.currentTimeMillis()
            
            // 最新の精度制限を取得
            val currentSettings = database.settingsDao().getSettings().first()
            val limit = currentSettings?.locationAccuracyLimit ?: 20.0f

            // 精度が一定以上の最後のTrackPointを取得して住所を保存する
            val lastAccuratePoint = database.trackPointDao().getLastAccurateTrackPoint(limit)
            val lastTrackPoint = database.trackPointDao().getLastTrackPoint()
            
            if (sessionId != null) {
                // 停止時の住所を保存（精度の高い地点があればそれを使う）
                if (lastAccuratePoint != null) {
                    locationManager.saveAddressRecord(
                        lat = lastAccuratePoint.latitude,
                        lng = lastAccuratePoint.longitude,
                        sectionId = sessionId,
                        trackId = lastAccuratePoint.id,
                        timestamp = lastAccuratePoint.time
                    )
                }

                val stepSegment = StepSegment(
                    sectionId = sessionId,
                    steps = steps,
                    startTime = startT,
                    endTime = endTime
                )
                database.stepSegmentDao().insertStepSegment(stepSegment)

                val section = database.sectionDao().getSectionById(sessionId)
                section?.let {
                    val updatedSection = it.copy(
                        durationSeconds = (endTime - startT) / 1000,
                        trackEndId = lastTrackPoint?.id
                    )
                    database.sectionDao().updateSection(updatedSection)
                }
            }
            currentSessionId = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tracking Service Channel",
                NotificationManager.IMPORTANCE_LOW // 音やバイブレーションを抑制
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WalkAround")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW) // 優先度を低く設定
            .setSilent(true) // 明示的にサイレント設定
            .build()
    }

    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "tracking_channel"
    }
}
