package com.zerostudio.cloudreve.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerostudio.cloudreve.core.common.locale.AppLanguage
import com.zerostudio.cloudreve.core.common.locale.AppLanguageController
import com.zerostudio.cloudreve.core.domain.repository.CloudreveRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val language: AppLanguage = AppLanguage.Default,
)

class SettingsViewModel(
    private val repository: CloudreveRepository,
    private val languageController: AppLanguageController,
) : ViewModel() {
    val state = languageController.currentLanguage
        .map { language -> SettingsUiState(language = language) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun setLanguage(language: AppLanguage) {
        languageController.setLanguage(language)
    }

    fun signOut() {
        viewModelScope.launch { repository.signOut() }
    }
}
