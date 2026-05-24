package com.zerostudio.cloudreve.core.common.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val APP_LANGUAGE_PREFERENCES_NAME = "cloudreve_app_language"
const val APP_LANGUAGE_TAG_KEY = "language_tag"

enum class AppLanguage(val languageTag: String) {
    SimplifiedChinese("zh-CN"),
    English("en");

    companion object {
        val Default = SimplifiedChinese

        fun fromLanguageTag(languageTag: String?): AppLanguage =
            entries.firstOrNull { it.languageTag == languageTag } ?: Default
    }
}

interface AppLanguageController {
    val currentLanguage: StateFlow<AppLanguage>

    fun setLanguage(language: AppLanguage)
}

class DefaultAppLanguageController(context: Context) : AppLanguageController {
    private val preferences = context.applicationContext.getSharedPreferences(
        APP_LANGUAGE_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private val _currentLanguage = MutableStateFlow(
        AppLanguage.fromLanguageTag(preferences.getString(APP_LANGUAGE_TAG_KEY, null)),
    )
    override val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    override fun setLanguage(language: AppLanguage) {
        if (_currentLanguage.value == language) return
        preferences.edit().putString(APP_LANGUAGE_TAG_KEY, language.languageTag).apply()
        _currentLanguage.value = language
    }
}

fun Context.createLocalizedContext(language: AppLanguage): Context {
    val locales = LocaleList.forLanguageTags(language.languageTag)
    val configuration = Configuration(resources.configuration)
    configuration.setLocales(locales)
    locales.get(0)?.let(configuration::setLayoutDirection)
    return createConfigurationContext(configuration)
}
