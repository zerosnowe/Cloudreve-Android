package com.zerostudio.cloudreve.feature.files

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.model.FileNode
import com.zerostudio.cloudreve.core.domain.model.FileType
import com.zerostudio.cloudreve.core.ui.FileRow
import com.zerostudio.cloudreve.core.ui.FileOverflowMenu
import com.zerostudio.cloudreve.core.ui.LocalCloudreveTopAppBarScrollBehavior
import com.zerostudio.cloudreve.core.ui.PageBottomPadding
import com.zerostudio.cloudreve.core.ui.RenameFileBottomSheet
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun FilesRoute(
    padding: PaddingValues,
    initialUri: CloudreveUri = CloudreveUri.Root,
    onOpenFolder: (FileNode) -> Unit = {},
    onOpenImage: (FileNode) -> Unit = {},
    onOpenAudio: (FileNode) -> Unit = {},
    onOpenFileDetails: (FileNode) -> Unit = {},
    viewModel: FilesViewModel = koinViewModel(parameters = { parametersOf(initialUri) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FilesScreen(
        state = state,
        padding = padding,
        onQueryChange = viewModel::onQueryChange,
        onRefresh = viewModel::refresh,
        onToggleSelection = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        onDownloadSelected = viewModel::downloadSelected,
        onShareSelected = viewModel::shareSelected,
        onDeleteSelected = viewModel::deleteSelected,
        onRenameSelected = viewModel::renameSelected,
        onCopySelected = viewModel::copySelected,
        onResolveOpenWith = viewModel::resolveOpenWithUrl,
        onOpenFolder = onOpenFolder,
        onOpenImage = onOpenImage,
        onOpenAudio = onOpenAudio,
        onOpenFileDetails = onOpenFileDetails,
    )
}

@Composable
fun FilesScreen(
    state: FilesUiState,
    padding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onToggleSelection: (FileNode) -> Unit,
    onClearSelection: () -> Unit,
    onDownloadSelected: () -> Unit,
    onShareSelected: ((String) -> Unit) -> Unit,
    onDeleteSelected: () -> Unit,
    onRenameSelected: (String) -> Unit,
    onCopySelected: () -> Unit,
    onResolveOpenWith: (FileNode, (String) -> Unit) -> Unit,
    onOpenFolder: (FileNode) -> Unit,
    onOpenImage: (FileNode) -> Unit,
    onOpenAudio: (FileNode) -> Unit,
    onOpenFileDetails: (FileNode) -> Unit,
) {
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }
    var showRenameSheet by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val scrollBehavior = LocalCloudreveTopAppBarScrollBehavior.current
    val context = LocalContext.current
    val selectedFiles = remember(state.files, state.selectedUris) {
        state.files.filter { it.uri in state.selectedUris }
    }
    val singleSelectedFile = selectedFiles.singleOrNull()
    val selectionActive = selectedFiles.isNotEmpty()
    val actualSearchExpanded = searchExpanded || state.query.isNotBlank()
    val errorText = state.errorMessage ?: if (state.errorRes != null) stringResource(state.errorRes) else null
    val refreshTexts = listOf(
        stringResource(R.string.files_pull_refresh_pull),
        stringResource(R.string.files_pull_refresh_release),
        stringResource(R.string.files_pull_refresh_refreshing),
        stringResource(R.string.files_pull_refresh_done),
    )

    BackHandler(enabled = selectionActive) {
        onClearSelection()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (state.isRefreshing && state.files.isEmpty()) {
            InfiniteProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .padding(top = padding.calculateTopPadding())
                    .widthIn(max = 720.dp),
            ) {
                SearchBar(
                    modifier = Modifier.fillMaxWidth(),
                    expanded = actualSearchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    inputField = {
                        InputField(
                            query = state.query,
                            onQueryChange = onQueryChange,
                            onSearch = {
                                onQueryChange(it)
                                searchExpanded = false
                                focusManager.clearFocus()
                            },
                            expanded = actualSearchExpanded,
                            onExpandedChange = { searchExpanded = it },
                            label = stringResource(R.string.files_search_label),
                        )
                    },
                    outsideEndAction = {
                        IconButton(
                            onClick = {
                                searchExpanded = false
                                onQueryChange("")
                                focusManager.clearFocus()
                            },
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Close,
                                contentDescription = stringResource(R.string.files_search_cancel),
                            )
                        }
                    },
                ) {
                    Column(
                        modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.files_search_count, state.visibleFiles.size),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                errorText?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MiuixTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                PullToRefresh(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    refreshTexts = refreshTexts,
                    topAppBarScrollBehavior = scrollBehavior,
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical(),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 0.dp,
                            top = 12.dp,
                            end = 0.dp,
                            bottom = PageBottomPadding + if (selectionActive) 80.dp else 0.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (state.visibleFiles.isEmpty() && !state.isRefreshing) {
                            item(contentType = "files_empty") {
                                TextButton(
                                    text = stringResource(R.string.files_empty_refresh),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    onClick = onRefresh,
                                )
                            }
                        }
                        items(
                            items = state.visibleFiles,
                            key = { it.uri.value },
                            contentType = { it.type },
                        ) { file ->
                            FileRow(
                                file = file,
                                selected = file.uri in state.selectedUris,
                                selectionActive = selectionActive,
                                onClick = {
                                    if (selectionActive) {
                                        onToggleSelection(file)
                                    } else {
                                        when (file.type) {
                                            FileType.Folder -> onOpenFolder(file)
                                            FileType.Image -> onOpenImage(file)
                                            FileType.Audio -> onOpenAudio(file)
                                            else -> onOpenFileDetails(file)
                                        }
                                    }
                                },
                                onLongPress = { onToggleSelection(file) },
                                onToggleSelected = { onToggleSelection(file) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thumbnailUrl = state.thumbnailUrls[file.uri] ?: file.thumbnailUrl,
                                thumbnailCacheGeneration = state.thumbnailCacheGeneration,
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selectionActive,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = padding.calculateBottomPadding()),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            FilesSelectionNavigationBar(
                onDownload = onDownloadSelected,
                onShare = {
                    onShareSelected { shareUrl ->
                        val shareIntent = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, shareUrl)
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    }
                },
                onDelete = onDeleteSelected,
                onMore = { showMoreMenu = true },
            )
        }

        FileOverflowMenu(
            show = showMoreMenu,
            onDismiss = { showMoreMenu = false },
            canOpenWith = singleSelectedFile != null,
            canRename = singleSelectedFile != null,
            canCopy = selectedFiles.isNotEmpty(),
            onOpenWith = {
                val file = singleSelectedFile ?: return@FileOverflowMenu
                onResolveOpenWith(file) { url ->
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            onDownload = onDownloadSelected,
            onShare = {
                onShareSelected { shareUrl ->
                    val shareIntent = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, shareUrl)
                    context.startActivity(Intent.createChooser(shareIntent, null))
                }
            },
            onRename = {
                if (singleSelectedFile != null) {
                    showRenameSheet = true
                }
            },
            onCopy = onCopySelected,
        )

        RenameFileBottomSheet(
            show = showRenameSheet,
            initialValue = singleSelectedFile?.name.orEmpty(),
            onDismiss = { showRenameSheet = false },
            onConfirm = { newName ->
                showRenameSheet = false
                onRenameSelected(newName)
            },
        )
    }
}

@Composable
private fun FilesSelectionNavigationBar(
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onMore: () -> Unit,
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        color = MiuixTheme.colorScheme.surface,
        showDivider = true,
        defaultWindowInsetsPadding = false,
    ) {
        FilesSelectionNavigationItem(
            icon = MiuixIcons.Regular.Download,
            label = stringResource(R.string.files_action_download),
            onClick = onDownload,
        )
        FilesSelectionNavigationItem(
            icon = MiuixIcons.Regular.Share,
            label = stringResource(R.string.files_action_share),
            onClick = onShare,
        )
        FilesSelectionNavigationItem(
            icon = MiuixIcons.Regular.Delete,
            label = stringResource(R.string.files_action_delete),
            onClick = onDelete,
        )
        FilesSelectionNavigationItem(
            icon = MiuixIcons.Regular.More,
            label = stringResource(R.string.files_action_more),
            onClick = onMore,
        )
    }
}

@Composable
private fun RowScope.FilesSelectionNavigationItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}
