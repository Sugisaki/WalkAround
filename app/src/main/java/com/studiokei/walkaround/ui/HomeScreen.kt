package com.studiokei.walkaround.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.ui.StepSensorManager.SensorMode

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val healthConnectManager = HealthConnectManager(context)
    val homeViewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    AppDatabase.getDatabase(context),
                    StepSensorManager(context, healthConnectManager),
                    healthConnectManager
                )
            }
        }
    )
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    // --- 権限リクエスト用ランチャー ---
    val healthConnectPermissionsLauncher = rememberLauncherForActivityResult(
        contract = healthConnectManager.requestPermissionsContract()
    ) { grantedPermissions ->
        homeViewModel.onPermissionsResult(grantedPermissions.values.all { it })
    }

    val activityRecognitionPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        homeViewModel.onPermissionsResult(isGranted)
    }

    // 権限リクエストをまとめて行うメソッド
    fun requestPermissions() {
        when (uiState.sensorMode) {
            SensorMode.COUNTER, SensorMode.DETECTOR -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }
            SensorMode.HEALTH_CONNECT -> {
                if (!uiState.hasHealthConnectPermissions) {
                    healthConnectPermissionsLauncher.launch(
                        arrayOf("androidx.health.connect.permission.read.STEPS")
                    )
                }
            }
            else -> {}
        }
    }

    fun handleStartClick() {
        requestPermissions() // 権限リクエストを実施
        homeViewModel.startTracking() // 直後に無条件で開始
    }

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (uiState.sensorMode == SensorMode.UNAVAILABLE) {
                Text(
                    text = "歩数計センサーまたはヘルスコネクトがこのデバイスでは利用できません。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Red,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = "現在の歩数",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${uiState.currentStepCount}",
                style = MaterialTheme.typography.displayLarge
            )
            
            if (uiState.isRunning) {
                val sensorText = when (uiState.sensorMode) {
                    SensorMode.COUNTER -> "取得方法: 歩数カウンター (ハードウェア)"
                    SensorMode.DETECTOR -> "取得方法: 歩数検出器 (ハードウェア)"
                    SensorMode.HEALTH_CONNECT -> {
                        if (uiState.hasHealthConnectPermissions) "取得方法: ヘルスコネクト"
                        else "取得方法: ヘルスコネクト (権限不足)"
                    }
                    SensorMode.UNAVAILABLE -> "取得方法: 利用不可 (歩数は取得できません)"
                }
                Text(
                    text = sensorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (uiState.isRunning) {
                Button(onClick = { homeViewModel.stopTracking() }) {
                    Text("ストップ")
                }
            } else {
                Button(
                    onClick = { handleStartClick() }
                ) {
                    Text("スタート")
                }

                if (uiState.sensorMode == SensorMode.HEALTH_CONNECT && !uiState.hasHealthConnectPermissions) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ヘルスコネクトの権限が必要です。",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
