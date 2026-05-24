package com.zerostudio.cloudreve.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.zerostudio.cloudreve.feature.preview.ImageEditorScreen
import com.zerostudio.cloudreve.feature.preview.ImagePreviewEditPayload
import org.koin.compose.koinInject

class ImageEditorActivity : ComponentActivity() {
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
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH).orEmpty()
        val title = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "image" }
        setContent {
            val languageController: AppLanguageController = koinInject()
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
                    ImageEditorScreen(
                        filePath = filePath,
                        title = title,
                        onBack = ::finish,
                        onSaved = {
                            Toast.makeText(
                                this@ImageEditorActivity,
                                com.zerostudio.cloudreve.feature.preview.R.string.image_editor_saved,
                                Toast.LENGTH_SHORT,
                            ).show()
                            finish()
                        },
                        onSaveFailed = {
                            Toast.makeText(
                                this@ImageEditorActivity,
                                com.zerostudio.cloudreve.feature.preview.R.string.image_editor_save_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_FILE_PATH = "com.zerostudio.cloudreve.extra.EDITOR_FILE_PATH"
        private const val EXTRA_NAME = "com.zerostudio.cloudreve.extra.EDITOR_NAME"
        private const val EXTRA_MIME = "com.zerostudio.cloudreve.extra.EDITOR_MIME"

        fun createIntent(context: Context, payload: ImagePreviewEditPayload): Intent =
            Intent(context, ImageEditorActivity::class.java)
                .putExtra(EXTRA_FILE_PATH, payload.filePath)
                .putExtra(EXTRA_NAME, payload.name)
                .putExtra(EXTRA_MIME, payload.mimeType)
    }
}
