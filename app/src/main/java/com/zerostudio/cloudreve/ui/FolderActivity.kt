package com.zerostudio.cloudreve.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.zerostudio.cloudreve.feature.files.FilesRoute
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun Context.startFolderActivity(uri: CloudreveUri, title: String) {
    val intent = FolderActivity.createIntent(this, uri, title)
    val activity = findActivity()
    if (activity != null) {
        activity.startActivity(intent)
    } else {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class FolderActivity : ComponentActivity() {
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
        val folderUri = CloudreveUri.parse(intent.getStringExtra(EXTRA_URI).orEmpty())
        val folderTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
            .ifBlank { folderUri.value.substringAfterLast('/').ifBlank { "Cloudreve" } }
        setContent {
            FolderApp(
                folderUri = folderUri,
                title = folderTitle,
                onBack = ::finish,
            )
        }
    }

    companion object {
        private const val EXTRA_URI = "com.zerostudio.cloudreve.extra.FOLDER_URI"
        private const val EXTRA_TITLE = "com.zerostudio.cloudreve.extra.FOLDER_TITLE"

        fun createIntent(
            context: Context,
            uri: CloudreveUri,
            title: String,
        ): Intent = Intent(context, FolderActivity::class.java)
            .putExtra(EXTRA_URI, uri.value)
            .putExtra(EXTRA_TITLE, title)
    }
}

@Composable
private fun FolderApp(
    folderUri: CloudreveUri,
    title: String,
    onBack: () -> Unit,
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
            FolderScreen(
                folderUri = folderUri,
                title = title,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun FolderScreen(
    folderUri: CloudreveUri,
    title: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = title,
                largeTitle = title,
                titlePadding = 16.dp,
                navigationIconPadding = 16.dp,
                actionIconPadding = 16.dp,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.transfers_back),
                        )
                    }
                },
            )
        },
        containerColor = MiuixTheme.colorScheme.background,
    ) { padding: PaddingValues ->
        FilesRoute(
            padding = padding,
            initialUri = folderUri,
            onOpenFolder = { folder: FileNode ->
                context.startFolderActivity(uri = folder.uri, title = folder.name)
            },
            onOpenImage = { image: FileNode ->
                context.startImagePreviewActivity(image)
            },
            onOpenAudio = { audio: FileNode ->
                context.startMusicPlayerActivity(audio)
            },
            onOpenFileDetails = { file: FileNode ->
                context.startFileDetailActivity(file)
            },
        )
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
