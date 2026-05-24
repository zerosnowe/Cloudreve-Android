package com.zerostudio.cloudreve.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import com.zerostudio.cloudreve.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.util.UUID

private val TopBarActionIconSize = 24.dp

@Composable
fun FilesTopBarActions(
    onEnqueueUploads: (List<String>) -> Unit,
    onEnqueueDirectoryUpload: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onCreateFile: (String) -> Unit,
) {
    val context = LocalContext.current
    val activityContext = LocalView.current.context
    val activity = remember(activityContext) { activityContext.findActivity() }
    var showMenu by remember { mutableStateOf(false) }
    var nameSheet by remember { mutableStateOf<CreateNameSheet?>(null) }

    IconButton(
        onClick = {
            val intent = UploadProgressActivity.createIntent(activity ?: context)
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
    ) {
        Icon(
            imageVector = MiuixIcons.Regular.Update,
            contentDescription = stringResource(R.string.files_action_transfers),
            modifier = Modifier.size(TopBarActionIconSize),
        )
    }
    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = MiuixIcons.Regular.Add,
                contentDescription = stringResource(R.string.files_action_add),
                modifier = Modifier.size(TopBarActionIconSize),
            )
        }
        FilesCreateMenuAndSheets(
            showMenu = showMenu,
            nameSheet = nameSheet,
            onMenuDismiss = { showMenu = false },
            onNameSheetChange = { nameSheet = it },
            onEnqueueUploads = onEnqueueUploads,
            onEnqueueDirectoryUpload = onEnqueueDirectoryUpload,
            onCreateFolder = onCreateFolder,
            onCreateFile = onCreateFile,
        )
    }
}

@Composable
private fun FilesCreateMenuAndSheets(
    showMenu: Boolean,
    nameSheet: CreateNameSheet?,
    onMenuDismiss: () -> Unit,
    onNameSheetChange: (CreateNameSheet?) -> Unit,
    onEnqueueUploads: (List<String>) -> Unit,
    onEnqueueDirectoryUpload: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onCreateFile: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openFilesContract = remember { ActivityResultContracts.OpenMultipleDocuments() }
    val openDirectoryContract = remember { ActivityResultContracts.OpenDocumentTree() }
    val pickImagesContract = remember { ActivityResultContracts.PickMultipleVisualMedia() }
    val filePicker = rememberContextActivityResultLauncher(openFilesContract) { uris ->
        val sourceUris = uris.map { it.toString() }
        scope.launch {
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
            }
            onEnqueueUploads(sourceUris)
        }
    }
    val imagePicker = rememberContextActivityResultLauncher(pickImagesContract) { uris ->
        val sourceUris = uris.map { it.toString() }
        scope.launch {
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
            }
            onEnqueueUploads(sourceUris)
        }
    }
    val directoryPicker = rememberContextActivityResultLauncher(openDirectoryContract) { uri ->
        if (uri != null) {
            val sourceUri = uri.toString()
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
                onEnqueueDirectoryUpload(sourceUri)
            }
        }
    }

    OverlayListPopup(
        show = showMenu,
        popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
        alignment = PopupPositionProvider.Align.End,
        onDismissRequest = onMenuDismiss,
        onDismissFinished = onMenuDismiss,
    ) {
        ListPopupColumn {
            Column(
                modifier = Modifier.padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AddMenuItem(
                    icon = MiuixIcons.Regular.Image,
                    label = stringResource(R.string.files_action_upload_image),
                ) {
                    onMenuDismiss()
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
                AddMenuItem(
                    icon = MiuixIcons.Regular.UploadCloud,
                    label = stringResource(R.string.files_action_upload_file),
                ) {
                    onMenuDismiss()
                    filePicker.launch(arrayOf("*/*"))
                }
                AddMenuItem(
                    icon = MiuixIcons.Regular.Folder,
                    label = stringResource(R.string.files_action_upload_folder),
                ) {
                    onMenuDismiss()
                    directoryPicker.launch(null)
                }
                AddMenuItem(
                    icon = MiuixIcons.Regular.AddFolder,
                    label = stringResource(R.string.files_action_create_folder),
                ) {
                    onMenuDismiss()
                    onNameSheetChange(CreateNameSheet.Folder)
                }
                AddMenuItem(
                    icon = MiuixIcons.Regular.File,
                    label = stringResource(R.string.files_action_create_file),
                ) {
                    onMenuDismiss()
                    onNameSheetChange(CreateNameSheet.File)
                }
            }
        }
    }

    CreateNameBottomSheet(
        sheet = nameSheet,
        onDismiss = { onNameSheetChange(null) },
        onSubmit = { sheet, name ->
            when (sheet) {
                CreateNameSheet.Folder -> onCreateFolder(name)
                CreateNameSheet.File -> onCreateFile(name)
            }
            onNameSheetChange(null)
        },
    )
}

@Composable
private fun AddMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = label)
    }
}

@Composable
private fun CreateNameBottomSheet(
    sheet: CreateNameSheet?,
    onDismiss: () -> Unit,
    onSubmit: (CreateNameSheet, String) -> Unit,
) {
    var name by remember(sheet) { mutableStateOf("") }
    val canSubmit = name.trim().isNotEmpty()
    val title = when (sheet) {
        CreateNameSheet.Folder -> stringResource(R.string.files_create_folder_title)
        CreateNameSheet.File -> stringResource(R.string.files_create_file_title)
        null -> ""
    }
    val actionColor = if (canSubmit) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }

    WindowBottomSheet(
        show = sheet != null,
        title = title,
        startAction = {
            Text(
                text = stringResource(R.string.common_cancel),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        },
        endAction = {
            Text(
                text = stringResource(R.string.common_create),
                color = actionColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = canSubmit && sheet != null) {
                    val currentSheet = sheet ?: return@clickable
                    onSubmit(currentSheet, name.trim())
                },
            )
        },
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.files_create_name_label),
                singleLine = true,
            )
        }
    }
}

private enum class CreateNameSheet {
    Folder,
    File,
}

@Composable
private fun <I, O> rememberContextActivityResultLauncher(
    activityContract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit,
): ActivityResultLauncher<I> {
    val viewContext = LocalView.current.context
    val localContext = LocalContext.current
    val registryOwner = remember(viewContext, localContext) {
        viewContext.findActivityResultRegistryOwner() ?: localContext.findActivityResultRegistryOwner()
    }
    val currentOnResult: State<(O) -> Unit> = rememberUpdatedState(onResult)
    val key = remember { "cloudreve:${UUID.randomUUID()}" }
    var launcher by remember { mutableStateOf<ActivityResultLauncher<I>?>(null) }
    var pendingLaunch by remember { mutableStateOf<PendingActivityLaunch<I>?>(null) }

    DisposableEffect(registryOwner, activityContract, key) {
        if (registryOwner == null) {
            onDispose { }
        } else {
            val registeredLauncher = registryOwner.activityResultRegistry.register(key, activityContract) { result ->
                currentOnResult.value(result)
            }
            launcher = registeredLauncher
            onDispose {
                launcher = null
                registeredLauncher.unregister()
            }
        }
    }

    LaunchedEffect(launcher, pendingLaunch) {
        val pending = pendingLaunch ?: return@LaunchedEffect
        val registeredLauncher = launcher ?: return@LaunchedEffect
        pendingLaunch = null
        registeredLauncher.launch(pending.input, pending.options)
    }

    return remember {
        object : ActivityResultLauncher<I>() {
            override val contract: ActivityResultContract<I, *>
                get() = activityContract

            override fun launch(input: I, options: ActivityOptionsCompat?) {
                val registeredLauncher = launcher
                if (registeredLauncher != null) {
                    registeredLauncher.launch(input, options)
                } else {
                    pendingLaunch = PendingActivityLaunch(input, options)
                }
            }

            override fun unregister() = Unit
        }
    }
}

private data class PendingActivityLaunch<I>(
    val input: I,
    val options: ActivityOptionsCompat?,
)

private tailrec fun Context.findActivityResultRegistryOwner(): ActivityResultRegistryOwner? = when (this) {
    is ActivityResultRegistryOwner -> this
    is ContextWrapper -> baseContext.findActivityResultRegistryOwner()
    else -> null
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
