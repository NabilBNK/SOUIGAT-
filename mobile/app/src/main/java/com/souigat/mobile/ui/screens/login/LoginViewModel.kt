package com.souigat.mobile.ui.screens.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.souigat.mobile.data.repository.AuthException
import com.souigat.mobile.domain.model.UserProfile
import com.souigat.mobile.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val user: UserProfile) : LoginUiState()

    sealed class Error : LoginUiState() {
        object InvalidCredentials : Error()
        object AccountDisabled : Error()
        object NetworkUnavailable : Error()
        object TooManyAttempts : Error()
        data class Unknown(val message: String) : Error()
    }
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    var phone by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set

    fun onPhoneChanged(value: String) {
        phone = value
    }

    fun onPasswordChanged(value: String) {
        password = value
    }

    fun resetState() {
        if (_uiState.value is LoginUiState.Error) {
            _uiState.value = LoginUiState.Idle
        }
    }

    fun login() {
        val phoneRegex = Regex("^0[5-79][0-9]{8}$")
        if (!phoneRegex.matches(phone)) {
            _uiState.value = LoginUiState.Error.Unknown("Format invalide. Ex: 0661234567")
            return
        }
        if (password.length < 8) {
            _uiState.value = LoginUiState.Error.Unknown("Le mot de passe doit faire au moins 8 caracteres")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            authRepository.login(phone.trim(), password)
                .onSuccess { user ->
                    _uiState.value = LoginUiState.Success(user)
                }
                .onFailure { error ->
                    Timber.e(error, "Login failed")
                    _uiState.value = when (error) {
                        is AuthException.InvalidCredentials -> LoginUiState.Error.InvalidCredentials
                        is AuthException.AccountDisabled -> LoginUiState.Error.AccountDisabled
                        is AuthException.NetworkUnavailable -> LoginUiState.Error.NetworkUnavailable
                        is AuthException.TooManyAttempts -> LoginUiState.Error.TooManyAttempts
                        else -> LoginUiState.Error.Unknown(error.message ?: "Erreur inconnue")
                    }
                }
        }
    }
}
