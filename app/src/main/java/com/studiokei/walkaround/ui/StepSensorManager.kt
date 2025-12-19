package com.studiokei.walkaround.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

class StepSensorManager(
    context: Context,
    private val healthConnectManager: HealthConnectManager
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private var stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    val sensorMode: SensorMode = when {
        stepCounterSensor != null -> SensorMode.COUNTER
        stepDetectorSensor != null -> SensorMode.DETECTOR
        healthConnectManager.isAvailable -> SensorMode.HEALTH_CONNECT
        else -> SensorMode.UNAVAILABLE
    }

    val isStepSensorAvailable: Boolean = sensorMode != SensorMode.UNAVAILABLE

    private var initialSteps = -1
    private var sessionSteps = 0
    private var healthConnectInitialSteps = -1L
    private var startTime: Instant = Instant.now()

    fun steps(): Flow<Int> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                when (sensorMode) {
                    SensorMode.COUNTER -> {
                        val totalSteps = event.values[0].toInt()
                        if (initialSteps == -1) {
                            initialSteps = totalSteps
                        }
                        sessionSteps = totalSteps - initialSteps
                        trySend(sessionSteps)
                    }
                    SensorMode.DETECTOR -> {
                        if (event.values[0] == 1.0f) {
                            sessionSteps++
                        }
                        trySend(sessionSteps)
                    }
                    else -> { /* Do nothing */ }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        when (sensorMode) {
            SensorMode.COUNTER, SensorMode.DETECTOR -> {
                startDirectSensor(listener)
            }
            SensorMode.HEALTH_CONNECT -> {
                // Polling for Health Connect
                launch {
                    startTime = Instant.now()
                    healthConnectInitialSteps = healthConnectManager.readSteps(
                        start = startTime.minusSeconds(60), // Look back a minute to get a baseline
                        end = startTime
                    )
                    while (isActive) {
                        val totalStepsNow = healthConnectManager.readSteps(
                            start = startTime,
                            end = Instant.now()
                        )
                        sessionSteps = totalStepsNow.toInt()
                        trySend(sessionSteps)
                        delay(5000) // Poll every 5 seconds
                    }
                }
            }
            SensorMode.UNAVAILABLE -> { /* Do nothing */ }
        }

        awaitClose {
            if (sensorMode == SensorMode.COUNTER || sensorMode == SensorMode.DETECTOR) {
                stopDirectSensor(listener)
            }
        }
    }

    private fun startDirectSensor(listener: SensorEventListener) {
        sessionSteps = 0
        initialSteps = -1
        val sensor = if (sensorMode == SensorMode.COUNTER) stepCounterSensor else stepDetectorSensor
        sensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun stopDirectSensor(listener: SensorEventListener) {
        sensorManager.unregisterListener(listener)
    }

    enum class SensorMode {
        COUNTER, DETECTOR, HEALTH_CONNECT, UNAVAILABLE
    }
}
