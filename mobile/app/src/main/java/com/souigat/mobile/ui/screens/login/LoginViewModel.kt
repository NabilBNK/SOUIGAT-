package com.souigat.mobile.ui.screens.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.remote.dto.UserProfileDto
import com.souigat.mobile.data.repository.AuthException
import com.souigat.mobile.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Typed UI state honoring the orchestration specs
sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val user: UserProfileDto) : LoginUiState()
    sealed class Error : LoginUiState() {
        object InvalidCredentials : Error()
        object AccountDisabled : Error()
        object NetworkUnavailable : Error()
        data class Unknown(val message: String) : Error()
    }
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    var username by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set

    fun onUsernameChanged(value: String) { username = value }
    fun onPasswordChanged(value: String) { password = value }

    fun resetState() {
        if (_uiState.value is LoginUiState.Error) {
            _uiState.value = LoginUiState.Idle
        }
    }

    fun login() {
        // Local validation before network call
        if (username.isBlank()) {
            _uiState.value = LoginUiState.Error.Unknown("Veuillez saisir votre nom d'utilisateur")
            return
        }
        if (password.length < 8) {
            _uiState.value = LoginUiState.Error.Unknown("Le mot de passe doit faire au moins 8 caractères")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            authRepository.login(username.trim().lowercase(), password)
                .onSuccess { user -> _uiState.value = LoginUiState.Success(user) }
                .onFailure { e ->
                    _uiState.value = when (e) {
                        is AuthException.InvalidCredentials -> LoginUiState.Error.InvalidCredentials
                        is AuthException.AccountDisabled -> LoginUiState.Error.AccountDisabled
                        is AuthException.NetworkUnavailable -> LoginUiState.Error.NetworkUnavailable
                        else -> LoginUiState.Error.Unknown(e.message ?: "Erreur inconnue")
                    }
                }
        }
    }
}
