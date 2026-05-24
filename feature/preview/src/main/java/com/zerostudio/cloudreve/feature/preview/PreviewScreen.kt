package com.zerostudio.cloudreve.feature.preview

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
import coil3.size.Precision
import coil3.size.Scale
import com.zerostudio.cloudreve.core.domain.model.FileNode
import com.zerostudio.cloudreve.core.ui.PageBottomPadding
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun PreviewRoute(
    padding: PaddingValues,
    onOpenImage: (FileNode) -> Unit = {},
    onSearch: () -> Unit = {},
    viewModel: AlbumViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val viewContext = LocalView.current.context
    LaunchedEffect(state.isScanning) {
        if (
            state.isScanning &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            viewContext.findActivity()?.let { activity ->
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE,
                )
            }
        }
    }
    val syncStartedText = stringResource(R.string.album_sync_started)
    val syncCompletedText = stringResource(R.string.album_sync_completed)
    val infoEvent = state.infoEvent
    LaunchedEffect(infoEvent?.id) {
        if (infoEvent == null) return@LaunchedEffect
        val message = when (infoEvent.type) {
            AlbumInfoEventType.SyncStarted -> syncStartedText
            AlbumInfoEventType.SyncCompleted -> syncCompletedText
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeInfoEvent(infoEvent.id)
    }
    AlbumScreen(
        state = state,
        padding = padding,
        bottomPadding = PageBottomPadding,
        showFloatingHeader = true,
        onSearch = onSearch,
        onOpenImage = onOpenImage,
        onVisiblePhoto = viewModel::ensureThumbnail,
    )
}

@Composable
fun AlbumSearchRoute(
    onBack: () -> Unit,
    onOpenImage: (FileNode) -> Unit,
    viewModel: AlbumViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.album_search_title),
                color = MiuixTheme.colorScheme.background,
                titlePadding = 16.dp,
                navigationIconPadding = 16.dp,
                actionIconPadding = 16.dp,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.album_search_back),
                        )
                    }
                },
            )
        },
        containerColor = MiuixTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MiuixTheme.colorScheme.background),
        ) {
            AlbumSearchBar(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (state.query.isBlank()) {
                    Text(
                        text = stringResource(R.string.album_search_hint),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 40.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    AlbumGrid(
                        state = state,
                        bottomPadding = 24.dp,
                        topContentPadding = 12.dp,
                        onOpenImage = onOpenImage,
                        onVisiblePhoto = viewModel::ensureThumbnail,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumScreen(
    state: AlbumUiState,
    padding: PaddingValues,
    bottomPadding: Dp,
    showFloatingHeader: Boolean,
    onSearch: () -> Unit,
    onOpenImage: (FileNode) -> Unit,
    onVisiblePhoto: (AlbumMediaItem) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val scrollBehavior = MiuixScrollBehavior()
    val currentDate by remember(state.gridItems, gridState) {
        derivedStateOf { state.gridItems.currentPhotoDateTitle(gridState.firstVisibleItemIndex) }
    }
    val photoCount = remember(state.gridItems) {
        state.gridItems.count { it is AlbumGridItem.Photo }
    }
    val fallbackTitle = stringResource(R.string.album_title)
    val topBarTitle = currentDate.ifBlank { fallbackTitle }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
            .background(MiuixTheme.colorScheme.background),
    ) {
        AlbumGrid(
            state = state,
            gridState = gridState,
            bottomPadding = bottomPadding + padding.calculateBottomPadding(),
            topContentPadding = if (showFloatingHeader) 128.dp else 12.dp,
            scrollBehavior = scrollBehavior,
            onOpenImage = onOpenImage,
            onVisiblePhoto = onVisiblePhoto,
        )
        if (showFloatingHeader) {
            AlbumDateTopAppBar(
                title = topBarTitle,
                subtitle = stringResource(R.string.album_photo_count, photoCount),
                scrollBehavior = scrollBehavior,
                onSearch = onSearch,
                modifier = Modifier
                    .align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun AlbumGrid(
    state: AlbumUiState,
    bottomPadding: Dp,
    topContentPadding: Dp,
    onOpenImage: (FileNode) -> Unit,
    onVisiblePhoto: (AlbumMediaItem) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior? = null,
) {
    val hasPhotos = state.gridItems.any { it is AlbumGridItem.Photo }
    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(ALBUM_COLUMNS),
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (scrollBehavior != null) {
                        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        Modifier
                    },
                )
                .overScrollVertical(),
            state = gridState,
            contentPadding = PaddingValues(
                start = 0.dp,
                top = topContentPadding,
                end = 0.dp,
                bottom = bottomPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            itemsIndexed(
                items = state.gridItems,
                key = { _, item -> item.key },
                contentType = { _, item ->
                    when (item) {
                        is AlbumGridItem.Header -> "album_header"
                        is AlbumGridItem.Photo -> "album_photo"
                    }
                },
            ) { _, item ->
                when (item) {
                    is AlbumGridItem.Header -> Unit
                    is AlbumGridItem.Photo -> AlbumPhotoTile(
                        item = item,
                        onOpenImage = onOpenImage,
                        onVisiblePhoto = onVisiblePhoto,
                    )
                }
            }
        }

        if (!state.isScanning && !hasPhotos) {
            Text(
                text = if (state.errorMessage == null) {
                    stringResource(R.string.album_empty)
                } else {
                    state.errorMessage
                },
                modifier = Modifier.align(Alignment.Center),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun AlbumDateTopAppBar(
    title: String,
    subtitle: String,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = title,
        largeTitle = title,
        subtitle = subtitle,
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.36f),
        titleColor = Color.White,
        largeTitleColor = Color.White,
        subtitleColor = Color.White.copy(alpha = 0.82f),
        scrollBehavior = scrollBehavior,
        titlePadding = 10.dp,
        navigationIconPadding = 10.dp,
        actionIconPadding = 4.dp,
        actions = {
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = MiuixIcons.Regular.Search,
                    contentDescription = stringResource(R.string.album_search_title),
                    tint = Color.White,
                )
            }
        },
    )
}

@Composable
private fun AlbumSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchBar(
        modifier = modifier,
        expanded = true,
        onExpandedChange = {},
        inputField = {
            InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = onQueryChange,
                expanded = true,
                onExpandedChange = {},
                label = stringResource(R.string.album_search_label),
            )
        },
        outsideEndAction = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Close,
                        contentDescription = stringResource(R.string.album_search_clear),
                    )
                }
            }
        },
    ) {
        Unit
    }
}

@Composable
private fun AlbumPhotoTile(
    item: AlbumGridItem.Photo,
    onOpenImage: (FileNode) -> Unit,
    onVisiblePhoto: (AlbumMediaItem) -> Unit,
) {
    val media = item.media
    val thumbnailUrl = item.thumbnailUrl
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val tileSizePx = with(density) {
        (configuration.screenWidthDp.dp / ALBUM_COLUMNS).roundToPx().coerceAtLeast(96)
    }

    LaunchedEffect(media.uri, thumbnailUrl) {
        if (thumbnailUrl.isNullOrBlank()) {
            onVisiblePhoto(media)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .clickable {
                onOpenImage(media.toFileNode(resolvedThumbnailUrl = thumbnailUrl))
            },
    ) {
        if (!thumbnailUrl.isNullOrBlank()) {
            val request = remember(thumbnailUrl, tileSizePx) {
                ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    // Coil performs decode, downsampling, cancellation and cache IO off the
                    // main thread. The request is bounded to the visible tile size, uses
                    // RGB_565 for thumbnails, and keeps memory/disk cache enabled to avoid OOM.
                    .size(tileSizePx, tileSizePx)
                    .precision(Precision.INEXACT)
                    .scale(Scale.FILL)
                    .allowRgb565(true)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = media.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = MiuixIcons.Regular.Image,
                contentDescription = media.name,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

private const val ALBUM_COLUMNS = 4
private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 4102

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
