package com.zerostudio.cloudreve.feature.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun ImagePreviewRoute(
    args: ImagePreviewArgs,
    onBack: () -> Unit,
    onShare: (ImagePreviewSharePayload) -> Unit,
    onEdit: (ImagePreviewEditPayload) -> Unit,
    onDeleted: () -> Unit,
    viewModel: ImagePreviewViewModel = koinViewModel(parameters = { parametersOf(args) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ImagePreviewScreen(
        state = state,
        onBack = onBack,
        onShare = { viewModel.share(onShare) },
        onEdit = { viewModel.edit(onEdit) },
        onToggleFavorite = viewModel::toggleFavorite,
        onDetails = viewModel::showDetails,
        onDismissDetails = viewModel::dismissDetails,
        onDeletePrompt = viewModel::showDeletePrompt,
        onDismissDeletePrompt = viewModel::dismissDeletePrompt,
        onConfirmDelete = { viewModel.delete(onDeleted) },
    )
}

@Composable
fun ImagePreviewScreen(
    state: ImagePreviewUiState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDetails: () -> Unit,
    onDismissDetails: () -> Unit,
    onDeletePrompt: () -> Unit,
    onDismissDeletePrompt: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val args = state.args
    var imageScale by remember(state.imageFilePath) { mutableFloatStateOf(1f) }
    var imageOffset by remember(state.imageFilePath) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember(state.imageFilePath) { mutableStateOf(IntSize.Zero) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = args.name,
                subtitle = formatEpochMillis(args.updatedAtEpochMillis),
                color = MiuixTheme.colorScheme.background,
                titlePadding = 16.dp,
                navigationIconPadding = 16.dp,
                actionIconPadding = 16.dp,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.image_preview_back),
                        )
                    }
                },
            )
        },
        containerColor = MiuixTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MiuixTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.imageFilePath != null) {
                        AsyncImage(
                            model = File(state.imageFilePath),
                            contentDescription = args.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { viewportSize = it }
                                .pointerInput(state.imageFilePath, viewportSize) {
                                    detectTransformGestures { centroid, pan, zoom, _ ->
                                        val previousScale = imageScale
                                        val nextScale = (previousScale * zoom).coerceIn(1f, 6f)
                                        val scaleChange = nextScale / previousScale
                                        val viewportCenter = Offset(
                                            x = viewportSize.width / 2f,
                                            y = viewportSize.height / 2f,
                                        )
                                        val centroidOffset = centroid - viewportCenter
                                        val rawOffset = if (nextScale == 1f) {
                                            Offset.Zero
                                        } else {
                                            (imageOffset + centroidOffset) * scaleChange - centroidOffset + pan
                                        }
                                        imageScale = nextScale
                                        imageOffset = if (nextScale == 1f) {
                                            Offset.Zero
                                        } else {
                                            rawOffset.boundToViewport(viewportSize, nextScale)
                                        }
                                    }
                                }
                                .graphicsLayer {
                                    scaleX = imageScale
                                    scaleY = imageScale
                                    translationX = imageOffset.x
                                    translationY = imageOffset.y
                                    transformOrigin = TransformOrigin.Center
                                },
                            contentScale = ContentScale.Fit,
                        )
                    }
                    if (state.isLoadingImage) {
                        InfiniteProgressIndicator()
                    }
                    val blockingError = state.errorMessage
                        ?.takeIf { state.imageFilePath == null && !state.isLoadingImage }
                    blockingError?.let { message ->
                        FloatingErrorText(
                            message = message,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            }

            PreviewFloatingBars(
                favorite = state.isFavorite,
                onShare = onShare,
                onEdit = onEdit,
                onToggleFavorite = onToggleFavorite,
                onDetails = onDetails,
                onDelete = onDeletePrompt,
            )

            ImageDetailsBottomSheet(
                show = state.showDetailsDialog,
                loading = state.isLoadingDetails,
                details = state.details,
                localMetadata = state.localMetadata,
                geoPoint = state.geoPoint,
                locationAddress = state.locationAddress,
                loadingLocationAddress = state.isLoadingLocationAddress,
                fallback = state.args,
                onDismiss = onDismissDetails,
            )
        }
    }

    DeleteDialog(
        show = state.showDeleteDialog,
        deleting = state.isDeleting,
        fileName = state.args.name,
        onDismiss = onDismissDeletePrompt,
        onConfirm = onConfirmDelete,
    )
}

@Composable
private fun PreviewFloatingBars(
    favorite: Boolean,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 22.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, bottom = bottomPadding),
    ) {
        FloatingIconGroup(
            modifier = Modifier.align(Alignment.BottomStart),
            horizontalPadding = 4.dp,
        ) {
            FloatingIconButton(
                icon = MiuixIcons.Regular.Share,
                label = stringResource(R.string.image_preview_share),
                onClick = onShare,
            )
        }
        FloatingIconGroup(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalPadding = 8.dp,
        ) {
            FloatingIconButton(
                icon = if (favorite) MiuixIcons.Regular.FavoritesFill else MiuixIcons.Regular.Favorites,
                label = stringResource(R.string.image_preview_favorite),
                tint = if (favorite) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                onClick = onToggleFavorite,
            )
            Spacer(modifier = Modifier.width(8.dp))
            FloatingIconButton(
                icon = MiuixIcons.Regular.Info,
                label = stringResource(R.string.image_preview_details),
                tint = MiuixTheme.colorScheme.primary,
                onClick = onDetails,
            )
            Spacer(modifier = Modifier.width(8.dp))
            FloatingIconButton(
                icon = MiuixIcons.Regular.Edit,
                label = stringResource(R.string.image_preview_edit),
                tint = MiuixTheme.colorScheme.primary,
                onClick = onEdit,
            )
        }
        FloatingIconGroup(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalPadding = 4.dp,
        ) {
            FloatingIconButton(
                icon = MiuixIcons.Regular.Delete,
                label = stringResource(R.string.image_preview_delete),
                tint = MiuixTheme.colorScheme.error,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun FloatingIconGroup(
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    content: @Composable RowScope.() -> Unit,
) {
    Card(
        modifier = modifier.height(56.dp),
        cornerRadius = 28.dp,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun FloatingIconButton(
    icon: ImageVector,
    label: String,
    tint: Color = MiuixTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    IconButton(
        modifier = Modifier.size(48.dp),
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(26.dp),
            tint = tint,
        )
    }
}

@Composable
private fun FloatingErrorText(
    message: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        cornerRadius = 18.dp,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MiuixTheme.colorScheme.error,
        ),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = MiuixTheme.colorScheme.error,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DeleteDialog(
    show: Boolean,
    deleting: Boolean,
    fileName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = stringResource(R.string.image_preview_delete_title),
        summary = stringResource(R.string.image_preview_delete_summary, fileName),
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismiss,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                enabled = !deleting,
                onClick = onConfirm,
            ) {
                if (deleting) {
                    InfiniteProgressIndicator()
                } else {
                    Text(stringResource(R.string.image_preview_delete))
                }
            }
        }
    }
}

private fun formatEpochMillis(epochMillis: Long): String {
    if (epochMillis <= 0L) return "-"
    return DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))
}

private fun Offset.boundToViewport(viewportSize: IntSize, scale: Float): Offset {
    if (viewportSize == IntSize.Zero || scale <= 1f) return Offset.Zero
    val maxX = ((viewportSize.width * scale) - viewportSize.width) / 2f
    val maxY = ((viewportSize.height * scale) - viewportSize.height) / 2f
    return Offset(
        x = x.coerceIn(-maxX, maxX),
        y = y.coerceIn(-maxY, maxY),
    )
}
