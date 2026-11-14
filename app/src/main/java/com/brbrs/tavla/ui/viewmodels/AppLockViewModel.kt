package com.brbrs.tavla.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.tavla.auth.AuthRepository
import com.brbrs.tavla.biometric.BiometricHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppLockUiState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val appLockEnabled: Boolean = false,
    val unlocked: Boolean = false,
)

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    val biometricHelper: BiometricHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                authRepository.session,
                authRepository.appLockEnabled,
                authRepository.storageMode,
            ) { session, appLock, mode ->
                val loggedIn = session != null || mode == "local"
                loggedIn to appLock
            }.collect { (loggedIn, appLock) ->
                _uiState.update { it.copy(isLoading = false, isLoggedIn = loggedIn, appLockEnabled = appLock) }
            }
        }
    }

    fun onUnlocked() {
        _uiState.update { it.copy(unlocked = true) }
    }
}
