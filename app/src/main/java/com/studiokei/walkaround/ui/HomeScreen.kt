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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.ui.StepSensorManager.SensorMode
import com.studiokei.walkaround.util.DateTimeFormatUtils
import java.time.Instant
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import com.studiokei.walkaround.data.model.SectionSummary
import kotlinx.coroutines.launch
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
    val healthConnectManager = HealthConnectManager(context)
    val homeViewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    context.applicationContext,
                    AppDatabase.getDatabase(context),
                    StepSensorManager(context, healthConnectManager),
                    healthConnectManager
                )
            }
        }
    )
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    // --- 権限リクエスト用ランチャー ---
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val healthConnectPermissionsLauncher = rememberLauncherForActivityResult(
        contract = healthConnectManager.requestPermissionsContract()
    ) { grantedPermissions ->
        homeViewModel.onPermissionsResult(grantedPermissions.values.all { it })
        // ヘルスコネクトの確認が終わったら、結果に関わらず開始
        homeViewModel.startTracking()
    }

    val activityRecognitionPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        homeViewModel.onPermissionsResult(isGranted)
        if (isGranted) {
            // 身体活動の許可が得られたら開始（ヘルスコネクトは不要）
            homeViewModel.startTracking()
        } else {
            // 身体活動の許可が得られなかった場合のみ、ヘルスコネクトが必要か確認
            if (uiState.sensorMode == SensorMode.HEALTH_CONNECT && !uiState.hasHealthConnectPermissions) {
                println("🟧🟧 身体活動拒否 -> ヘルスコネクト権限リクエストへ")
                healthConnectPermissionsLauncher.launch(arrayOf("androidx.health.connect.permission.read.STEPS"))
            } else {
                // ヘルスコネクトが使えない場合、開始
                homeViewModel.startTracking()
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        ) {
            println("[Debug] 🟧🟧 位置情報許可後の開始")
            homeViewModel.startTracking()
        }
    }

    // Android 13以降での通知権限の確認
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun handleStartClick() {
        val fineLocationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasLocation = fineLocationGranted || coarseLocationGranted

        val activityRecognitionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        // 1. 位置情報が全くない場合はリクエスト（必要なら身体活動も混ぜる）
        if (!hasLocation) {
            println("🟧🟧 1. 位置情報がないため権限リクエストへ")
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (!activityRecognitionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && 
                uiState.sensorMode != SensorMode.UNAVAILABLE) {
                permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            locationPermissionLauncher.launch(permissions.toTypedArray())
            return
        }

        // --- ここから「位置情報はある」状態 ---
        // 2. 身体活動がない場合、リクエスト（ランチャー側で拒否時のみヘルスコネクトを確認する）
        if (!activityRecognitionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            uiState.sensorMode != SensorMode.UNAVAILABLE) {
            println("🟧🟧 2. 身体活動権限リクエストへ")
            activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            return
        }

        // 権限は揃っている（または身体活動の許可がある）ので、即座にトラッキングを開始！
        homeViewModel.startTracking()
    }

    // 住所表示ボタン押下時の処理
    fun handleFetchAddressClick() {
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

    // 住所表示用ダイアログの修正
    if (uiState.showAddressDialog) {
        AlertDialog(
            onDismissRequest = { homeViewModel.dismissAddressDialog() },
            title = { Text("現在地の住所") },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 1行目: 住所 (標準的なサイズ)
                    Text(
                        text = uiState.currentAddress ?: "住所を取得中...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    // 2行目: 地点名称 (少し大きいサイズ・太字)
                    if (!uiState.currentFeatureName.isNullOrBlank()) {
                        Text(
                            text = uiState.currentFeatureName!!,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { homeViewModel.dismissAddressDialog() }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(modifier = modifier) { innerPadding ->
        // ルートを LazyColumn に変更し、画面全体をスクロール可能にする
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp), // 全体にパディングを適用
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // エラー表示
            if (uiState.sensorMode == SensorMode.UNAVAILABLE) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "歩数計センサーまたはヘルスコネクトがこのデバイスでは利用できません。",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Red,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // 歩数や位置情報の表示
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (uiState.isRunning) {
                        // ヘルスコネクトモード以外の場合のみ現在の歩数を表示
                        if (uiState.sensorMode != SensorMode.HEALTH_CONNECT) {
                            Text(text = "現在の歩数", style = MaterialTheme.typography.titleMedium)
                            Text(text = "${uiState.currentStepCount}", style = MaterialTheme.typography.displayLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

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
                        Text(text = "本日の歩数", style = MaterialTheme.typography.titleMedium)

                        // ヘルスコネクトの値をメインに表示 (権限がある場合)
                        val displaySteps = if (uiState.isHealthConnectAvailable && uiState.hasHealthConnectPermissions) {
                            uiState.todayHealthConnectSteps ?: uiState.todayStepCount.toLong()
                        } else {
                            uiState.todayStepCount.toLong()
                        }
                        Text(text = "$displaySteps", style = MaterialTheme.typography.displayLarge)

                        Spacer(modifier = Modifier.height(8.dp))

                        // ヘルスコネクトの状態表示（異常時のみメッセージを表示）
                        if (!uiState.isHealthConnectAvailable) {
                            // ヘルスコネクトはこのデバイスでは利用できません
                            // 何も表示しない
                        } else if (!uiState.hasHealthConnectPermissions) {
                            Text(
                                text = "ヘルスコネクトに接続されていません",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
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
                    // ネストした LazyColumn の代わりに、ここで直接 items を使用
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
        AlertDialog(
            onDismissRequest = { homeViewModel.cancelDeletion() },
            title = { Text("セクションの削除") },
            text = { Text("このセクションを削除しますか？\nこの操作は元に戻せません。") },
            confirmButton = {
                TextButton(
                    onClick = { homeViewModel.confirmDeletion() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { homeViewModel.cancelDeletion() }) {
                    Text("キャンセル")
                }
            }
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

        AlertDialog(
            onDismissRequest = { homeViewModel.dismissDeleteDoneDialog() },
            title = { Text("削除完了") },
            text = { Text("セクションを削除しました。") },
            confirmButton = {
                TextButton(onClick = { homeViewModel.dismissDeleteDoneDialog() }) {
                    Text("OK")
                }
            }
        )
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
    val deleteButtonWidth = 80.dp // 削除ボタンの幅
    val cardShape = CardDefaults.shape // カードのデフォルトの角丸を取得
    val density = LocalDensity.current // LocalDensityを取得

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Red, shape = cardShape) // 背景を角丸で描画
    ) {
        // 背景の削除ボタン
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

        // 前景のカード
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                // ドラッグ量を現在のオフセットに加算
                                val newOffset = with(density) {
                                    (offsetX.value + dragAmount).coerceIn(
                                        -deleteButtonWidth.toPx() * 1.2f,
                                        0f
                                    )
                                }
                                offsetX.snapTo(newOffset)
                            }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                // ドラッグ終了時のオフセットがボタン幅の半分以上なら、ボタンを表示した位置で固定
                                val threshold = with(density) { -deleteButtonWidth.toPx() / 2 }
                                if (offsetX.value < threshold) {
                                    with(density) { offsetX.animateTo(-deleteButtonWidth.toPx()) }
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
                    }
                    else {
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
                                val miles = meters / 1609.34
                                "距離: %.2f mile".format(miles)
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
                            with(density) { offsetX.animateTo(-deleteButtonWidth.toPx()) }
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
                                            .padding(4.dp) // ボーダーの内側に少しパディングを追加
                                    )                }
            }
        }
    }
}
