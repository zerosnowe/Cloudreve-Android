package com.zerostudio.cloudreve.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zerostudio.cloudreve.core.common.locale.APP_LANGUAGE_PREFERENCES_NAME
import com.zerostudio.cloudreve.core.common.locale.APP_LANGUAGE_TAG_KEY
import com.zerostudio.cloudreve.core.common.locale.AppLanguage
import com.zerostudio.cloudreve.core.common.locale.AppLanguageController
import com.zerostudio.cloudreve.core.common.locale.createLocalizedContext
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.model.FileNode
import com.zerostudio.cloudreve.core.domain.model.FileType
import com.zerostudio.cloudreve.core.ui.MiuixCloudreveTheme
import com.zerostudio.cloudreve.core.ui.enableCloudreveImmersiveSystemBars
import com.zerostudio.cloudreve.feature.preview.CloudrevePlaybackService
import com.zerostudio.cloudreve.feature.preview.MusicPlayerArgs
import com.zerostudio.cloudreve.feature.preview.MusicPlayerRoute
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Scaffold

fun Context.startMusicPlayerActivity(file: FileNode) {
    val intent = MusicPlayerActivity.createIntent(this, file)
    val activity = findActivity()
    if (activity != null) {
        activity.startActivity(intent)
    } else {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class MusicPlayerActivity : ComponentActivity() {
    private var currentArgs by mutableStateOf<MusicPlayerArgs?>(null)

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
        startService(Intent(this, CloudrevePlaybackService::class.java))
        requestPostNotificationsIfNeeded()
        currentArgs = intent.toMusicPlayerArgs()
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
                LocalActivityResultRegistryOwner provides this@MusicPlayerActivity,
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration,
            ) {
                MiuixCloudreveTheme {
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        currentArgs?.let { args ->
                            key(args.uri.value) {
                                MusicPlayerRoute(
                                    args = args,
                                    onBack = ::finish,
                                    onOpenDetails = {
                                        startFileDetailActivity(args.toFileNode())
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentArgs = intent.toMusicPlayerArgs()
    }

    private fun requestPostNotificationsIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS,
            )
        }
    }

    private fun Intent.toMusicPlayerArgs(): MusicPlayerArgs = MusicPlayerArgs(
        id = getStringExtra(EXTRA_ID).orEmpty(),
        uri = CloudreveUri.parse(getStringExtra(EXTRA_URI).orEmpty()),
        parentUri = CloudreveUri.parse(getStringExtra(EXTRA_PARENT_URI).orEmpty()),
        name = getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "audio" },
        size = getLongExtra(EXTRA_SIZE, 0L),
        mimeType = getStringExtra(EXTRA_MIME),
        updatedAtEpochMillis = getLongExtra(EXTRA_UPDATED_AT, 0L),
    )

    private fun MusicPlayerArgs.toFileNode(): FileNode = FileNode(
        id = id,
        name = name,
        uri = uri,
        parentUri = parentUri,
        type = FileType.Audio,
        size = size,
        mimeType = mimeType,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    companion object {
        private const val EXTRA_ID = "com.zerostudio.cloudreve.extra.AUDIO_ID"
        private const val EXTRA_URI = "com.zerostudio.cloudreve.extra.AUDIO_URI"
        private const val EXTRA_PARENT_URI = "com.zerostudio.cloudreve.extra.AUDIO_PARENT_URI"
        private const val EXTRA_NAME = "com.zerostudio.cloudreve.extra.AUDIO_NAME"
        private const val EXTRA_SIZE = "com.zerostudio.cloudreve.extra.AUDIO_SIZE"
        private const val EXTRA_MIME = "com.zerostudio.cloudreve.extra.AUDIO_MIME"
        private const val EXTRA_UPDATED_AT = "com.zerostudio.cloudreve.extra.AUDIO_UPDATED_AT"
        private const val REQUEST_POST_NOTIFICATIONS = 5201

        fun createIntent(context: Context, file: FileNode): Intent =
            Intent(context, MusicPlayerActivity::class.java)
                .putExtra(EXTRA_ID, file.id)
                .putExtra(EXTRA_URI, file.uri.value)
                .putExtra(EXTRA_PARENT_URI, file.parentUri.value)
                .putExtra(EXTRA_NAME, file.name)
                .putExtra(EXTRA_SIZE, file.size)
                .putExtra(EXTRA_MIME, file.mimeType)
                .putExtra(EXTRA_UPDATED_AT, file.updatedAtEpochMillis)
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
