package com.zerostudio.cloudreve.feature.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerostudio.cloudreve.core.domain.model.SignInCommand
import com.zerostudio.cloudreve.core.domain.repository.CloudreveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val twoFactorCode: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    @param:StringRes val errorRes: Int? = null,
)

class AuthViewModel(
    private val repository: CloudreveRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value, errorMessage = null, errorRes = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, errorMessage = null, errorRes = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, errorMessage = null, errorRes = null) }
    fun onTwoFactorCodeChange(value: String) = _state.update { it.copy(twoFactorCode = value, errorMessage = null, errorRes = null) }

    fun signIn() {
        val snapshot = _state.value
        if (snapshot.serverUrl.isBlank() || snapshot.username.isBlank() || snapshot.password.isBlank()) {
            _state.update { it.copy(errorRes = R.string.auth_error_required_fields, errorMessage = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, errorRes = null) }
            runCatching {
                repository.signIn(
                    SignInCommand(
                        baseUrl = snapshot.serverUrl,
                        username = snapshot.username,
                        password = snapshot.password,
                        twoFactorCode = snapshot.twoFactorCode.ifBlank { null },
                    ),
                )
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        errorMessage = error.message,
                        errorRes = if (error.message == null) R.string.auth_error_sign_in_failed else null,
                    )
                }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }
}
