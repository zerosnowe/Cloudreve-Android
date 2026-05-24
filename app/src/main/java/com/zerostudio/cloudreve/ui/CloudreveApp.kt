package com.zerostudio.cloudreve.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zerostudio.cloudreve.core.common.locale.AppLanguageController
import com.zerostudio.cloudreve.core.common.locale.createLocalizedContext
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.repository.CloudreveRepository
import com.zerostudio.cloudreve.core.ui.CloudreveDestination
import com.zerostudio.cloudreve.core.ui.CloudreveScaffold
import com.zerostudio.cloudreve.core.ui.MiuixCloudreveTheme
import com.zerostudio.cloudreve.feature.auth.AuthRoute
import com.zerostudio.cloudreve.feature.files.FilesRoute
import com.zerostudio.cloudreve.feature.preview.PreviewRoute
import com.zerostudio.cloudreve.feature.settings.SettingsRoute
import com.zerostudio.cloudreve.feature.share.ShareRoute
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator

@Composable
fun CloudreveApp(
    repository: CloudreveRepository = koinInject(),
    languageController: AppLanguageController = koinInject(),
) {
    val signedInState by produceState<Boolean?>(initialValue = null, repository) {
        repository.activeSession().collect { value = it }
    }
    val signedIn = signedInState == true
    val language by languageController.currentLanguage.collectAsStateWithLifecycle()
    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, language) {
        baseContext.createLocalizedContext(language)
    }
    val localizedConfiguration = remember(localizedContext) {
        localizedContext.resources.configuration
    }
    var destination by remember { mutableStateOf(CloudreveDestination.Files) }
    val activityContext = LocalContext.current

    LaunchedEffect(signedIn) {
        if (!signedIn) destination = CloudreveDestination.Files
    }

    val rootFilesViewModel = if (signedIn && destination == CloudreveDestination.Files) {
        koinViewModel<com.zerostudio.cloudreve.feature.files.FilesViewModel>(
            key = CloudreveUri.Root.value,
            parameters = { parametersOf(CloudreveUri.Root) },
        )
    } else {
        null
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
    ) {
        MiuixCloudreveTheme {
            CloudreveScaffold(
                currentDestination = destination,
                onDestinationSelected = { destination = it },
                showBottomBar = signedIn,
                showTopBar = !signedIn || destination != CloudreveDestination.Preview,
                topBarTitle = if (signedIn) null else stringResource(com.zerostudio.cloudreve.core.ui.R.string.destination_title_sign_in),
                actions = {
                    val filesViewModel = rootFilesViewModel
                    if (signedIn && destination == CloudreveDestination.Files && filesViewModel != null) {
                        FilesTopBarActions(
                            onEnqueueUploads = filesViewModel::enqueueUploads,
                            onEnqueueDirectoryUpload = filesViewModel::enqueueDirectoryUpload,
                            onCreateFolder = filesViewModel::createFolder,
                            onCreateFile = filesViewModel::createFile,
                        )
                    }
                },
            ) { padding ->
                when {
                    signedInState == null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            InfiniteProgressIndicator()
                        }
                    }

                    !signedIn -> AuthRoute(padding)

                    else -> {
                        when (destination) {
                            CloudreveDestination.Files -> FilesRoute(
                                padding = padding,
                                viewModel = rootFilesViewModel
                                    ?: koinViewModel(
                                        key = CloudreveUri.Root.value,
                                        parameters = { parametersOf(CloudreveUri.Root) },
                                    ),
                                onOpenFolder = { folder ->
                                    activityContext.startFolderActivity(uri = folder.uri, title = folder.name)
                                },
                                onOpenImage = { image ->
                                    activityContext.startImagePreviewActivity(image)
                                },
                                onOpenAudio = { audio ->
                                    activityContext.startMusicPlayerActivity(audio)
                                },
                                onOpenFileDetails = { file ->
                                    activityContext.startFileDetailActivity(file)
                                },
                            )

                            CloudreveDestination.Preview -> PreviewRoute(
                                padding = padding,
                                onOpenImage = { image ->
                                    activityContext.startImagePreviewActivity(image)
                                },
                                onSearch = {
                                    activityContext.startAlbumSearchActivity()
                                },
                            )
                            CloudreveDestination.Share -> ShareRoute(padding)
                            CloudreveDestination.Settings -> SettingsRoute(padding)
                        }
                    }
                }
            }
        }
    }
}
