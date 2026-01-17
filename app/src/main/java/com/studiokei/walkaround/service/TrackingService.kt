package com.studiokei.walkaround.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.studiokei.walkaround.R
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.AddressRecord
import com.studiokei.walkaround.data.model.Section
import com.studiokei.walkaround.data.model.StepSegment
import com.studiokei.walkaround.data.model.TrackPoint
import com.studiokei.walkaround.ui.LocationManager
import com.studiokei.walkaround.ui.StepSensorManager
import com.studiokei.walkaround.util.Constants
import com.studiokei.walkaround.util.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 位置情報と歩数をバックグラウンドで計測し続けるフォアグラウンドサービス。
 */
class TrackingService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private lateinit var database: AppDatabase
    private lateinit var stepSensorManager: StepSensorManager
    private lateinit var locationManager: LocationManager
    private lateinit var ttsHelper: TextToSpeechHelper
    private var currentSessionId: Long? = null
    private var startTimeMillis: Long = 0L
    private var sensorJob: Job? = null
    private var locationJob: Job? = null
    private var addressCheckJob: Job? = null
    
    private var trackPointCounter = 0
    private var isStartAddressSaved = false
    
    private var lastThoroughfareKey: String? = null
    private var accuracyLimit: Float = 20.0f
    private var isVoiceEnabled: Boolean = true

    private var lastAccurateLocation: Location? = null
    private var lastAccurateTrackId: Long? = null
    private var lastProcessedLocation: Location? = null

    private val addressCheckMutex = Mutex()

    var onAddressUpdate: ((AddressRecord) -> Unit)? = null

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
        stepSensorManager = StepSensorManager(this)
        locationManager = LocationManager(this)
        ttsHelper = TextToSpeechHelper(this)

        createNotificationChannel()

        // 設定から精度制限と音声有効フラグを取得して監視
        serviceScope.launch {
            database.settingsDao().getSettings().collect { settings ->
                accuracyLimit = settings?.locationAccuracyLimit ?: 20.0f
                isVoiceEnabled = settings?.isVoiceEnabled ?: true
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
        lastAccurateLocation = null
        lastAccurateTrackId = null
        lastProcessedLocation = null
        startTimeMillis = System.currentTimeMillis()

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
        startAddressCheckTimer()

        serviceScope.launch {
            val newSection = Section(
                trackStartId = null,
                createdAtTimestamp = startTimeMillis
            )
            currentSessionId = database.sectionDao().insertSection(newSection)
        }
    }

    private fun launchStepMeasurement() {
        sensorJob?.cancel()
        sensorJob = stepSensorManager.steps().onEach { steps ->
            _currentSteps.value = steps
            val displaySteps = "$steps"
            updateNotification("歩数: $displaySteps, 位置情報: ${trackPointCounter}件")
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

            // 住所保存と音声案内のロジック
            if (location.accuracy <= accuracyLimit) {
                lastAccurateLocation = location
                lastAccurateTrackId = insertedId

                // 200m移動による割り込みチェック
                val dist = lastProcessedLocation?.distanceTo(location) ?: Float.MAX_VALUE
                if (dist >= 200f && isStartAddressSaved) {
                    serviceScope.launch { processAddressCheck() }
                }

                // 開始時の住所保存
                if (!isStartAddressSaved) {
                    // 最初の住所保存
                    isStartAddressSaved = true
                    serviceScope.launch {
                        locationManager.updateCachedLocale(location.latitude, location.longitude)
                        processAddressCheck(isInitial = true)
                    }
                }
            }
            
            trackPointCounter++
            _currentTrackCount.value = trackPointCounter
            val displaySteps = "${_currentSteps.value}"
            updateNotification("歩数: $displaySteps, 位置情報: ${trackPointCounter}件")

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

    private fun startAddressCheckTimer() {
        addressCheckJob?.cancel()
        addressCheckJob = serviceScope.launch {
            while (true) {
                delay(Constants.ADDRESS_PROCESS_INTERVAL_MS)
                processAddressCheck()
            }
        }
    }

    /**
     * 住所の変化をチェックし、必要に応じて保存と案内を行います。
     * @param isInitial 開始時の即時処理フラグ
     */
    private suspend fun processAddressCheck(isInitial: Boolean = false) = addressCheckMutex.withLock {
        val location = lastAccurateLocation ?: return
        val trackId = lastAccurateTrackId
        val sessionId = currentSessionId
        val currentTime = System.currentTimeMillis()

        // 距離判定の最適化: 
        // 5m未満の微細な移動でも、まだ「その場所」の住所を一度も取得できていない（lastProcessedLocationがnull）場合は
        // スキップせずにアグレッシブに取得を試みます。
        val distance = lastProcessedLocation?.distanceTo(location) ?: Float.MAX_VALUE
        if (!isInitial && distance < 5f && lastProcessedLocation != null && lastThoroughfareKey != null) {
            // すでに直近で住所取得に成功しており、かつ移動が小さい場合はスキップ
            return
        }

        Log.d("TrackingService", "Address check triggered: dist=$distance, initial=$isInitial")

        // 住所オブジェクトを取得（1回のみ）
        val address = locationManager.getLocaleAddress(location.latitude, location.longitude)
        
        if (address == null) {
            Log.w("TrackingService", "Address fetch failed (Geocoder returned null). Will retry.")
            // 失敗した場合は lastProcessedLocation を更新せず、次回のタイマー等でのリトライを許容する
            return
        }

        // 取得に成功したので、この地点（またはそのごく近傍）は「判定済み」として記録
        lastProcessedLocation = Location(location)
        
        val currentKey = locationManager.getAddressKey(address)
        val isKeyChanged = currentKey != lastThoroughfareKey

        Log.d("TrackingService", "Comparing address keys: current='$currentKey', last='$lastThoroughfareKey', changed=$isKeyChanged")

        if (isKeyChanged || isInitial) {
            Log.i("TrackingService", "Updating address record. Key: $currentKey")
            lastThoroughfareKey = currentKey
            
            val insertedAddressRecord = locationManager.saveAddressRecord(
                lat = location.latitude,
                lng = location.longitude,
                sectionId = sessionId,
                trackId = trackId,
                timestamp = currentTime,
                address = address
            )
            insertedAddressRecord?.let {
                onAddressUpdate?.invoke(it)
            }

            if (isVoiceEnabled) {
                val speakText = AddressRecord(address = address).cityDisplayWithFeature()
                if (!speakText.isNullOrBlank()) {
                    val msg = if (isInitial) "計測を開始しました。現在地は $speakText です。" else speakText
                    ttsHelper.speak(msg)
                } else if (isInitial) {
                    ttsHelper.speak("計測を開始しました。")
                }
            }
        }
    }

    private fun stopTracking() {
        if (!_isRunning.value) return
        
        sensorJob?.cancel()
        locationJob?.cancel()
        addressCheckJob?.cancel()
        _isRunning.value = false

        // IDなどを確実に保持するためにローカル変数へコピー
        val sessionId = currentSessionId
        val initialSteps = _currentSteps.value
        val startT = startTimeMillis
        val endT = System.currentTimeMillis()

        serviceScope.launch {
            try {
                var finalSteps = initialSteps
                val currentSettings = database.settingsDao().getSettings().first()
                val limit = currentSettings?.locationAccuracyLimit ?: 20.0f

                val lastAccuratePoint = database.trackPointDao().getLastAccurateTrackPoint(limit)
                val lastTrackPoint = database.trackPointDao().getLastTrackPoint()
                
                if (sessionId != null) {
                    if (lastAccuratePoint != null) {
                        locationManager.saveAddressRecord(
                            lat = lastAccuratePoint.latitude,
                            lng = lastAccuratePoint.longitude,
                            sectionId = sessionId,
                            trackId = lastAccuratePoint.id,
                            timestamp = lastAccuratePoint.time
                        )
                    }

                    // 歩数セグメントを保存
                    val stepSegment = StepSegment(
                        sectionId = sessionId,
                        steps = finalSteps,
                        startTime = startT,
                        endTime = endT
                    )
                    val insertedSegmentId = database.stepSegmentDao().insertStepSegment(stepSegment)
                    Log.i("TrackingService", "【最新版】StepSegmentを保存: ID=$insertedSegmentId, steps=$finalSteps, sessionId=$sessionId")

                    val section = database.sectionDao().getSectionById(sessionId)
                    section?.let {
                        val updatedSection = it.copy(
                            durationSeconds = (endT - startT) / 1000,
                            trackEndId = lastTrackPoint?.id
                        )
                        database.sectionDao().updateSection(updatedSection)
                    }
                    
                    if (isVoiceEnabled) {
                        ttsHelper.speak("計測を終了しました。お疲れ様でした。")
                    }
                } else {
                    Log.e("TrackingService", "【最新版】sessionId が null のため保存をスキップ")
                }
            } catch (e: Exception) {
                Log.e("TrackingService", "【最新版】停止処理中にエラー発生", e)
            } finally {
                currentSessionId = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tracking Service Channel",
                NotificationManager.IMPORTANCE_LOW
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::ttsHelper.isInitialized) {
            ttsHelper.shutdown()
        }
        // スコープ内の全ジョブをキャンセル
        serviceScope.coroutineContext[Job]?.cancelChildren()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "tracking_channel"
    }
}
