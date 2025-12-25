package com.studiokei.walkaround.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studiokei.walkaround.data.database.AppDatabase

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

    Scaffold(modifier = modifier) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Text(text = "アプリ設定", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            // Step Interval Setting
            SettingCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("歩数記録間隔 (N歩ごと):")
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

            // Median Filter Window Size Setting
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

            // Display Unit Setting
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
                                onClick = null // null recommended for accessibility with screenreaders
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
                                onClick = null // null recommended for accessibility with screenreaders
                            )
                            Text(text = "mile")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Dark Mode Setting
            SettingCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ダークモード:")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = uiState.isDarkMode,
                        onCheckedChange = { settingsViewModel.updateDarkMode(it) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Notification Enabled Setting
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
            Spacer(modifier = Modifier.height(8.dp))

            // Volume Setting
            SettingCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("音量: ${"%.1f".format(uiState.volume)}")
                    Slider(
                        value = uiState.volume,
                        onValueChange = { settingsViewModel.updateVolume(it) },
                        valueRange = 0f..1f,
                        steps = 9 // 0.0, 0.1, ..., 1.0
                    )
                }
            }
        }
    }
}

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
