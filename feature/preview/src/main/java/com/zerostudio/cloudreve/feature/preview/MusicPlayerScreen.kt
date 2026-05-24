package com.zerostudio.cloudreve.feature.preview

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.gaze.capsule.ContinuousRoundedRectangle
import kotlin.math.abs
import kotlinx.coroutines.android.awaitFrame
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MusicPlayerRoute(
    args: MusicPlayerArgs,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    viewModel: MusicPlayerViewModel = koinViewModel(parameters = { parametersOf(args) }),
) {
    LaunchedEffect(args.uri.value, args.name, args.size, args.updatedAtEpochMillis) {
        viewModel.updateArgs(args)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    MusicPlayerScreen(
        state = state,
        onBack = onBack,
        onTogglePlayPause = viewModel::togglePlayPause,
        onSeekBack = { viewModel.seekBy(-10_000L) },
        onSeekForward = { viewModel.seekBy(10_000L) },
        onSeekToFraction = viewModel::seekToFraction,
        onSetVolumeFraction = viewModel::setVolumeFraction,
        onRefreshAudioOutputs = viewModel::refreshAudioOutputs,
        onSelectAudioOutput = viewModel::selectAudioOutput,
        onSetShowLyricsPage = viewModel::setShowLyricsPage,
        onSeekToLyric = viewModel::seekTo,
        onRetryLyrics = viewModel::retryLyrics,
        onOpenDetails = onOpenDetails,
    )
}

@Composable
fun MusicPlayerScreen(
    state: MusicPlayerUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onSetVolumeFraction: (Float) -> Unit,
    onRefreshAudioOutputs: () -> Unit,
    onSelectAudioOutput: (String) -> Unit,
    onSetShowLyricsPage: (Boolean) -> Unit,
    onSeekToLyric: (Int) -> Unit,
    onRetryLyrics: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    BackHandler(enabled = state.showLyricsPage) {
        onSetShowLyricsPage(false)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        MusicBackdrop(state = state)
        AnimatedContent(
            targetState = state.showLyricsPage,
            modifier = Modifier.fillMaxSize(),
            label = "music_player_page",
        ) { lyricsPage ->
            if (lyricsPage) {
                LyricsPage(
                    state = state,
                    onSeekToLyric = onSeekToLyric,
                    onRetryLyrics = onRetryLyrics,
                    onOpenDetails = onOpenDetails,
                )
            } else {
                OverviewPage(
                    state = state,
                    onBack = onBack,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekBack = onSeekBack,
                    onSeekForward = onSeekForward,
                    onSeekToFraction = onSeekToFraction,
                    onSetVolumeFraction = onSetVolumeFraction,
                    onRefreshAudioOutputs = onRefreshAudioOutputs,
                    onSelectAudioOutput = onSelectAudioOutput,
                    onOpenLyrics = { onSetShowLyricsPage(true) },
                    onOpenDetails = onOpenDetails,
                )
            }
        }
    }
}

@Composable
private fun OverviewPage(
    state: MusicPlayerUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onSetVolumeFraction: (Float) -> Unit,
    onRefreshAudioOutputs: () -> Unit,
    onSelectAudioOutput: (String) -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val artwork = rememberArtworkBitmap(state.artworkBytes)
    val artworkWidthFraction by animateFloatAsState(
        targetValue = if (state.playbackState.isPlaying) 0.96f else 0.88f,
        animationSpec = spring(dampingRatio = 0.56f, stiffness = 230f),
        label = "music_artwork_width_fraction",
    )
    val artworkMaxWidth by animateDpAsState(
        targetValue = if (state.playbackState.isPlaying) 368.dp else 336.dp,
        animationSpec = spring(dampingRatio = 0.56f, stiffness = 230f),
        label = "music_artwork_max_width",
    )
    fun Modifier.playerContentWidth() = fillMaxWidth(0.96f).widthIn(max = 368.dp)
    val smoothPositionMs = rememberSmoothPlaybackPosition(state.playbackState)
    val smoothPlaybackProgress = remember(smoothPositionMs, state.playbackState.durationMs) {
        if (state.playbackState.durationMs <= 0L) {
            0f
        } else {
            (smoothPositionMs.toFloat() / state.playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding + 18.dp, bottom = bottomPadding + 22.dp)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.24f)),
            )
        }
        Spacer(modifier = Modifier.height(36.dp))
        ArtworkPanel(
            artwork = artwork,
            modifier = Modifier
                .fillMaxWidth(artworkWidthFraction)
                .widthIn(max = artworkMaxWidth),
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier
                .playerContentWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = state.title,
                    color = Color.White,
                    style = MiuixTheme.textStyles.title2.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.artist ?: stringResource(R.string.music_player_unknown_artist),
                    color = Color.White.copy(alpha = 0.54f),
                    style = MiuixTheme.textStyles.title4.copy(fontSize = 16.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MaterialRoundIconButton(
                    icon = Icons.Rounded.StarBorder,
                    contentDescription = stringResource(R.string.music_player_favorite),
                    onClick = {},
                )
                MaterialRoundIconButton(
                    icon = Icons.Rounded.MoreHoriz,
                    contentDescription = stringResource(R.string.music_player_more),
                    onClick = onOpenDetails,
                )
            }
        }
        Spacer(modifier = Modifier.height(22.dp))
        PlaybackSlider(
            value = smoothPlaybackProgress,
            onValueChangeFinished = onSeekToFraction,
            modifier = Modifier.playerContentWidth(),
            trackHeight = 7.dp,
            activeColor = Color.White.copy(alpha = 0.74f),
            inactiveColor = Color.White.copy(alpha = 0.24f),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .playerContentWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(smoothPositionMs),
                color = Color.White.copy(alpha = 0.64f),
                style = MiuixTheme.textStyles.title4.copy(fontSize = 15.sp),
            )
            Text(
                text = formatRemainingDuration(
                    durationMs = state.playbackState.durationMs,
                    positionMs = smoothPositionMs,
                ),
                color = Color.White.copy(alpha = 0.64f),
                style = MiuixTheme.textStyles.title4.copy(fontSize = 15.sp),
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Row(
            modifier = Modifier
                .playerContentWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportControlButton(
                icon = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.music_player_seek_back),
                onClick = onSeekBack,
                buttonSize = 76.dp,
                iconSize = 54.dp,
            )
            TransportControlButton(
                icon = if (state.playbackState.isPlaying) {
                    Icons.Rounded.Pause
                } else {
                    Icons.Rounded.PlayArrow
                },
                contentDescription = if (state.playbackState.isPlaying) {
                    stringResource(R.string.music_player_pause)
                } else {
                    stringResource(R.string.music_player_play)
                },
                onClick = onTogglePlayPause,
                buttonSize = 84.dp,
                iconSize = 66.dp,
            )
            TransportControlButton(
                icon = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.music_player_seek_forward),
                onClick = onSeekForward,
                buttonSize = 76.dp,
                iconSize = 54.dp,
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.playerContentWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeDown,
                contentDescription = stringResource(R.string.music_player_volume_down),
                modifier = Modifier.size(22.dp),
                tint = Color.White.copy(alpha = 0.86f),
            )
            Spacer(modifier = Modifier.width(14.dp))
            PlaybackSlider(
                value = state.volumeFraction,
                onValueChangeFinished = onSetVolumeFraction,
                modifier = Modifier.weight(1f),
                trackHeight = 7.dp,
                activeColor = Color.White.copy(alpha = 0.78f),
                inactiveColor = Color.White.copy(alpha = 0.22f),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = stringResource(R.string.music_player_volume_up),
                modifier = Modifier.size(22.dp),
                tint = Color.White.copy(alpha = 0.86f),
            )
        }
        state.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = error,
                color = Color(0xFFFFC5C5),
                style = MiuixTheme.textStyles.body2,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        BottomMusicActions(
            state = state,
            onOpenLyrics = onOpenLyrics,
            onRefreshAudioOutputs = onRefreshAudioOutputs,
            onSelectAudioOutput = onSelectAudioOutput,
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun rememberSmoothPlaybackPosition(playbackState: MusicPlaybackState): Long {
    val animatedPositionState = remember { mutableLongStateOf(playbackState.positionMs) }
    LaunchedEffect(
        playbackState.isPlaying,
        playbackState.positionMs,
        playbackState.durationMs,
        playbackState.lastUpdateTime,
    ) {
        if (playbackState.isPlaying && playbackState.durationMs > 0L) {
            while (true) {
                val elapsed = System.currentTimeMillis() - playbackState.lastUpdateTime
                animatedPositionState.longValue = (playbackState.positionMs + elapsed)
                    .coerceIn(0L, playbackState.durationMs)
                awaitFrame()
            }
        } else {
            animatedPositionState.longValue = playbackState.positionMs
        }
    }
    return animatedPositionState.longValue
}

@Composable
private fun MaterialRoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 40.dp,
    iconSize: Dp = 24.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 560f),
        label = "music_round_button_scale",
    )
    Box(
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color(0xFF8C8E9A).copy(alpha = 0.62f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = Color.White,
        )
    }
}

@Composable
private fun BottomMusicActions(
    state: MusicPlayerUiState,
    onOpenLyrics: () -> Unit,
    onRefreshAudioOutputs: () -> Unit,
    onSelectAudioOutput: (String) -> Unit,
) {
    val context = LocalContext.current
    var showAudioOutputMenu by rememberSaveable { mutableStateOf(false) }
    var bluetoothPermissionGranted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        bluetoothPermissionGranted = granted
        onRefreshAudioOutputs()
        showAudioOutputMenu = true
    }

    fun openAudioOutputMenu() {
        bluetoothPermissionGranted =
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        onRefreshAudioOutputs()
        showAudioOutputMenu = true
    }
    BackHandler(enabled = showAudioOutputMenu) {
        showAudioOutputMenu = false
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomMaterialIconButton(
                icon = Icons.Rounded.ChatBubbleOutline,
                contentDescription = stringResource(R.string.music_player_lyrics),
                onClick = onOpenLyrics,
            )
            BottomMaterialIconButton(
                icon = Icons.Rounded.Headset,
                contentDescription = stringResource(R.string.music_player_audio_output),
                onClick = ::openAudioOutputMenu,
            )
            BottomMaterialIconButton(
                icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                contentDescription = stringResource(R.string.music_player_queue),
                onClick = {},
            )
        }
        AudioOutputCascadingMenu(
            show = showAudioOutputMenu,
            devices = state.audioOutputDevices,
            permissionGranted = bluetoothPermissionGranted,
            error = state.audioOutputError,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 28.dp)
                .offset(y = (-70).dp)
                .zIndex(4f),
            onRequestPermission = {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            },
            onDismiss = { showAudioOutputMenu = false },
            onSelect = { deviceId ->
                onSelectAudioOutput(deviceId)
                showAudioOutputMenu = false
            },
        )
    }
}

@Composable
private fun AudioOutputCascadingMenu(
    show: Boolean,
    devices: List<MusicAudioOutputDevice>,
    permissionGranted: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (!show) expanded = false
    }
    val title = stringResource(R.string.music_player_audio_output_title)
    val selectedDevice = devices.firstOrNull { it.selected }
    val selectedLabel = stringResource(R.string.music_player_audio_output_selected)
    val permissionRequired = stringResource(R.string.music_player_audio_output_permission_required)
    val grantPermission = stringResource(R.string.music_player_audio_output_grant_permission)
    val noHeadset = stringResource(R.string.music_player_audio_output_no_headset)
    val menuContainerColor = Color(0xFF202534).copy(alpha = 0.98f)
    val menuSelectedContainerColor = Color(0xFF343A4D).copy(alpha = 0.98f)
    val contentColor = Color(0xFFF6F7FF).copy(alpha = 0.92f)
    val summaryColor = Color(0xFFF6F7FF).copy(alpha = 0.56f)
    val selectedColor = Color.White

    AnimatedVisibility(
        visible = show,
        modifier = modifier,
        enter = fadeIn(tween(140)) + scaleIn(
            initialScale = 0.92f,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 520f),
        ),
        exit = fadeOut(tween(110)) + scaleOut(
            targetScale = 0.96f,
            animationSpec = tween(110),
        ),
    ) {
        Card(
            modifier = Modifier.width(286.dp),
            cornerRadius = 26.dp,
            colors = CardDefaults.defaultColors(
                color = menuContainerColor,
                contentColor = contentColor,
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AudioOutputMenuHeaderRow(
                    title = title,
                    summary = selectedDevice?.name,
                    expanded = expanded,
                    contentColor = contentColor,
                    summaryColor = summaryColor,
                    onClick = { expanded = !expanded },
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(120)) + expandVertically(
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = 480f),
                    ),
                    exit = fadeOut(tween(90)) + shrinkVertically(
                        animationSpec = tween(120),
                    ),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AudioOutputMenuDivider()
                        devices.forEach { device ->
                            val icon = when (device.type) {
                                MusicAudioOutputType.Speaker -> Icons.AutoMirrored.Rounded.VolumeUp
                                MusicAudioOutputType.Headset -> Icons.Rounded.Headset
                            }
                            AudioOutputMenuDeviceRow(
                                title = device.name,
                                summary = if (device.selected) selectedLabel else null,
                                icon = icon,
                                selected = device.selected,
                                contentColor = contentColor,
                                summaryColor = summaryColor,
                                selectedColor = selectedColor,
                                selectedContainerColor = menuSelectedContainerColor,
                                onClick = { onSelect(device.id) },
                            )
                        }
                        if (!permissionGranted) {
                            AudioOutputMenuDeviceRow(
                                title = grantPermission,
                                summary = permissionRequired,
                                icon = Icons.Rounded.Headset,
                                selected = false,
                                contentColor = contentColor,
                                summaryColor = summaryColor,
                                selectedColor = selectedColor,
                                selectedContainerColor = menuSelectedContainerColor,
                                onClick = onRequestPermission,
                            )
                        } else if (devices.none { it.type == MusicAudioOutputType.Headset }) {
                            AudioOutputMenuDeviceRow(
                                title = noHeadset,
                                summary = null,
                                icon = Icons.Rounded.Headset,
                                selected = false,
                                enabled = false,
                                contentColor = summaryColor,
                                summaryColor = summaryColor,
                                selectedColor = selectedColor,
                                selectedContainerColor = menuSelectedContainerColor,
                                onClick = {},
                            )
                        }
                        error?.takeIf(String::isNotBlank)?.let { message ->
                            AudioOutputMenuDeviceRow(
                                title = message,
                                summary = null,
                                icon = Icons.Rounded.Headset,
                                selected = false,
                                enabled = false,
                                contentColor = Color(0xFFFFC5C5),
                                summaryColor = summaryColor,
                                selectedColor = selectedColor,
                                selectedContainerColor = menuSelectedContainerColor,
                                onClick = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioOutputMenuHeaderRow(
    title: String,
    summary: String?,
    expanded: Boolean,
    contentColor: Color,
    summaryColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Headset,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            tint = contentColor,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = contentColor,
                style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            summary?.let {
                Text(
                    text = it,
                    color = summaryColor,
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    rotationZ = if (expanded) 180f else 0f
                },
            tint = summaryColor,
        )
    }
}

@Composable
private fun AudioOutputMenuDeviceRow(
    title: String,
    summary: String?,
    icon: ImageVector,
    selected: Boolean,
    contentColor: Color,
    summaryColor: Color,
    selectedColor: Color,
    selectedContainerColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) selectedContainerColor else Color.Transparent)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            tint = if (selected) selectedColor else contentColor,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = if (selected) selectedColor else contentColor,
                style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            summary?.let {
                Text(
                    text = it,
                    color = if (selected) Color.White.copy(alpha = 0.72f) else summaryColor,
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = selectedColor,
            )
        }
    }
}

@Composable
private fun AudioOutputMenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(1.dp)
            .background(Color(0xFF596070).copy(alpha = 0.36f)),
    )
}

@Composable
private fun BottomMaterialIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    buttonSize: Dp = 44.dp,
    iconSize: Dp = 28.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 560f),
        label = "music_bottom_button_scale",
    )
    Box(
        modifier = Modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = Color(0xFFF0F1FA).copy(alpha = 0.84f),
        )
    }
}

@Composable
private fun TransportControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    buttonSize: Dp,
    iconSize: Dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.07f else 1f,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 620f),
        label = "music_transport_button_scale",
    )
    Box(
        modifier = Modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = Color.White,
        )
    }
}

@Composable
private fun LyricsPage(
    state: MusicPlayerUiState,
    onSeekToLyric: (Int) -> Unit,
    onRetryLyrics: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val artwork = rememberArtworkBitmap(state.artworkBytes)
    val animatedPositionState = remember { mutableLongStateOf(0L) }
    val currentPositionProvider = remember {
        { animatedPositionState.longValue.toInt() }
    }

    LaunchedEffect(
        state.playbackState.isPlaying,
        state.playbackState.positionMs,
        state.playbackState.durationMs,
        state.playbackState.lastUpdateTime,
    ) {
        if (state.playbackState.isPlaying) {
            while (true) {
                val elapsed = System.currentTimeMillis() - state.playbackState.lastUpdateTime
                animatedPositionState.longValue = (
                    state.playbackState.positionMs + elapsed
                    ).coerceAtMost(state.playbackState.durationMs)
                awaitFrame()
            }
        } else {
            animatedPositionState.longValue = state.playbackState.positionMs
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding + 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 28.dp)
                .padding(top = 28.dp)
                .fillMaxWidth(),
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                artwork?.let { image ->
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        modifier = Modifier
                            .clip(ContinuousRoundedRectangle(6.dp))
                            .border(
                                1.dp,
                                Color.White.copy(0.2f),
                                ContinuousRoundedRectangle(6.dp),
                            )
                            .size(60.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
                PlayerMetadata(
                    title = state.title,
                    artist = state.artist ?: stringResource(R.string.music_player_unknown_artist),
                )
            }
            Spacer(Modifier.width(8.dp))
            PlayerControls(
                onOpenDetails = onOpenDetails,
            )
        }

        when {
            state.isLoadingLyrics -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    InfiniteProgressIndicator()
                }
            }

            state.lyrics != null && state.lyrics.lines.isNotEmpty() -> {
                PlayerLyrics(
                    listState = listState,
                    lyrics = state.lyrics,
                    currentPosition = currentPositionProvider,
                    showTranslation = state.showTranslation,
                    showPhonetic = state.showPhonetic,
                    onSeekTo = onSeekToLyric,
                    onShare = {},
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .weight(1f),
                )
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        modifier = Modifier
                            .widthIn(max = 360.dp),
                        cornerRadius = 22.dp,
                        colors = CardDefaults.defaultColors(
                            color = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Notes,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                                tint = Color.White,
                            )
                            Text(
                                text = stringResource(R.string.music_player_no_lyrics),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                style = MiuixTheme.textStyles.title4,
                            )
                            Text(
                                text = stringResource(R.string.music_player_no_lyrics_summary),
                                color = Color.White.copy(alpha = 0.72f),
                                textAlign = TextAlign.Center,
                                style = MiuixTheme.textStyles.body2,
                            )
                            Button(onClick = onRetryLyrics) {
                                Text(stringResource(R.string.music_player_retry_lyrics))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerMetadata(
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.graphicsLayer {
            blendMode = BlendMode.Plus
            compositingStrategy = CompositingStrategy.Offscreen
        },
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MiuixTheme.textStyles.title4.copy(
                fontWeight = FontWeight.Bold,
                textMotion = TextMotion.Animated,
            ),
            modifier = Modifier.basicMarquee(
                spacing = MarqueeSpacing(20.dp),
                repeatDelayMillis = 2000,
            ),
            maxLines = 1,
        )
        Text(
            text = artist,
            color = Color.White,
            style = MiuixTheme.textStyles.body2.copy(
                textMotion = TextMotion.Animated,
                lineHeight = 1.em,
            ),
            modifier = Modifier
                .alpha(0.4f)
                .basicMarquee(
                    spacing = MarqueeSpacing(20.dp),
                    repeatDelayMillis = 2000,
                ),
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayerControls(
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.graphicsLayer {
            blendMode = BlendMode.Plus
        },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(Color.White.copy(0.2f))
                .clickable(onClick = onOpenDetails)
                .padding(4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ellipsis),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.Center),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun PlayerLyrics(
    listState: androidx.compose.foundation.lazy.LazyListState,
    lyrics: com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics?,
    currentPosition: () -> Int,
    showTranslation: Boolean,
    showPhonetic: Boolean,
    onSeekTo: (Int) -> Unit,
    onShare: (KaraokeLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lyrics == null) return

    val currentTextStyle = MiuixTheme.textStyles.title4
    val sf = SFPro()
    val normalStyle = remember(currentTextStyle) {
        currentTextStyle.copy(
            fontSize = 34.sp,
            fontFamily = sf,
            fontWeight = FontWeight.ExtraBold,
            shadow = Shadow(
                color = Color.White.copy(alpha = 0.34f),
                offset = Offset.Zero,
                blurRadius = 18f,
            ),
            textMotion = TextMotion.Animated,
        )
    }
    val accompanimentStyle = remember(currentTextStyle) {
        currentTextStyle.copy(
            fontSize = 20.sp,
            fontFamily = sf,
            fontWeight = FontWeight.ExtraBold,
            shadow = Shadow(
                color = Color.White.copy(alpha = 0.26f),
                offset = Offset.Zero,
                blurRadius = 12f,
            ),
            textMotion = TextMotion.Animated,
        )
    }

    KaraokeLyricsView(
        listState = listState,
        lyrics = lyrics,
        currentPosition = currentPosition,
        onLineClicked = { line -> onSeekTo(line.start) },
        onLinePressed = { line -> onShare(line as KaraokeLine) },
        showTranslation = showTranslation,
        showPhonetic = showPhonetic,
        normalLineTextStyle = normalStyle,
        accompanimentLineTextStyle = accompanimentStyle,
        phoneticTextStyle = normalStyle.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            shadow = Shadow(
                color = Color.White.copy(alpha = 0.20f),
                offset = Offset.Zero,
                blurRadius = 8f,
            ),
        ),
        textColor = Color.White,
        blendMode = BlendMode.Plus,
        modifier = modifier.graphicsLayer {
            blendMode = BlendMode.Plus
            compositingStrategy = CompositingStrategy.Offscreen
        },
        useBlurEffect = false,
    )
}

private fun SFPro(): FontFamily = FontFamily(
    Font(R.font.sf_pro, FontWeight.ExtraLight),
    Font(R.font.sf_pro, FontWeight.Light),
    Font(R.font.sf_pro, FontWeight.Medium),
    Font(R.font.sf_pro, FontWeight.SemiBold),
    Font(R.font.sf_pro, FontWeight.Bold),
    Font(R.font.sf_pro, FontWeight.ExtraBold),
)

@Composable
private fun ArtworkPanel(
    artwork: androidx.compose.ui.graphics.ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val artworkRadius = 10.dp
    val artworkShape = ContinuousRoundedRectangle(artworkRadius)
    Card(
        modifier = modifier,
        cornerRadius = artworkRadius,
        colors = CardDefaults.defaultColors(
            color = Color.White.copy(alpha = 0.10f),
            contentColor = Color.White,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(artworkShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.05f),
                            Color.Black.copy(alpha = 0.12f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = MiuixIcons.Regular.Music,
                    contentDescription = null,
                    modifier = Modifier.size(86.dp),
                    tint = Color.White.copy(alpha = 0.92f),
                )
            }
        }
    }
}

@Composable
private fun PlayerCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    buttonSize: androidx.compose.ui.unit.Dp = 44.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    tint: Color = Color.White,
    backgroundColor: Color = Color.White.copy(alpha = 0.16f),
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.size(buttonSize),
        onClick = onClick,
        cornerRadius = buttonSize / 2f,
        colors = CardDefaults.defaultColors(
            color = backgroundColor,
            contentColor = tint,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = tint,
            )
        }
    }
}

@Composable
private fun FloatingButtonBar(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal,
    content: @Composable RowScope.() -> Unit,
) {
    Card(
        modifier = modifier.widthIn(max = 260.dp),
        cornerRadius = 28.dp,
        colors = CardDefaults.defaultColors(
            color = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun FloatingBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(
        modifier = Modifier.size(48.dp),
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = Color.White,
        )
    }
}

@Composable
private fun ToggleChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(
            color = if (active) Color.White else Color.White.copy(alpha = 0.12f),
            contentColor = if (active) Color(0xFF15171B) else Color.White,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (active) Color(0xFF15171B) else Color.White,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PlaybackSlider(
    value: Float,
    onValueChange: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 6.dp,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.24f),
    onValueChangeFinished: ((Float) -> Unit)? = null,
) {
    val view = LocalView.current
    val targetValue = value.coerceIn(0f, 1f)
    var localValue by remember { mutableFloatStateOf(targetValue) }
    var isInteracting by remember { mutableStateOf(false) }
    var pointerDown by remember { mutableStateOf(false) }
    var lastHapticValue by remember { mutableFloatStateOf(Float.NaN) }
    var lastHapticTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(targetValue, isInteracting) {
        if (!isInteracting) {
            localValue = targetValue
        }
    }
    val displayedValue = if (isInteracting) localValue else targetValue
    val scale by animateFloatAsState(
        targetValue = if (pointerDown || isInteracting) 1.045f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        label = "music_slider_scale",
    )
    fun performDragHaptic(fraction: Float, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (
            force ||
            lastHapticValue.isNaN() ||
            abs(fraction - lastHapticValue) >= 0.035f ||
            now - lastHapticTime >= 90L
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            lastHapticValue = fraction
            lastHapticTime = now
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        pointerDown = event.changes.any { it.pressed }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = displayedValue,
            onValueChange = { fraction ->
                val normalized = fraction.coerceIn(0f, 1f)
                val wasInteracting = isInteracting
                isInteracting = true
                localValue = normalized
                performDragHaptic(normalized, force = !wasInteracting)
                onValueChange?.invoke(normalized)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            valueRange = 0f..1f,
            onValueChangeFinished = {
                val normalized = localValue.coerceIn(0f, 1f)
                isInteracting = false
                pointerDown = false
                lastHapticValue = Float.NaN
                onValueChangeFinished?.invoke(normalized)
            },
            height = trackHeight,
            colors = SliderDefaults.sliderColors(
                foregroundColor = activeColor,
                backgroundColor = inactiveColor,
                thumbColor = activeColor,
                keyPointColor = inactiveColor,
                keyPointForegroundColor = activeColor,
            ),
            hapticEffect = SliderDefaults.SliderHapticEffect.None,
        )
    }
}

@Composable
private fun BoxScope.MusicBackdrop(state: MusicPlayerUiState) {
    val fallbackPalette = listOf(
        Color(0xFF294B68),
        Color(0xFF1C3852),
        Color(0xFF132537),
    )
    val palette = if (state.palette.isNotEmpty()) state.palette else fallbackPalette
    val blurBitmap = rememberArtworkBitmap(state.artworkBytes)
    val transition = rememberInfiniteTransition(label = "music_flowing_background")
    val artworkDriftX by transition.animateFloat(
        initialValue = -0.12f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "music_background_artwork_drift_x",
    )
    val artworkDriftY by transition.animateFloat(
        initialValue = -0.08f,
        targetValue = 0.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 23_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "music_background_artwork_drift_y",
    )
    val glowOneX by transition.animateFloat(
        initialValue = -0.10f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "music_background_glow_one_x",
    )
    val glowOneY by transition.animateFloat(
        initialValue = -0.06f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 21_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "music_background_glow_one_y",
    )
    val glowTwoX by transition.animateFloat(
        initialValue = 0.09f,
        targetValue = -0.11f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 19_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "music_background_glow_two_x",
    )
    val glowTwoY by transition.animateFloat(
        initialValue = -0.05f,
        targetValue = 0.11f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "music_background_glow_two_y",
    )
    val glowScale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 17_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "music_background_glow_scale",
    )
    val sheenShift by transition.animateFloat(
        initialValue = -0.45f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "music_background_sheen_shift",
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette[0].copy(alpha = 0.98f),
                        palette.getOrElse(1) { palette[0] }.copy(alpha = 0.94f),
                        palette.getOrElse(2) { palette[0] }.copy(alpha = 1f),
                    ),
                ),
            ),
    ) {
        if (blurBitmap != null) {
            Image(
                bitmap = blurBitmap,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .offset(
                        x = (artworkDriftX * 26f).dp,
                        y = (artworkDriftY * 32f).dp,
                    )
                    .graphicsLayer {
                        scaleX = 1.18f
                        scaleY = 1.18f
                    }
                    .blur(126.dp),
                alpha = 0.18f,
                contentScale = ContentScale.Crop,
            )
        }
        Canvas(modifier = Modifier.matchParentSize()) {
            val firstColor = palette[0].copy(alpha = 0.24f)
            val secondColor = palette.getOrElse(1) { palette[0] }.copy(alpha = 0.18f)
            val thirdColor = palette.getOrElse(2) { palette[0] }.copy(alpha = 0.16f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(firstColor, Color.Transparent),
                    center = Offset(
                        size.width * (0.26f + glowOneX),
                        size.height * (0.18f + glowOneY),
                    ),
                    radius = size.minDimension * (0.50f * glowScale),
                ),
                radius = size.minDimension * (0.50f * glowScale),
                center = Offset(
                    size.width * (0.26f + glowOneX),
                    size.height * (0.18f + glowOneY),
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondColor, Color.Transparent),
                    center = Offset(
                        size.width * (0.78f + glowTwoX),
                        size.height * (0.42f + glowTwoY),
                    ),
                    radius = size.minDimension * (0.56f * (2f - glowScale)),
                ),
                radius = size.minDimension * (0.56f * (2f - glowScale)),
                center = Offset(
                    size.width * (0.78f + glowTwoX),
                    size.height * (0.42f + glowTwoY),
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(thirdColor, Color.Transparent),
                    center = Offset(
                        size.width * (0.52f - glowOneX * 0.45f),
                        size.height * (0.88f - glowTwoY * 0.35f),
                    ),
                    radius = size.minDimension * (0.60f + (glowScale - 1f) * 0.18f),
                ),
                radius = size.minDimension * (0.60f + (glowScale - 1f) * 0.18f),
                center = Offset(
                    size.width * (0.52f - glowOneX * 0.45f),
                    size.height * (0.88f - glowTwoY * 0.35f),
                ),
            )
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.05f),
                        palette[0].copy(alpha = 0.07f),
                        Color.Transparent,
                    ),
                    start = Offset(size.width * (sheenShift - 0.34f), 0f),
                    end = Offset(size.width * (sheenShift + 0.22f), size.height),
                ),
                blendMode = BlendMode.Screen,
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.08f)),
        )
    }
}

@Composable
private fun rememberArtworkBitmap(bytes: ByteArray?): androidx.compose.ui.graphics.ImageBitmap? {
    return remember(bytes?.contentHashCode()) {
        bytes?.let { artwork ->
            runCatching {
                BitmapFactory.decodeByteArray(artwork, 0, artwork.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun formatRemainingDuration(durationMs: Long, positionMs: Long): String {
    val remaining = (durationMs - positionMs).coerceAtLeast(0L)
    return "-${formatDuration(remaining)}"
}
