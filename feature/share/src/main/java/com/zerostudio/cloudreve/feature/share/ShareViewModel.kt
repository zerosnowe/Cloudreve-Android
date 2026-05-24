package com.zerostudio.cloudreve.feature.share

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.repository.CloudreveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareUiState(
    val uri: String = CloudreveUri.Root.value,
    val link: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    @param:StringRes val errorRes: Int? = null,
)

class ShareViewModel(
    private val repository: CloudreveRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    fun onUriChange(value: String) = _state.update { it.copy(uri = value, errorMessage = null, errorRes = null) }

    fun createShare() {
        val uri = _state.value.uri
        if (uri.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, errorRes = null) }
            runCatching { repository.createShare(listOf(CloudreveUri.parse(uri))) }
                .onSuccess { share -> _state.update { it.copy(link = share.url) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            errorMessage = error.message,
                            errorRes = if (error.message == null) R.string.share_error_failed else null,
                        )
                    }
                }
            _state.update { it.copy(isLoading = false) }
        }
    }
}
