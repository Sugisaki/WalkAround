package com.studiokei.walkaround.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.ui.StepSensorManager.SensorMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onSectionClick: (Long) -> Unit = {} // クリックイベントを追加
) {
    val context = LocalContext.current
    val healthConnectManager = HealthConnectManager(context)
    val homeViewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    AppDatabase.getDatabase(context),
                    StepSensorManager(context, healthConnectManager),
                    healthConnectManager,
                    LocationManager(context)
                )
            }
        }
    )
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        .withZone(ZoneId.systemDefault())

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

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        ) {
            homeViewModel.startTracking()
        }
    }

    fun requestPermissions() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
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
        val fineLocationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (fineLocationGranted || coarseLocationGranted) {
            homeViewModel.startTracking()
        } else {
            requestPermissions()
        }
    }

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ステータス表示エリア
            if (uiState.sensorMode == SensorMode.UNAVAILABLE) {
                Text(
                    text = "歩数計センサーまたはヘルスコネクトがこのデバイスでは利用できません。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Red,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (uiState.isRunning) {
                Text(text = "現在の歩数", style = MaterialTheme.typography.titleMedium)
                Text(text = "${uiState.currentStepCount}", style = MaterialTheme.typography.displayLarge)
                
                Spacer(modifier = Modifier.height(8.dp))

                Text(text = "現在の位置情報の数", style = MaterialTheme.typography.titleMedium)
                Text(text = "${uiState.currentTrackPointCount}", style = MaterialTheme.typography.displayLarge)

                val sensorText = when (uiState.sensorMode) {
                    SensorMode.COUNTER -> "取得方法: 歩数カウンター (ハードウェア)"
                    SensorMode.DETECTOR -> "取得方法: 歩数検出器 (ハードウェア)"
                    SensorMode.HEALTH_CONNECT -> {
                        if (uiState.hasHealthConnectPermissions) "取得方法: ヘルスコネクト"
                        else "取得方法: ヘルスコネクト (権限不足)"
                    }
                    SensorMode.UNAVAILABLE -> "取得方法: 利用不可"
                }
                Text(
                    text = sensorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                // 非走行時に「本日の歩数」を表示
                Text(text = "本日の歩数", style = MaterialTheme.typography.titleMedium)
                Text(text = "${uiState.todayStepCount}", style = MaterialTheme.typography.displayLarge)
                Spacer(modifier = Modifier.height(24.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isRunning) {
                Button(onClick = { homeViewModel.stopTracking() }) {
                    Text("ストップ")
                }
            } else {
                Button(onClick = { handleStartClick() }) {
                    Text("スタート")
                }
            }

            // 走行中でないときのみ、過去のセクション一覧を表示
            if (!uiState.isRunning) {
                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.sections.isNotEmpty()) {
                    Text(
                        text = "過去のセクション",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.sections) { summary ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSectionClick(summary.sectionId) } // クリックイベント
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = dateTimeFormatter.format(Instant.ofEpochMilli(summary.startTimeMillis)),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "歩数: ${summary.steps}", style = MaterialTheme.typography.bodyMedium)
                                        Text(text = "Track数: ${summary.trackPointCount}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
