package com.studiokei.walkaround.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant

class StepSensorManager(
    context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private var stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    val sensorMode: SensorMode = when {
        stepCounterSensor != null -> SensorMode.COUNTER
        stepDetectorSensor != null -> SensorMode.DETECTOR
        else -> SensorMode.UNAVAILABLE
    }

    val isStepSensorAvailable: Boolean = sensorMode != SensorMode.UNAVAILABLE

    private var initialSteps = -1
    private var sessionSteps = 0

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
                    else -> {
                        /* Do nothing */
                        Log.e("StepSensorManager", "Unsupported sensor mode: $sensorMode")
                    }
                }

                Log.d("StepSensorManager", "sensor mode: $sensorMode")

            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        when (sensorMode) {
            SensorMode.COUNTER, SensorMode.DETECTOR -> {
                startDirectSensor(listener)
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
        COUNTER, DETECTOR, UNAVAILABLE
    }
}
