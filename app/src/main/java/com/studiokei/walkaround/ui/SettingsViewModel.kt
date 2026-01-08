package com.studiokei.walkaround.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 設定画面の状態管理を行うViewModel。
 */
class SettingsViewModel(private val database: AppDatabase) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // データベースから設定を読み込み、UI状態に反映する
        viewModelScope.launch {
            database.settingsDao().getSettings().collect { settings ->
                _uiState.value = settings?.let {
                    SettingsUiState(
                        stepInterval = it.stepInterval,
                        displayUnit = it.displayUnit,
                        isDarkMode = it.isDarkMode,
                        isNotificationEnabled = it.isNotificationEnabled,
                        volume = it.volume,
                        medianWindowSize = it.medianWindowSize,
                        locationAccuracyLimit = it.locationAccuracyLimit,
                        followSystemTheme = it.followSystemTheme,
                        isVoiceEnabled = it.isVoiceEnabled
                    )
                } ?: SettingsUiState() // 設定が見つからない場合はデフォルト値を使用
            }
        }
    }

    fun updateStepInterval(interval: Int) {
        _uiState.value = _uiState.value.copy(stepInterval = interval)
        saveSettings()
    }

    fun updateDisplayUnit(unit: String) {
        _uiState.value = _uiState.value.copy(displayUnit = unit)
        saveSettings()
    }

    fun updateDarkMode(isDark: Boolean) {
        _uiState.value = _uiState.value.copy(isDarkMode = isDark)
        saveSettings()
    }

    fun updateFollowSystemTheme(follow: Boolean) {
        _uiState.value = _uiState.value.copy(followSystemTheme = follow)
        saveSettings()
    }

    fun updateVoiceEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isVoiceEnabled = enabled)
        saveSettings()
    }

    fun updateNotificationEnabled(isEnabled: Boolean) {
        _uiState.value = _uiState.value.copy(isNotificationEnabled = isEnabled)
        saveSettings()
    }

    fun updateVolume(newVolume: Float) {
        _uiState.value = _uiState.value.copy(volume = newVolume)
        saveSettings()
    }

    fun updateMedianWindowSize(size: Int) {
        _uiState.value = _uiState.value.copy(medianWindowSize = size)
        saveSettings()
    }

    fun updateLocationAccuracyLimit(limit: Float) {
        _uiState.value = _uiState.value.copy(locationAccuracyLimit = limit)
        saveSettings()
    }

    /**
     * 現在のUI状態をデータベースに保存する。
     */
    private fun saveSettings() {
        viewModelScope.launch {
            val currentSettings = _uiState.value
            database.settingsDao().insertSettings(
                Settings(
                    id = 1, // 常にID 1のレコードを更新/作成する
                    stepInterval = currentSettings.stepInterval,
                    displayUnit = currentSettings.displayUnit,
                    isDarkMode = currentSettings.isDarkMode,
                    isNotificationEnabled = currentSettings.isNotificationEnabled,
                    volume = currentSettings.volume,
                    medianWindowSize = currentSettings.medianWindowSize,
                    locationAccuracyLimit = currentSettings.locationAccuracyLimit,
                    followSystemTheme = currentSettings.followSystemTheme,
                    isVoiceEnabled = currentSettings.isVoiceEnabled
                )
            )
        }
    }
}

/**
 * 設定画面のUI状態。
 */
data class SettingsUiState(
    val stepInterval: Int = 500,
    val displayUnit: String = "km",
    val isDarkMode: Boolean = false,
    val isNotificationEnabled: Boolean = true,
    val volume: Float = 0.5f,
    val medianWindowSize: Int = 7,
    val locationAccuracyLimit: Float = 20.0f,
    val followSystemTheme: Boolean = true,
    val isVoiceEnabled: Boolean = true
)
