package com.comp.traap.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.comp.traap.data.repository.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
        val isServiceRunning: Boolean = false,
        val isButtonVisible: Boolean = true,
        val tapCount: Int = 0,
        val statusMessage: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val locationRepository = LocationRepository(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** Start location tracking service */
    fun startLocationService() {
        _uiState.value =
                _uiState.value.copy(
                        isServiceRunning = true,
                        isButtonVisible = false,
                        statusMessage = "hi"
                )
    }

    /** Stop location tracking service */
    fun stopLocationService() {
        _uiState.value = _uiState.value.copy(isServiceRunning = false, statusMessage = "he")

        // Clear status after 3 seconds
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            clearStatus()
        }
    }

    /** Restore service state when app is reopened */
    fun restoreServiceState(isRunning: Boolean) {
        _uiState.value =
                _uiState.value.copy(
                        isServiceRunning = isRunning,
                        isButtonVisible = !isRunning // Hide button if service is running
                )
    }

    /** Handle screen tap when button is hidden */
    fun onScreenTap() {
        if (!_uiState.value.isButtonVisible) {
            val newTapCount = _uiState.value.tapCount + 1

            if (newTapCount >= 10) {
                // Show button and reset counter
                _uiState.value =
                        _uiState.value.copy(
                                isButtonVisible = true,
                                tapCount = 0,
                                statusMessage = ""
                        )
            } else {
                _uiState.value = _uiState.value.copy(tapCount = newTapCount)

                // Reset counter after 1 second
                viewModelScope.launch {
                    kotlinx.coroutines.delay(1000)
                    if (_uiState.value.tapCount == newTapCount) {
                        _uiState.value = _uiState.value.copy(tapCount = 0)
                    }
                }
            }
        }
    }

    /** Hide power button */
    fun hideButton() {
        _uiState.value = _uiState.value.copy(isButtonVisible = false)
    }

    /** Update status message */
    fun updateStatus(message: String) {
        _uiState.value = _uiState.value.copy(statusMessage = message)

        // Auto-clear after 3 seconds
        if (message.isNotEmpty()) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(3000)
                clearStatus()
            }
        }
    }

    /** Clear status message */
    private fun clearStatus() {
        if (_uiState.value.statusMessage.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(statusMessage = "")
        }
    }
}
