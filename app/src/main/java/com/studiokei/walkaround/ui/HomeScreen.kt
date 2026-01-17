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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.RectangleShape
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

    Scaffold(modifier = modifier) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // エラー表示
            if (uiState.sensorMode == SensorMode.UNAVAILABLE) {
                item {
                    Text(
                        text = "歩数計センサーが利用できません。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 11.dp)
                    )
                }
            } else {
                // 歩数・位置情報表示
                item {
                    CurrentStatusCard(uiState)
                }
            }

            // 歩数記録確認ボタン（Android 10 以上）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uiState.isFitnessApiAvailable) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val permission = Manifest.permission.ACTIVITY_RECOGNITION
                            if (context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                homeViewModel.fetchDailySteps()
                                Log.d("HomeScreen", "Permission granted")
                            } else {
                                activityRecognitionPermissionLauncher.launch(permission)
                                Log.e("HomeScreen", "Permission not granted")
                            }
                        },
                        shape = RectangleShape,
                        border = BorderStroke(1.dp, Color.Black),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "歩数記録を確認",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 住所表示ボタン
            item {
                Button(onClick = { handleFetchAddressClick() }) {
                    Text("住所を表示")
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }

            // スタート／ストップボタン
            item {
                if (uiState.isRunning) {
                    Button(onClick = { homeViewModel.stopTracking() }) {
                        Text("ストップ")
                    }
                } else {
                    Button(onClick = { handleStartClick() }) {
                        Text("スタート")
                    }
                }
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
            if (uiState.showDeleteDoneDialog) {
                kotlinx.coroutines.delay(1000)
                homeViewModel.dismissDeleteDoneDialog()
            }
        }
        DeleteDoneDialog(onDismiss = { homeViewModel.dismissDeleteDoneDialog() })
    }
}

// --- ダイアログや複雑なコンポーネントを別Composableに分割 ---

@Composable
private fun GpsDisabledDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("位置情報が無効です") },
        text = { Text("位置情報を利用するには、端末の設定で位置情報サービスを有効にしてください。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("設定を開く") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
private fun GpsLostDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("記録を停止しました") },
        text = { Text("GPSがオフになったため、記録を自動的に停止しました。") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

@Composable
private fun AddressDialog(address: String?, featureName: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("現在地の住所") },
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
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

@Composable
private fun DailyStepsDialog(dailySteps: List<Pair<String, Long>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("過去7日間の歩数記録") },
        text = {
            if (dailySteps.isEmpty()) {
                Text("記録がありません。")
            } else {
                LazyColumn {
                    items(dailySteps) { (date, steps) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = date)
                            Text(text = "$steps 歩", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("セクションの削除") },
        text = { Text("このセクションを削除しますか？\nこの操作は元に戻せません。") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
            ) { Text("削除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
private fun DeleteDoneDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("削除完了") },
        text = { Text("セクションを削除しました。") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

@Composable
private fun CurrentStatusCard(uiState: HomeUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (uiState.isRunning) {
            Text(text = "現在の歩数", style = MaterialTheme.typography.titleMedium)
            Text(text = "${uiState.currentStepCount}", style = MaterialTheme.typography.displayLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "現在の位置情報の数", style = MaterialTheme.typography.titleMedium)
            Text(text = "${uiState.currentTrackPointCount}", style = MaterialTheme.typography.displayLarge)

            val sensorText = when (uiState.sensorMode) {
                SensorMode.COUNTER -> "取得方法: 歩数カウンター (ハードウェア)"
                SensorMode.DETECTOR -> "取得方法: 歩数検出器 (ハードウェア)"
                SensorMode.UNAVAILABLE -> "取得方法: 利用不可"
            }
            Text(
                text = sensorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Text(text = "本日の歩数", style = MaterialTheme.typography.titleMedium)
            val displaySteps = uiState.todayStepCount.toLong()
            Text(text = "$displaySteps", style = MaterialTheme.typography.displayLarge)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}


/**
 * 横スワイプで削除ボタンを表示できるセクションカード。
 *
 * @param sectionSummary 表示するセクションの概要データ。
 * @param displayUnit 距離の表示単位 ("km" または "mile")。
 * @param onDelete 削除ボタンがクリックされたときのコールバック。
 * @param onClick カード本体がクリックされたときのコールバック。
 */
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
                // ここに元のCardの内容をコピー
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
                        Text(
                            text = "歩数: ${sectionSummary.steps}",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.End
                        )
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
