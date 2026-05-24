package com.zerostudio.cloudreve.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zerostudio.cloudreve.R
import com.zerostudio.cloudreve.core.common.locale.APP_LANGUAGE_PREFERENCES_NAME
import com.zerostudio.cloudreve.core.common.locale.APP_LANGUAGE_TAG_KEY
import com.zerostudio.cloudreve.core.common.locale.AppLanguage
import com.zerostudio.cloudreve.core.common.locale.AppLanguageController
import com.zerostudio.cloudreve.core.common.locale.createLocalizedContext
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.model.FileDetails
import com.zerostudio.cloudreve.core.domain.model.FileNode
import com.zerostudio.cloudreve.core.domain.model.FileType
import com.zerostudio.cloudreve.core.domain.repository.CloudreveRepository
import com.zerostudio.cloudreve.core.transfer.TransferScheduler
import com.zerostudio.cloudreve.core.ui.FileOverflowMenu
import com.zerostudio.cloudreve.core.ui.FileTypeVisual
import com.zerostudio.cloudreve.core.ui.MiuixCloudreveTheme
import com.zerostudio.cloudreve.core.ui.RenameFileBottomSheet
import com.zerostudio.cloudreve.core.ui.cloudreveUriDisplayName
import com.zerostudio.cloudreve.core.ui.enableCloudreveImmersiveSystemBars
import com.zerostudio.cloudreve.core.ui.formatCloudreveBytesWithRaw
import com.zerostudio.cloudreve.core.ui.formatCloudreveFileSize
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun Context.startFileDetailActivity(file: FileNode) {
    val intent = FileDetailActivity.createIntent(this, file)
    val activity = findActivity()
    if (activity != null) {
        activity.startActivity(intent)
    } else {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class FileDetailActivity : ComponentActivity() {
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
        val file = FileNode(
            id = intent.getStringExtra(EXTRA_ID).orEmpty(),
            name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "file" },
            uri = CloudreveUri.parse(intent.getStringExtra(EXTRA_URI).orEmpty()),
            parentUri = CloudreveUri.parse(intent.getStringExtra(EXTRA_PARENT_URI).orEmpty()),
            type = intent.getStringExtra(EXTRA_TYPE)?.let { runCatching { FileType.valueOf(it) }.getOrNull() }
                ?: FileType.Unknown,
            size = intent.getLongExtra(EXTRA_SIZE, 0L),
            mimeType = intent.getStringExtra(EXTRA_MIME),
            updatedAtEpochMillis = intent.getLongExtra(EXTRA_UPDATED_AT, 0L),
            isFavorite = intent.getBooleanExtra(EXTRA_IS_FAVORITE, false),
        )
        setContent {
            FileDetailApp(
                initialFile = file,
                onBack = ::finish,
            )
        }
    }

    companion object {
        private const val EXTRA_ID = "com.zerostudio.cloudreve.extra.FILE_ID"
        private const val EXTRA_NAME = "com.zerostudio.cloudreve.extra.FILE_NAME"
        private const val EXTRA_URI = "com.zerostudio.cloudreve.extra.FILE_URI"
        private const val EXTRA_PARENT_URI = "com.zerostudio.cloudreve.extra.FILE_PARENT_URI"
        private const val EXTRA_TYPE = "com.zerostudio.cloudreve.extra.FILE_TYPE"
        private const val EXTRA_SIZE = "com.zerostudio.cloudreve.extra.FILE_SIZE"
        private const val EXTRA_MIME = "com.zerostudio.cloudreve.extra.FILE_MIME"
        private const val EXTRA_UPDATED_AT = "com.zerostudio.cloudreve.extra.FILE_UPDATED_AT"
        private const val EXTRA_IS_FAVORITE = "com.zerostudio.cloudreve.extra.FILE_IS_FAVORITE"

        fun createIntent(
            context: Context,
            file: FileNode,
        ): Intent = Intent(context, FileDetailActivity::class.java)
            .putExtra(EXTRA_ID, file.id)
            .putExtra(EXTRA_NAME, file.name)
            .putExtra(EXTRA_URI, file.uri.value)
            .putExtra(EXTRA_PARENT_URI, file.parentUri.value)
            .putExtra(EXTRA_TYPE, file.type.name)
            .putExtra(EXTRA_SIZE, file.size)
            .putExtra(EXTRA_MIME, file.mimeType)
            .putExtra(EXTRA_UPDATED_AT, file.updatedAtEpochMillis)
            .putExtra(EXTRA_IS_FAVORITE, file.isFavorite)
    }
}

@Composable
private fun FileDetailApp(
    initialFile: FileNode,
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
            FileDetailScreen(
                initialFile = initialFile,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun FileDetailScreen(
    initialFile: FileNode,
    onBack: () -> Unit,
    repository: CloudreveRepository = koinInject(),
    transferScheduler: TransferScheduler = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentFile by remember(initialFile) { mutableStateOf(initialFile) }
    var details by remember(initialFile) { mutableStateOf<FileDetails?>(null) }
    var thumbnailUrl by remember(initialFile) { mutableStateOf<String?>(null) }
    var errorMessage by remember(initialFile) { mutableStateOf<String?>(null) }
    var isLoading by remember(initialFile) { mutableStateOf(true) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showRenameSheet by remember { mutableStateOf(false) }
    var reloadVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentFile.uri, reloadVersion) {
        isLoading = true
        errorMessage = null
        thumbnailUrl = null
        details = runCatching { repository.getFileDetails(currentFile.uri) }
            .onFailure { error ->
                errorMessage = error.message?.takeIf(String::isNotBlank)
                    ?: context.getString(R.string.file_details_load_failed)
            }
            .getOrNull()
        if (currentFile.supportsThumbnail) {
            thumbnailUrl = runCatching { repository.createThumbnailUrl(currentFile.uri) }.getOrNull()
        }
        isLoading = false
    }

    fun reportError(error: Throwable, fallbackRes: Int) {
        errorMessage = error.message?.takeIf(String::isNotBlank) ?: context.getString(fallbackRes)
    }

    fun enqueueDownload() {
        scope.launch {
            runCatching { transferScheduler.enqueueDownload(currentFile) }
                .onFailure { error -> reportError(error, R.string.file_details_download_failed) }
        }
    }

    fun shareFile() {
        scope.launch {
            runCatching { repository.createShare(listOf(currentFile.uri)).url }
                .onSuccess { shareUrl ->
                    val shareIntent = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, shareUrl)
                    context.startActivity(Intent.createChooser(shareIntent, null))
                }
                .onFailure { error -> reportError(error, R.string.file_details_share_failed) }
        }
    }

    fun openWith() {
        scope.launch {
            runCatching { repository.createDownloadUrl(currentFile.uri) }
                .onSuccess { downloadUrl ->
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                .onFailure { error -> reportError(error, R.string.file_details_open_failed) }
        }
    }

    fun copyToCurrentDirectory() {
        scope.launch {
            runCatching { repository.copy(listOf(currentFile.uri), currentFile.parentUri) }
                .onFailure { error -> reportError(error, R.string.file_details_copy_failed) }
        }
    }

    fun renameFile(newName: String) {
        val sourceFile = currentFile
        val renamedUri = sourceFile.parentUri.child(newName)
        scope.launch {
            runCatching { repository.rename(sourceFile.uri, newName) }
                .onSuccess {
                    currentFile = sourceFile.copy(name = newName, uri = renamedUri)
                    details = details?.copy(name = newName, uri = renamedUri)
                    reloadVersion += 1
                }
                .onFailure { error -> reportError(error, R.string.file_details_rename_failed) }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.file_details_title),
                color = MiuixTheme.colorScheme.background,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.transfers_back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.More,
                                contentDescription = stringResource(com.zerostudio.cloudreve.core.ui.R.string.file_action_open_with),
                            )
                        }
                        FileOverflowMenu(
                            show = showMoreMenu,
                            onDismiss = { showMoreMenu = false },
                            onOpenWith = ::openWith,
                            onDownload = ::enqueueDownload,
                            onShare = ::shareFile,
                            onRename = { showRenameSheet = true },
                            onCopy = ::copyToCurrentDirectory,
                        )
                    }
                },
                titlePadding = 16.dp,
                navigationIconPadding = 16.dp,
                actionIconPadding = 16.dp,
            )
        },
        containerColor = MiuixTheme.colorScheme.background,
    ) { padding: PaddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                FileTypeVisual(
                    file = currentFile,
                    thumbnailUrl = thumbnailUrl,
                    boxSize = 84.dp,
                    imageSize = 72.dp,
                )
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = currentFile.name,
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = formatCloudreveFileSize(details?.size ?: currentFile.size),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = ::enqueueDownload) {
                        Text(stringResource(com.zerostudio.cloudreve.core.ui.R.string.file_action_download))
                    }
                }
            }

            errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (isLoading && details == null) {
                item {
                    InfiniteProgressIndicator()
                }
            }

            item {
                FileDetailSection(title = stringResource(R.string.file_details_section_basic)) {
                    DetailInfoRow(
                        label = stringResource(R.string.file_details_type),
                        value = (details ?: currentFile).displayTypeLabel(context),
                    )
                    DetailInfoRow(label = stringResource(R.string.file_details_directory)) {
                        Card(
                            cornerRadius = 18.dp,
                            colors = CardDefaults.defaultColors(
                                color = MiuixTheme.colorScheme.surfaceContainer,
                                contentColor = MiuixTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Regular.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentFile.parentDisplayName(context),
                                    style = MiuixTheme.textStyles.body2,
                                )
                            }
                        }
                    }
                    DetailInfoRow(
                        label = stringResource(R.string.file_details_size),
                        value = formatCloudreveBytesWithRaw(details?.size ?: currentFile.size),
                    )
                    DetailInfoRow(
                        label = stringResource(R.string.file_details_storage_used),
                        value = formatCloudreveBytesWithRaw(details?.storageUsed ?: details?.size ?: currentFile.size),
                    )
                    DetailInfoRow(
                        label = stringResource(R.string.file_details_created_at),
                        value = formatEpochMillis(details?.createdAtEpochMillis ?: 0L),
                    )
                    DetailInfoRow(
                        label = stringResource(R.string.file_details_updated_at),
                        value = formatEpochMillis(details?.updatedAtEpochMillis ?: currentFile.updatedAtEpochMillis),
                    )
                }
            }

            if (!details?.metadata.isNullOrEmpty()) {
                item {
                    FileDetailSection(title = stringResource(R.string.file_details_section_metadata)) {
                        details?.metadata
                            ?.filterValues { it.isNotBlank() }
                            ?.forEach { (key, value) ->
                                DetailInfoRow(label = key, value = value)
                            }
                    }
                }
            }
        }
    }

    RenameFileBottomSheet(
        show = showRenameSheet,
        initialValue = currentFile.name,
        onDismiss = { showRenameSheet = false },
        onConfirm = { newName ->
            showRenameSheet = false
            renameFile(newName)
        },
    )
}

@Composable
private fun FileDetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title4,
            fontWeight = FontWeight.Bold,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
        Text(
            text = value.ifBlank { "-" },
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
        content()
    }
}

private fun FileNode.parentDisplayName(context: Context): String =
    if (parentUri.canonicalValue == CloudreveUri.Root.value) {
        context.getString(R.string.file_details_root_directory)
    } else {
        cloudreveUriDisplayName(parentUri.value)
    }

private val FileNode.supportsThumbnail: Boolean
    get() = type == FileType.Image || type == FileType.Video || type == FileType.Audio

private fun Any.displayTypeLabel(context: Context): String = when (this) {
    is FileDetails -> type.displayTypeLabel(context, mimeType)
    is FileNode -> type.displayTypeLabel(context, mimeType)
    else -> context.getString(R.string.file_details_type_file)
}

private fun FileType.displayTypeLabel(
    context: Context,
    mimeType: String?,
): String = when (this) {
    FileType.Folder -> context.getString(com.zerostudio.cloudreve.core.ui.R.string.file_type_folder)
    FileType.Image -> context.getString(R.string.file_details_type_image)
    FileType.Video -> context.getString(R.string.file_details_type_video)
    FileType.Audio -> context.getString(R.string.file_details_type_audio)
    FileType.Pdf -> context.getString(R.string.file_details_type_pdf)
    FileType.Office -> context.getString(R.string.file_details_type_office)
    FileType.Code -> context.getString(R.string.file_details_type_code)
    FileType.Archive -> context.getString(com.zerostudio.cloudreve.core.ui.R.string.file_type_archive)
    FileType.Unknown -> mimeType?.takeIf(String::isNotBlank) ?: context.getString(R.string.file_details_type_file)
}

private fun formatEpochMillis(epochMillis: Long): String {
    if (epochMillis <= 0L) return "-"
    return DateTimeFormatter
        .ofPattern("yyyy/M/d HH:mm:ss", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
