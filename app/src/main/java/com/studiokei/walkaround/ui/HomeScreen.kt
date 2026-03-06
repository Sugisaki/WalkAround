package com.studiokei.walkaround.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.SectionSummary
import com.studiokei.walkaround.ui.StepSensorManager.SensorMode
import com.studiokei.walkaround.ui.components.getNeumorphicBg
import com.studiokei.walkaround.ui.components.NeumorphicButton
import com.studiokei.walkaround.ui.components.NeumorphicSurface
import com.studiokei.walkaround.util.DateTimeFormatUtils
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToInt

/**
 * ホーム画面。
 * 歩数や位置情報の現在の状態、および過去の走行セクション一覧を表示します。
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onSectionClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val appDatabase = AppDatabase.getDatabase(context)
                HomeViewModel(
                    context.applicationContext,
                    appDatabase,
                    StepSensorManager(context),
                    FitnessHistoryManager(context)
                )
            }
        }
    )
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    // --- GPS無効時に表示するダイアログの状態管理 ---
    var showGpsDisabledDialog by rememberSaveable { mutableStateOf(false) }

    // --- 権限リクエスト用ランチャー ---
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> /* 通知権限の結果はここでは特にハンドリングしない */ }

    // --- 位置情報設定画面を開くためのランチャー ---
    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { /* 設定画面から戻ってきた際の処理は必要に応じて追加 */ }

    // --- 身体活動(Activity Recognition)の権限リクエスト用ランチャー ---
    val activityRecognitionPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // ViewModelに権限結果を通知
        homeViewModel.onActivityRecognitionPermissionResult(isGranted)
    }

    // --- 位置情報の権限リクエスト用ランチャー ---
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 権限リクエスト後のアクションは、呼び出し元のボタンに責任を移譲 */ }

    // Android 13以降での通知権限の確認
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun handleStartClick() {
        // GPSが有効かチェック
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!isGpsEnabled) {
            showGpsDisabledDialog = true
            return
        }

        val fineLocationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasLocation = fineLocationGranted || coarseLocationGranted

        val activityRecognitionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        // 1. 位置情報がない場合
        if (!hasLocation) {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            // 身体活動の権限も必要なら同時にリクエスト
            if (!activityRecognitionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uiState.sensorMode != SensorMode.UNAVAILABLE) {
                permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            locationPermissionLauncher.launch(permissions.toTypedArray())
            return
        }

        // 2. 位置情報はあるが、身体活動の権限がない場合
        if (!activityRecognitionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uiState.sensorMode != SensorMode.UNAVAILABLE) {
            activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            return
        }

        // 3. 必要な権限がすべて揃っている場合
        homeViewModel.startTracking()
    }

    // 住所表示ボタン押下時の処理
    fun handleFetchAddressClick() {
        // --- GPSが有効かチェック ---
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!isGpsEnabled) {
            showGpsDisabledDialog = true
            return
        }

        val fineLocationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (fineLocationGranted || coarseLocationGranted) {
            homeViewModel.fetchCurrentAddress()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // --- ダイアログ表示 ---
    // GPS無効時ダイアログ
    if (showGpsDisabledDialog) {
        GpsDisabledDialog(
            onConfirm = {
                showGpsDisabledDialog = false
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                locationSettingsLauncher.launch(intent)
            },
            onDismiss = { showGpsDisabledDialog = false }
        )
    }

    // GPSロスト（走行中停止）ダイアログ
    if (uiState.showGpsLostDialog) {
        GpsLostDialog(onDismiss = { homeViewModel.dismissGpsLostDialog() })
    }

    // 住所表示ダイアログ
    if (uiState.showAddressDialog) {
        AddressDialog(
            address = uiState.currentAddress,
            featureName = uiState.currentFeatureName,
            onDismiss = { homeViewModel.dismissAddressDialog() }
        )
    }
    
    // 歩数履歴表示ダイアログ
    if (uiState.showStepsDialog) {
        DailyStepsDialog(
            dailySteps = uiState.dailySteps,
            onDismiss = { homeViewModel.dismissStepsDialog() }
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = getNeumorphicBg()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // スタート／ストップボタン
            item {
                val isRunning = uiState.isRunning
                val buttonText = if (isRunning) "STOP" else "START"
                val onClickAction = if (isRunning) {
                    { homeViewModel.requestStopTracking() }
                } else {
                    { handleStartClick() }
                }

                NeumorphicButton(
                    onClick = onClickAction,
                    modifier = Modifier.size(160.dp),
                    shape = CircleShape
                ) {
                    Text(
                        buttonText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = if (isRunning) Color(0xFFE57373) else MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(48.dp))
            }

            // サブボタン群
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 歩数記録確認ボタン（Android 10 以上）
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uiState.isFitnessApiAvailable) {
                        NeumorphicButton(
                            onClick = {
                                val permission = Manifest.permission.ACTIVITY_RECOGNITION
                                if (context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    homeViewModel.fetchDailySteps()
                                } else {
                                    activityRecognitionPermissionLauncher.launch(permission)
                                }
                            },
                            modifier = Modifier.size(120.dp, 60.dp)
                        ) {
                            Text("歩数記録", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    // 住所確認ボタン
                    NeumorphicButton(
                        onClick = { handleFetchAddressClick() },
                        modifier = Modifier.size(120.dp, 60.dp)
                    ) {
                        Text("住所確認", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // 走行中の住所表示
            if (uiState.isRunning) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    NeumorphicSurface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            uiState.displayAddress?.let { address ->
                                Text(text = address, style = MaterialTheme.typography.bodyMedium)
                            }
                            uiState.displayFeatureName?.let { featureName ->
                                Text(
                                    text = featureName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }

            // 歩数・位置情報表示
            item {
                CurrentStatusCard(uiState)
            }

            // 走行セクション
            if (!uiState.isRunning) {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (uiState.sections.isNotEmpty()) {
                    item {
                        Text(
                            text = "走行セクション",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(uiState.sections, key = { it.sectionId }) { summary ->
                        SwipeableSectionCard(
                            sectionSummary = summary,
                            displayUnit = uiState.displayUnit,
                            onDelete = { homeViewModel.requestDeletion(summary.sectionId) },
                            onClick = { onSectionClick(summary.sectionId) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // 削除確認ダイアログ
    if (uiState.showDeleteConfirmDialog) {
        DeleteConfirmDialog(
            onConfirm = { homeViewModel.confirmDeletion() },
            onDismiss = { homeViewModel.cancelDeletion() }
        )
    }

    // 削除完了ダイアログ
    if (uiState.showDeleteDoneDialog) {
        LaunchedEffect(uiState.showDeleteDoneDialog) {
            kotlinx.coroutines.delay(1000)
            homeViewModel.dismissDeleteDoneDialog()
        }
        DeleteDoneDialog(onDismiss = { homeViewModel.dismissDeleteDoneDialog() })
    }

    // 走行停止確認ダイアログ
    if (uiState.showStopConfirmDialog) {
        LaunchedEffect(uiState.showStopConfirmDialog) {
            kotlinx.coroutines.delay(10000)
            homeViewModel.cancelStopTracking()
        }
        StopConfirmDialog(
            onConfirm = { homeViewModel.confirmStopTracking() },
            onDismiss = { homeViewModel.cancelStopTracking() }
        )
    }
}

@Composable
private fun CurrentStatusCard(uiState: HomeUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (uiState.sensorMode != SensorMode.UNAVAILABLE) {
            Text(
                text = if (uiState.isRunning) "歩数" else "今日の歩数",
                style = MaterialTheme.typography.titleSmall,
                color = Color.Gray
            )
            Text(
                text = "${if (uiState.isRunning) uiState.currentStepCount else uiState.todayStepCount.toLong()}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SwipeableSectionCard(
    sectionSummary: SectionSummary,
    displayUnit: String,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val deleteButtonWidth = 80.dp
    val cardShape = CardDefaults.shape
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Red, shape = cardShape)
    ) {
        // Background delete button
        IconButton(
            onClick = {
                // スワイプをリセットしてから削除処理を呼ぶ
                coroutineScope.launch {
                    offsetX.animateTo(0f)
                    onDelete()
                }
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(deleteButtonWidth)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "削除",
                tint = Color.White
            )
        }

        // Foreground card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(
                                    -with(density) { deleteButtonWidth.toPx() } * 1.2f,
                                    0f
                                )
                                offsetX.snapTo(newOffset)
                            }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                val threshold = -with(density) { deleteButtonWidth.toPx() / 2 }
                                if (offsetX.value < threshold) {
                                    offsetX.animateTo(-with(density) { deleteButtonWidth.toPx() })
                                } else {
                                    offsetX.animateTo(0f)
                                }
                            }
                        }
                    )
                }
                .clickable {
                    // カードがスワイプされていない場合のみクリックを処理
                    if (offsetX.value == 0f) {
                        onClick()
                    } else {
                        // スワイプされている場合は元の位置に戻す
                        coroutineScope.launch {
                            offsetX.animateTo(0f)
                        }
                    }
                }
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = DateTimeFormatUtils.headerDateFormatter.format(Instant.ofEpochMilli(sectionSummary.startTimeMillis)),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Track: ${sectionSummary.trackPointCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sec: ${sectionSummary.sectionId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            // アイコンの分のスペースを確保
                            Spacer(modifier = Modifier.width(40.dp))
                        }
                    }

                    val startCity = sectionSummary.startCityDisplay()
                    val destCity = sectionSummary.destinationCityDisplay()

                    if (destCity != null) {
                        Text(
                            text = "🔴 $destCity",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (startCity != null) {
                        Text(
                            text = "⬆️ $startCity",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (sectionSummary.distanceMeters != null) {
                            val meters = sectionSummary.distanceMeters
                            val distanceDisplay = if (displayUnit == "mile") {
                                "距離: %.2f mile".format(meters / 1609.34)
                            } else {
                                "距離: %.2f km".format(meters / 1000.0)
                            }
                            Text(text = distanceDisplay, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(text = "距離: ---", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (sectionSummary.steps > 0) {
                            Text(
                                text = "歩数: ${sectionSummary.steps}",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
                // アクションを開くためのインジケーターボタン
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            offsetX.animateTo(-with(density) { deleteButtonWidth.toPx() })
                        }
                    },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "アクションを表示",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                shape = CircleShape
                            )
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

// --- ダイアログ ---

@Composable
private fun StopConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("走行の停止") },
        text = { Text("本当に走行を停止しますか？") },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("ストップ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
private fun GpsDisabledDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("位置情報が無効です") },
        text = { Text("位置情報を利用するには、設定で位置情報サービスを有効にしてください。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("設定を開く") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

@Composable
private fun GpsLostDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("記録停止") },
        text = { Text("GPSロストにより記録を停止しました。") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

@Composable
private fun AddressDialog(address: String?, featureName: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("現在地") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = address ?: "住所を取得中...",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (!featureName.isNullOrBlank()) {
                    Text(
                        text = featureName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

@Composable
private fun DailyStepsDialog(dailySteps: List<Pair<String, Long>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("過去7日間の歩数") },
        text = {
            LazyColumn {
                items(dailySteps) { (date, steps) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(date)
                        Text("$steps 歩", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

@Composable
private fun DeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("削除") },
        text = { Text("この記録を削除しますか？") },
        confirmButton = { TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) { Text("削除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

@Composable
private fun DeleteDoneDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("完了") },
        text = { Text("削除しました。") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}
