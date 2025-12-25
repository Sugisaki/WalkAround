package com.studiokei.walkaround.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studiokei.walkaround.data.database.AppDatabase
import com.studiokei.walkaround.data.model.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val database: AppDatabase) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            database.settingsDao().getSettings().collect { settings ->
                _uiState.value = settings?.let {
                    SettingsUiState(
                        stepInterval = it.stepInterval,
                        displayUnit = it.displayUnit,
                        isDarkMode = it.isDarkMode,
                        isNotificationEnabled = it.isNotificationEnabled,
                        volume = it.volume,
                        medianWindowSize = it.medianWindowSize
                    )
                } ?: SettingsUiState() // Default values if no settings found
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

    private fun saveSettings() {
        viewModelScope.launch {
            val currentSettings = _uiState.value
            database.settingsDao().insertSettings(
                Settings(
                    stepInterval = currentSettings.stepInterval,
                    displayUnit = currentSettings.displayUnit,
                    isDarkMode = currentSettings.isDarkMode,
                    isNotificationEnabled = currentSettings.isNotificationEnabled,
                    volume = currentSettings.volume,
                    medianWindowSize = currentSettings.medianWindowSize
                )
            )
        }
    }
}

data class SettingsUiState(
    val stepInterval: Int = 500,
    val displayUnit: String = "km",
    val isDarkMode: Boolean = false,
    val isNotificationEnabled: Boolean = true,
    val volume: Float = 0.5f,
    val medianWindowSize: Int = 7
)
