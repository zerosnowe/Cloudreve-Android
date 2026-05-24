package com.zerostudio.cloudreve

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zerostudio.cloudreve.core.common.locale.APP_LANGUAGE_PREFERENCES_NAME
import com.zerostudio.cloudreve.core.common.locale.APP_LANGUAGE_TAG_KEY
import com.zerostudio.cloudreve.core.common.locale.AppLanguage
import com.zerostudio.cloudreve.core.common.locale.createLocalizedContext
import com.zerostudio.cloudreve.core.ui.enableCloudreveImmersiveSystemBars
import com.zerostudio.cloudreve.ui.CloudreveApp

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val preferences = newBase.getSharedPreferences(
            APP_LANGUAGE_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val language = AppLanguage.fromLanguageTag(preferences.getString(APP_LANGUAGE_TAG_KEY, null))
        super.attachBaseContext(newBase.createLocalizedContext(language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableCloudreveImmersiveSystemBars()
        setContent {
            CloudreveApp()
        }
    }
}
