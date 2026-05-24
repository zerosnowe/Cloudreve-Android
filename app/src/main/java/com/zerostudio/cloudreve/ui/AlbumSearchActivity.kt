package com.zerostudio.cloudreve.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zerostudio.cloudreve.core.common.locale.APP_LANGUAGE_PREFERENCES_NAME
import com.zerostudio.cloudreve.core.common.locale.APP_LANGUAGE_TAG_KEY
import com.zerostudio.cloudreve.core.common.locale.AppLanguage
import com.zerostudio.cloudreve.core.common.locale.AppLanguageController
import com.zerostudio.cloudreve.core.common.locale.createLocalizedContext
import com.zerostudio.cloudreve.core.ui.MiuixCloudreveTheme
import com.zerostudio.cloudreve.core.ui.enableCloudreveImmersiveSystemBars
import com.zerostudio.cloudreve.feature.preview.AlbumSearchRoute
import org.koin.compose.koinInject

fun Context.startAlbumSearchActivity() {
    val intent = Intent(this, AlbumSearchActivity::class.java)
    val activity = findActivity()
    if (activity != null) {
        activity.startActivity(intent)
    } else {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class AlbumSearchActivity : ComponentActivity() {
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
            AlbumSearchApp(
                onBack = ::finish,
                onOpenImage = { file -> startImagePreviewActivity(file) },
            )
        }
    }
}

@Composable
private fun AlbumSearchApp(
    onBack: () -> Unit,
    onOpenImage: (com.zerostudio.cloudreve.core.domain.model.FileNode) -> Unit,
    languageController: AppLanguageController = koinInject(),
) {
    val language = languageController.currentLanguage.collectAsStateWithLifecycle().value
    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, language) {
        baseContext.createLocalizedContext(language)
    }
    val localizedConfiguration = remember(localizedContext) {
        localizedContext.resources.configuration
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
    ) {
        MiuixCloudreveTheme {
            AlbumSearchRoute(
                onBack = onBack,
                onOpenImage = onOpenImage,
            )
        }
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
