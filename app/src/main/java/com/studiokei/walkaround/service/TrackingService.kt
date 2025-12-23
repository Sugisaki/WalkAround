package com.studiokei.walkaround.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.studiokei.walkaround.R
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.Section
import com.studiokei.walkaround.data.model.StepSegment
import com.studiokei.walkaround.data.model.TrackPoint
import com.studiokei.walkaround.ui.HealthConnectManager
import com.studiokei.walkaround.ui.LocationManager
import com.studiokei.walkaround.ui.StepSensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        startTime = System.currentTimeMillis()

        startForeground(NOTIFICATION_ID, createNotification("トラッキング中..."))

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
            _currentTrackCount.value = trackPointCounter
            updateNotification("歩数: ${_currentSteps.value}, 位置情報: ${trackPointCounter}件")

            currentSessionId?.let { sessionId ->
                val currentSection = database.sectionDao().getSectionById(sessionId)
                if (currentSection != null && currentSection.trackStartId == null) {
                    database.sectionDao().updateSection(
                        currentSection.copy(trackStartId = insertedId)
                    )
                }
            }
        }.launchIn(serviceScope)
    }

    private fun stopTracking() {
        sensorJob?.cancel()
        locationJob?.cancel()
        _isRunning.value = false

        serviceScope.launch {
            val endTime = System.currentTimeMillis()
            val lastTrackPoint = database.trackPointDao().getLastTrackPoint()
            
            currentSessionId?.let { sessionId ->
                val stepSegment = StepSegment(
                    sectionId = sessionId,
                    steps = _currentSteps.value,
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
                NotificationManager.IMPORTANCE_DEFAULT // LOWからDEFAULTに変更
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // 優先度を明示的に設定
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
