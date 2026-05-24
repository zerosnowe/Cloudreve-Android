package com.zerostudio.cloudreve.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zerostudio.cloudreve.R
import com.zerostudio.cloudreve.core.common.locale.APP_LANGUAGE_PREFERENCES_NAME
import com.zerostudio.cloudreve.core.common.locale.APP_LANGUAGE_TAG_KEY
import com.zerostudio.cloudreve.core.common.locale.AppLanguage
import com.zerostudio.cloudreve.core.common.locale.AppLanguageController
import com.zerostudio.cloudreve.core.common.locale.createLocalizedContext
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.model.FileNode
import com.zerostudio.cloudreve.core.ui.MiuixCloudreveTheme
import com.zerostudio.cloudreve.core.ui.enableCloudreveImmersiveSystemBars
import com.zerostudio.cloudreve.feature.preview.ImagePreviewArgs
import com.zerostudio.cloudreve.feature.preview.ImagePreviewEditPayload
import com.zerostudio.cloudreve.feature.preview.ImagePreviewRoute
import com.zerostudio.cloudreve.feature.preview.ImagePreviewSharePayload
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

fun Context.startImagePreviewActivity(file: FileNode) {
    val intent = ImagePreviewActivity.createIntent(this, file)
    val activity = findActivity()
    if (activity != null) {
        activity.startActivity(intent)
    } else {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class ImagePreviewActivity : ComponentActivity() {
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
        val args = ImagePreviewArgs(
            uri = CloudreveUri.parse(intent.getStringExtra(EXTRA_URI).orEmpty()),
            name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "image" },
            size = intent.getLongExtra(EXTRA_SIZE, 0L),
            mimeType = intent.getStringExtra(EXTRA_MIME),
            updatedAtEpochMillis = intent.getLongExtra(EXTRA_UPDATED_AT, 0L),
            isFavorite = intent.getBooleanExtra(EXTRA_IS_FAVORITE, false),
            thumbnailUrl = intent.getStringExtra(EXTRA_THUMBNAIL_URL),
        )
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
                    ImagePreviewRoute(
                        args = args,
                        onBack = ::finish,
                        onShare = ::shareImage,
                        onEdit = { payload -> startImageEditorActivity(payload) },
                        onDeleted = ::finish,
                    )
                }
            }
        }
    }

    private fun shareImage(payload: ImagePreviewSharePayload) {
        CoroutineScope(Dispatchers.Main).launch {
            runCatching {
                imageUriForShare(payload)
            }.onSuccess { uri ->
                val shareIntent = Intent(Intent.ACTION_SEND)
                    .setType(payload.mimeType?.takeIf { it.isNotBlank() } ?: "image/*")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(
                    Intent.createChooser(
                        shareIntent,
                        getString(com.zerostudio.cloudreve.feature.preview.R.string.image_preview_share),
                    ),
                )
            }.onFailure {
                Toast.makeText(this@ImagePreviewActivity, R.string.image_share_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun imageUriForShare(payload: ImagePreviewSharePayload): android.net.Uri {
        val target = File(payload.filePath)
        check(target.isFile) { "Image preview cache file is missing" }
        return FileProvider.getUriForFile(
            this@ImagePreviewActivity,
            "${packageName}.fileprovider",
            target,
        )
    }

    companion object {
        private const val EXTRA_URI = "com.zerostudio.cloudreve.extra.IMAGE_URI"
        private const val EXTRA_NAME = "com.zerostudio.cloudreve.extra.IMAGE_NAME"
        private const val EXTRA_SIZE = "com.zerostudio.cloudreve.extra.IMAGE_SIZE"
        private const val EXTRA_MIME = "com.zerostudio.cloudreve.extra.IMAGE_MIME"
        private const val EXTRA_UPDATED_AT = "com.zerostudio.cloudreve.extra.IMAGE_UPDATED_AT"
        private const val EXTRA_IS_FAVORITE = "com.zerostudio.cloudreve.extra.IMAGE_IS_FAVORITE"
        private const val EXTRA_THUMBNAIL_URL = "com.zerostudio.cloudreve.extra.IMAGE_THUMBNAIL_URL"
        fun createIntent(context: Context, file: FileNode): Intent =
            Intent(context, ImagePreviewActivity::class.java)
                .putExtra(EXTRA_URI, file.uri.value)
                .putExtra(EXTRA_NAME, file.name)
                .putExtra(EXTRA_SIZE, file.size)
                .putExtra(EXTRA_MIME, file.mimeType)
                .putExtra(EXTRA_UPDATED_AT, file.updatedAtEpochMillis)
                .putExtra(EXTRA_IS_FAVORITE, file.isFavorite)
                .putExtra(EXTRA_THUMBNAIL_URL, file.thumbnailUrl)
    }
}

private fun Context.startImageEditorActivity(payload: ImagePreviewEditPayload) {
    val intent = ImageEditorActivity.createIntent(this, payload)
    val activity = findActivity()
    if (activity != null) {
        activity.startActivity(intent)
    } else {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
