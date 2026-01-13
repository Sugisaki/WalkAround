package com.studiokei.walkaround.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studiokei.walkaround.BuildConfig
import com.studiokei.walkaround.data.database.AppDatabase

/**
 * アプリの設定画面。
 * 各種設定項目の表示と更新を行います。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(AppDatabase.getDatabase(context))
            }
        }
    )
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    // スクロール状態を保持
    val scrollState = rememberScrollState()

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // 画面全体をスクロール可能にする
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text(text = "アプリ設定", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            // 1. 表示モード（テーマ）設定
            SettingCard {
                Column {
                    // システム設定に従うスイッチ
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("システムのテーマ設定に従う:")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = uiState.followSystemTheme,
                            onCheckedChange = { settingsViewModel.updateFollowSystemTheme(it) }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    // 個別のダークモードスイッチ
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // システム設定に従う場合はグレーアウトして表示
                        Text(
                            text = "ダークモード:",
                            color = if (uiState.followSystemTheme) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) 
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = uiState.isDarkMode,
                            onCheckedChange = { settingsViewModel.updateDarkMode(it) },
                            enabled = !uiState.followSystemTheme // システムに従う場合は無効化
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 2. 音声による案内設定
            SettingCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("音声による住所案内を有効にする:")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = uiState.isVoiceEnabled,
                        onCheckedChange = { settingsViewModel.updateVoiceEnabled(it) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 3. 音量設定（常時表示へ移動）
            SettingCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("案内音量: ${"%.1f".format(uiState.volume)}")
                    Slider(
                        value = uiState.volume,
                        onValueChange = { settingsViewModel.updateVolume(it) },
                        valueRange = 0f..1f,
                        steps = 9 // 0.0, 0.1, ..., 1.0
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 4. 位置情報の許容精度設定
            SettingCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("位置情報の許容精度 (m): ${"%.1f".format(uiState.locationAccuracyLimit)}")
                    Slider(
                        value = uiState.locationAccuracyLimit,
                        onValueChange = { settingsViewModel.updateLocationAccuracyLimit(it) },
                        valueRange = 5f..100f,
                        steps = 18 // 5.0, 10.0, ..., 100.0 (5m刻み)
                    )
                    Text(
                        text = "この値より精度の低い（誤差が大きい）データは地図や住所取得に使用されません。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 5. 距離表示単位設定
            SettingCard {
                Column(modifier = Modifier.selectableGroup()) {
                    Text("距離表示単位:")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .selectable(
                                    selected = (uiState.displayUnit == "km"),
                                    onClick = { settingsViewModel.updateDisplayUnit("km") },
                                    role = Role.RadioButton
                                )
                                .weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (uiState.displayUnit == "km"),
                                onClick = null
                            )
                            Text(text = "km")
                        }
                        Row(
                            Modifier
                                .selectable(
                                    selected = (uiState.displayUnit == "mile"),
                                    onClick = { settingsViewModel.updateDisplayUnit("mile") },
                                    role = Role.RadioButton
                                )
                                .weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (uiState.displayUnit == "mile"),
                                onClick = null
                            )
                            Text(text = "mile")
                        }
                    }
                }
            }

            // --- プライバシーポリシー ---
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "その他",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val uriHandler = LocalUriHandler.current
            val privacyPolicyUrl = "https://studio-kei.info/android/walkaround/privacy.html"

            SettingCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { uriHandler.openUri(privacyPolicyUrl) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "プライバシーポリシー",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "プライバシーポリシーを開く"
                    )
                }
            }


            // 6. デバッグモード時のみ表示する設定項目（最下部へ移動）
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                
                Text(
                    text = "--- デバッグ設定 ---",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 歩数記録間隔の設定
                SettingCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("歩数間隔で記録:")
                        Spacer(modifier = Modifier.weight(1f))
                        TextField(
                            value = uiState.stepInterval.toString(),
                            onValueChange = { newValue ->
                                val interval = newValue.toIntOrNull() ?: 0
                                settingsViewModel.updateStepInterval(interval)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.5f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // 軌跡平滑化（メディアンフィルタ）設定
                SettingCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("軌跡平滑化 (メディアンフィルタ窓サイズ):")
                        Spacer(modifier = Modifier.height(8.dp))

                        val windowSizes = listOf(0, 3, 5, 7, 9, 11, 13, 15, 17, 19)
                        var expanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = if (uiState.medianWindowSize == 0) "OFF (0)" else uiState.medianWindowSize.toString(),
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                windowSizes.forEach { size ->
                                    DropdownMenuItem(
                                        text = { Text(if (size == 0) "OFF (0)" else size.toString()) },
                                        onClick = {
                                            settingsViewModel.updateMedianWindowSize(size)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // 通知有効化設定
                SettingCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("通知を有効にする:")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = uiState.isNotificationEnabled,
                            onCheckedChange = { settingsViewModel.updateNotificationEnabled(it) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 設定項目を囲むカードコンポーネント。
 */
@Composable
fun SettingCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
