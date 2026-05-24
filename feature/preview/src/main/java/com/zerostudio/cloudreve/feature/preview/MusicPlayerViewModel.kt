package com.zerostudio.cloudreve.feature.preview

import android.Manifest
import android.content.ContentResolver
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.AudioDeviceInfo
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.karaoke.copy
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser
import com.zerostudio.cloudreve.core.common.DispatchersProvider
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.repository.CloudreveRepository
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class MusicPlayerArgs(
    val id: String,
    val uri: CloudreveUri,
    val parentUri: CloudreveUri,
    val name: String,
    val size: Long,
    val mimeType: String?,
    val updatedAtEpochMillis: Long,
)

private const val SPEAKER_OUTPUT_ID = "speaker"
private const val HEADSET_OUTPUT_ID_PREFIX = "headset:"

data class MusicPlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastUpdateTime: Long = 0L,
)

enum class MusicAudioOutputType {
    Speaker,
    Headset,
}

data class MusicAudioOutputDevice(
    val id: String,
    val name: String,
    val type: MusicAudioOutputType,
    val selected: Boolean = false,
)

data class MusicPlayerUiState(
    val args: MusicPlayerArgs,
    val title: String = args.displayTitle(),
    val artist: String? = args.displayArtist(),
    val playbackState: MusicPlaybackState = MusicPlaybackState(),
    val volumeFraction: Float = 0.5f,
    val controllerReady: Boolean = false,
    val isLoadingLyrics: Boolean = true,
    val lyrics: SyncedLyrics? = null,
    val lyricsSourceName: String? = null,
    val artworkBytes: ByteArray? = null,
    val palette: List<Color> = emptyList(),
    val errorMessage: String? = null,
    val audioOutputDevices: List<MusicAudioOutputDevice> = emptyList(),
    val audioOutputError: String? = null,
    val showLyricsPage: Boolean = false,
    val showTranslation: Boolean = true,
    val showPhonetic: Boolean = false,
)

private data class MusicPlayerUiMemorySnapshot(
    val showLyricsPage: Boolean = false,
    val showTranslation: Boolean = true,
    val showPhonetic: Boolean = false,
    val preferredAudioOutputId: String = SPEAKER_OUTPUT_ID,
)

private object MusicPlayerUiStateMemory {
    private val snapshots = mutableMapOf<String, MusicPlayerUiMemorySnapshot>()

    @Synchronized
    fun snapshot(key: String): MusicPlayerUiMemorySnapshot = snapshots[key] ?: MusicPlayerUiMemorySnapshot()

    @Synchronized
    fun save(key: String, snapshot: MusicPlayerUiMemorySnapshot) {
        snapshots[key] = snapshot
    }
}

class MusicPlayerViewModel(
    private val repository: CloudreveRepository,
    private val appContext: Context,
    private val authenticatedClient: OkHttpClient,
    private val dispatchers: DispatchersProvider,
    args: MusicPlayerArgs,
) : ViewModel() {
    private var memoryKey = args.uri.value
    private val initialUiSnapshot = MusicPlayerUiStateMemory.snapshot(memoryKey)
    private val _state = MutableStateFlow(
        MusicPlayerUiState(
            args = args,
            showLyricsPage = initialUiSnapshot.showLyricsPage,
            showTranslation = initialUiSnapshot.showTranslation,
            showPhonetic = initialUiSnapshot.showPhonetic,
        ),
    )
    val state: StateFlow<MusicPlayerUiState> = _state.asStateFlow()

    private val autoParser = AutoParser()
    private val audioManager by lazy {
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val contentResolver: ContentResolver by lazy {
        appContext.contentResolver
    }
    private val publicClient by lazy { OkHttpClient.Builder().build() }
    private var mediaController: MediaController? = null
    private var lastArtworkBytes: ByteArray? = null
    private var preparePlaybackJob: Job? = null
    private var lyricsJob: Job? = null
    private var positionUpdateJob: Job? = null
    private var preferredAudioOutputId: String = initialUiSnapshot.preferredAudioOutputId
    private val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshSystemVolume()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackState()
            if (isPlaying) {
                startPositionUpdates()
            } else {
                stopPositionUpdates()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackState()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY)
            ) {
                updatePlaybackState()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(
                    errorMessage = error.message?.takeIf(String::isNotBlank),
                    playbackState = it.playbackState.copy(isBuffering = false),
                )
            }
        }
    }

    init {
        refreshSystemVolume()
        registerVolumeObserver()
        refreshAudioOutputs()
        connectController()
        loadLyrics()
    }

    fun updateArgs(args: MusicPlayerArgs) {
        val current = _state.value.args
        if (current == args) return
        if (current.uri == args.uri) {
            _state.update {
                it.copy(
                    args = args,
                    title = args.displayTitle(),
                    artist = args.displayArtist(),
                )
            }
            return
        }

        persistUiState()
        val nextSnapshot = MusicPlayerUiStateMemory.snapshot(args.uri.value)
        memoryKey = args.uri.value
        preferredAudioOutputId = nextSnapshot.preferredAudioOutputId
        preparePlaybackJob?.cancel()
        lyricsJob?.cancel()
        stopPositionUpdates()
        lastArtworkBytes = null

        val previous = _state.value
        _state.value = MusicPlayerUiState(
            args = args,
            title = args.displayTitle(),
            artist = args.displayArtist(),
            volumeFraction = previous.volumeFraction,
            controllerReady = previous.controllerReady,
            showLyricsPage = nextSnapshot.showLyricsPage,
            showTranslation = nextSnapshot.showTranslation,
            showPhonetic = nextSnapshot.showPhonetic,
            audioOutputDevices = previous.audioOutputDevices,
        )
        refreshAudioOutputs()
        mediaController?.let(::preparePlayback)
        loadLyrics()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun seekBy(deltaMs: Long) {
        val controller = mediaController ?: return
        val duration = controller.duration.takeIf { it > 0L } ?: _state.value.playbackState.durationMs
        val nextPosition = (controller.currentPosition + deltaMs).coerceIn(0L, duration.coerceAtLeast(0L))
        controller.seekTo(nextPosition)
        updatePlaybackState()
    }

    fun seekToFraction(fraction: Float) {
        val controller = mediaController ?: return
        val duration = controller.duration.takeIf { it > 0L } ?: _state.value.playbackState.durationMs
        if (duration <= 0L) return
        controller.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        updatePlaybackState()
    }

    fun seekTo(positionMs: Int) {
        mediaController?.seekTo(positionMs.toLong())
        updatePlaybackState()
    }

    fun setVolumeFraction(fraction: Float) {
        val normalized = fraction.coerceIn(0f, 1f)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val targetVolume = (normalized * maxVolume).roundToInt().coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        _state.update { it.copy(volumeFraction = targetVolume.toFloat() / maxVolume.toFloat()) }
    }

    fun setShowLyricsPage(show: Boolean) {
        _state.update { it.copy(showLyricsPage = show) }
        persistUiState()
    }

    fun refreshAudioOutputs() {
        viewModelScope.launch(dispatchers.default) {
            val devices = buildAudioOutputDevices()
            _state.update {
                it.copy(
                    audioOutputDevices = devices,
                    audioOutputError = null,
                )
            }
        }
    }

    fun selectAudioOutput(id: String) {
        viewModelScope.launch(dispatchers.main) {
            val outputInfos = withContext(dispatchers.default) { loadOutputDevices() }
            val target = when {
                id == SPEAKER_OUTPUT_ID -> outputInfos.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                id.startsWith(HEADSET_OUTPUT_ID_PREFIX) -> {
                    val groupKey = id.removePrefix(HEADSET_OUTPUT_ID_PREFIX)
                    outputInfos
                        .filter { it.isHeadsetOutput() && it.headsetGroupKey() == groupKey }
                        .selectPreferredHeadsetOutputOrNull()
                }
                else -> outputInfos.firstOrNull { it.outputId == id && it.isHeadsetOutput() }
            }
            if (id != SPEAKER_OUTPUT_ID && isBluetoothOutput(target?.type) && !hasBluetoothConnectPermission()) {
                _state.update {
                    it.copy(audioOutputError = appContext.getString(R.string.music_player_audio_output_permission_required))
                }
                return@launch
            }
            if (id != SPEAKER_OUTPUT_ID && target == null) {
                _state.update {
                    it.copy(audioOutputError = appContext.getString(R.string.music_player_audio_output_failed))
                }
                return@launch
            }
            preferredAudioOutputId = when {
                id == SPEAKER_OUTPUT_ID -> SPEAKER_OUTPUT_ID
                id.startsWith(HEADSET_OUTPUT_ID_PREFIX) -> id
                target != null -> target.groupedHeadsetOutputId()
                else -> id
            }
            persistUiState()
            CloudreveAudioOutputRouter.setPreferredAudioDevice(target)
            runCatching {
                if (target != null) {
                    audioManager.setCommunicationDevice(target)
                } else {
                    audioManager.clearCommunicationDevice()
                }
            }
            val devices = withContext(dispatchers.default) { buildAudioOutputDevices() }
            _state.update {
                it.copy(
                    audioOutputDevices = devices,
                    audioOutputError = null,
                )
            }
        }
    }

    fun retryLyrics() {
        loadLyrics(forceRefresh = true)
    }

    fun toggleTranslation() {
        _state.update { it.copy(showTranslation = !it.showTranslation) }
        persistUiState()
    }

    fun togglePhonetic() {
        _state.update { it.copy(showPhonetic = !it.showPhonetic) }
        persistUiState()
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun buildAudioOutputDevices(): List<MusicAudioOutputDevice> {
        val outputInfos = loadOutputDevices()
        val speaker = outputInfos.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val headsetDevices = outputInfos
            .asSequence()
            .filter { it.isHeadsetOutput() }
            .filterNot { isBluetoothOutput(it.type) && !hasBluetoothConnectPermission() }
            .groupBy { it.headsetGroupKey() }
            .mapNotNull { (_, infos) ->
                val info = infos.selectPreferredHeadsetOutputOrNull() ?: return@mapNotNull null
                val outputId = info.groupedHeadsetOutputId()
                MusicAudioOutputDevice(
                    id = outputId,
                    name = info.safeProductName()
                        ?: appContext.getString(R.string.music_player_audio_output_headset),
                    type = MusicAudioOutputType.Headset,
                    selected = preferredAudioOutputId == outputId || infos.any { preferredAudioOutputId == it.outputId },
                )
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .toList()
        val selectedId = preferredAudioOutputId
        return buildList {
            add(
                MusicAudioOutputDevice(
                    id = SPEAKER_OUTPUT_ID,
                    name = speaker?.safeProductName()
                        ?: appContext.getString(R.string.music_player_audio_output_speaker),
                    type = MusicAudioOutputType.Speaker,
                    selected = selectedId == SPEAKER_OUTPUT_ID || headsetDevices.none { it.selected },
                ),
            )
            addAll(headsetDevices)
        }
    }

    private fun loadOutputDevices(): List<AudioDeviceInfo> =
        runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }.getOrDefault(emptyList())

    private fun AudioDeviceInfo.safeProductName(): String? =
        runCatching {
            productName?.toString()?.takeIf { it.isNotBlank() }
        }.getOrNull()

    private fun AudioDeviceInfo.safeAddress(): String? =
        runCatching {
            address
                .takeIf { it.isNotBlank() }
                ?.takeIf { it != "00:00:00:00:00:00" }
        }.getOrNull()

    private fun AudioDeviceInfo.groupedHeadsetOutputId(): String =
        "$HEADSET_OUTPUT_ID_PREFIX${headsetGroupKey()}"

    private fun AudioDeviceInfo.headsetGroupKey(): String {
        val normalizedName = safeProductName()
            ?.lowercase(Locale.ROOT)
            ?.replace(headsetDescriptorRegex, " ")
            ?.replace(nonNameRegex, " ")
            ?.trim()
            ?.replace(whitespaceRegex, " ")
            ?.takeIf { it.isNotBlank() }
        if (isBluetoothOutput(type)) {
            return "bt:${normalizedName ?: safeAddress()?.lowercase(Locale.ROOT) ?: "type-$type"}"
        }
        val family = when {
            isBluetoothOutput(type) -> "bt"
            type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                type == AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired"
            type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                type == AudioDeviceInfo.TYPE_USB_DEVICE -> "usb"
            else -> "other"
        }
        return "$family:${normalizedName ?: "type-$type"}"
    }

    private fun List<AudioDeviceInfo>.selectPreferredHeadsetOutputOrNull(): AudioDeviceInfo? =
        minWithOrNull(
            compareBy<AudioDeviceInfo> { it.mediaOutputPriority() }
                .thenBy { it.id },
        )

    private fun AudioDeviceInfo.mediaOutputPriority(): Int = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 0
        AudioDeviceInfo.TYPE_BLE_HEADSET -> 1
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> 2
        AudioDeviceInfo.TYPE_USB_HEADSET -> 3
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 4
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> 5
        AudioDeviceInfo.TYPE_USB_DEVICE -> 6
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 20
        else -> 50
    }

    private fun hasBluetoothConnectPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun persistUiState() {
        val snapshot = _state.value
        MusicPlayerUiStateMemory.save(
            memoryKey,
            MusicPlayerUiMemorySnapshot(
                showLyricsPage = snapshot.showLyricsPage,
                showTranslation = snapshot.showTranslation,
                showPhonetic = snapshot.showPhonetic,
                preferredAudioOutputId = preferredAudioOutputId,
            ),
        )
    }

    private fun registerVolumeObserver() {
        runCatching {
            contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
        }
    }

    private fun refreshSystemVolume() {
        runCatching {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVolume)
            currentVolume.toFloat() / maxVolume.toFloat()
        }.onSuccess { fraction ->
            _state.update { it.copy(volumeFraction = fraction) }
        }
    }

    private fun connectController() {
        viewModelScope.launch(dispatchers.main) {
            runCatching {
                val token = SessionToken(appContext, ComponentName(appContext, CloudrevePlaybackService::class.java))
                MediaController.Builder(appContext, token).buildAsync().await()
            }.onSuccess { controller ->
                mediaController = controller
                controller.addListener(playerListener)
                _state.update { it.copy(controllerReady = true) }
                preparePlayback(controller)
                updatePlaybackState()
                if (controller.isPlaying) {
                    startPositionUpdates()
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        controllerReady = false,
                        errorMessage = error.message?.takeIf(String::isNotBlank),
                    )
                }
            }
        }
    }

    private fun preparePlayback(controller: MediaController) {
        preparePlaybackJob?.cancel()
        preparePlaybackJob = viewModelScope.launch(dispatchers.io) {
            val snapshot = _state.value.args
            runCatching {
                val title = snapshot.displayTitle()
                val artist = snapshot.displayArtist()
                _state.update { current ->
                    if (current.args.uri == snapshot.uri) {
                        current.copy(
                            title = title,
                            artist = artist,
                            artworkBytes = null,
                            palette = emptyList(),
                            playbackState = current.playbackState.copy(
                                positionMs = 0L,
                                durationMs = 0L,
                                isBuffering = true,
                                lastUpdateTime = System.currentTimeMillis(),
                            ),
                        )
                    } else {
                        current
                    }
                }
                val downloadUrl = repository.createDownloadUrl(snapshot.uri)
                val artworkBytes = loadArtworkBytes(snapshot.uri)
                MusicLiveUpdateLyricsRegistry.put(snapshot.uri.value, _state.value.lyrics)
                val metadataBuilder = MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setSubtitle(snapshot.name)
                    .setExtras(_state.value.lyrics.toMusicLiveUpdateExtras())
                if (artworkBytes != null) {
                    metadataBuilder.setArtworkData(
                        artworkBytes,
                        MediaMetadata.PICTURE_TYPE_FRONT_COVER,
                    )
                    updateArtwork(artworkBytes)
                }
                val metadata = metadataBuilder.build()
                val mediaItem = MediaItem.Builder()
                    .setMediaId(snapshot.uri.value)
                    .setUri(downloadUrl)
                    .setMediaMetadata(metadata)
                    .build()
                withContext(dispatchers.main) {
                    if (_state.value.args.uri != snapshot.uri) return@withContext
                    if (controller.currentMediaItem?.mediaId != mediaItem.mediaId) {
                        controller.setMediaItem(mediaItem)
                        controller.prepare()
                        controller.play()
                    } else if (controller.playbackState == Player.STATE_IDLE) {
                        controller.prepare()
                    }
                    if (controller.isPlaying) {
                        startPositionUpdates()
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (_state.value.args.uri == snapshot.uri) {
                    _state.update {
                        it.copy(
                            errorMessage = error.message?.takeIf(String::isNotBlank),
                            playbackState = it.playbackState.copy(isBuffering = false),
                        )
                    }
                }
            }
        }
    }

    private fun updatePlaybackState() {
        val controller = mediaController ?: return
        val snapshot = _state.value.args
        val metadataMatchesCurrentSong = controller.currentMediaItem?.mediaId == snapshot.uri.value
        val playbackState = MusicPlaybackState(
            isPlaying = controller.isPlaying,
            isBuffering = controller.playbackState == Player.STATE_BUFFERING || controller.playbackState == Player.STATE_IDLE,
            positionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = controller.duration.takeIf { it > 0L } ?: 0L,
            lastUpdateTime = System.currentTimeMillis(),
        )
        val metadata = controller.mediaMetadata
        val title = if (metadataMatchesCurrentSong) {
            metadata.title?.toString()?.takeIf(String::isNotBlank) ?: snapshot.displayTitle()
        } else {
            snapshot.displayTitle()
        }
        val artist = if (metadataMatchesCurrentSong) {
            metadata.artist?.toString()?.takeIf(String::isNotBlank) ?: snapshot.displayArtist()
        } else {
            snapshot.displayArtist()
        }
        if (metadataMatchesCurrentSong) {
            metadata.artworkData?.let(::updateArtwork)
        }
        _state.update {
            it.copy(
                title = title,
                artist = artist,
                playbackState = playbackState,
            )
        }
    }

    private fun updateArtwork(bytes: ByteArray?) {
        if (bytes.contentEquals(lastArtworkBytes)) return
        lastArtworkBytes = bytes?.clone()
        if (bytes == null) {
            _state.update { it.copy(artworkBytes = null, palette = emptyList()) }
            return
        }
        viewModelScope.launch(dispatchers.default) {
            val palette = runCatching { extractPalette(bytes) }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    artworkBytes = bytes,
                    palette = palette,
                )
            }
        }
    }

    private fun loadLyrics(forceRefresh: Boolean = false) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch(dispatchers.io) {
            val snapshot = _state.value.args
            _state.update { current ->
                if (current.args.uri == snapshot.uri) current.copy(isLoadingLyrics = true) else current
            }
            runCatching {
                if (forceRefresh) {
                    repository.refreshFiles(snapshot.parentUri)
                } else {
                    runCatching { repository.refreshFiles(snapshot.parentUri) }
                }
                val siblings = repository.listFiles(snapshot.parentUri).first()
                val candidates = selectLyricsCandidates(snapshot.name, siblings)
                val mainLyric = candidates.main ?: return@runCatching null
                val primaryText = downloadText(repository.createDownloadUrl(mainLyric.uri))
                val parsedPrimary = autoParser.parse(primaryText)
                val merged = candidates.translation?.let { translationNode ->
                    val translationText = downloadText(repository.createDownloadUrl(translationNode.uri))
                    mergeTranslations(parsedPrimary, autoParser.parse(translationText))
                } ?: parsedPrimary
                LyricsLoadResult(lyrics = merged.enhanceForKaraoke(), sourceName = mainLyric.name)
            }.onSuccess { result ->
                if (_state.value.args.uri == snapshot.uri) {
                    _state.update {
                        it.copy(
                            isLoadingLyrics = false,
                            lyrics = result?.lyrics,
                            lyricsSourceName = result?.sourceName,
                        )
                    }
                    viewModelScope.launch(dispatchers.main) {
                        publishLiveUpdateLyrics(snapshot.uri, result?.lyrics)
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (_state.value.args.uri == snapshot.uri) {
                    _state.update {
                        it.copy(
                            isLoadingLyrics = false,
                            lyrics = null,
                            lyricsSourceName = null,
                            errorMessage = error.message?.takeIf(String::isNotBlank),
                        )
                    }
                    viewModelScope.launch(dispatchers.main) {
                        publishLiveUpdateLyrics(snapshot.uri, null)
                    }
                }
            }
        }
    }

    private fun publishLiveUpdateLyrics(uri: CloudreveUri, lyrics: SyncedLyrics?) {
        MusicLiveUpdateLyricsRegistry.put(uri.value, lyrics)
        val controller = mediaController ?: return
        if (_state.value.args.uri != uri) return
        val currentItem = controller.currentMediaItem ?: return
        if (currentItem.mediaId != uri.value) return
        val metadata = controller.mediaMetadata
            .buildUpon()
            .setExtras(lyrics.toMusicLiveUpdateExtras())
            .build()
        val updatedItem = currentItem
            .buildUpon()
            .setMediaMetadata(metadata)
            .build()
        runCatching {
            controller.replaceMediaItem(controller.currentMediaItemIndex.coerceAtLeast(0), updatedItem)
        }
    }

    private fun selectLyricsCandidates(
        audioName: String,
        siblings: List<com.zerostudio.cloudreve.core.domain.model.FileNode>,
    ): LyricsCandidates {
        val baseName = audioName.substringBeforeLast('.', audioName).lowercase(Locale.ROOT)
        val lyricsFiles = siblings.filter { node ->
            node.name.substringAfterLast('.', "").lowercase(Locale.ROOT) in lyricExtensions
        }
        val main = lyricsFiles
            .filterNot { isTranslationName(it.name, baseName) }
            .maxByOrNull { scoreLyricCandidate(it.name, baseName) }
            ?.takeIf { scoreLyricCandidate(it.name, baseName) > 0 }
        val translation = lyricsFiles
            .filter { isTranslationName(it.name, baseName) }
            .maxByOrNull { scoreLyricCandidate(it.name, baseName) }
            ?.takeIf { scoreLyricCandidate(it.name, baseName) > 0 }
        return LyricsCandidates(main = main, translation = translation)
    }

    private fun scoreLyricCandidate(fileName: String, baseName: String): Int {
        val stem = fileName.substringBeforeLast('.', fileName).lowercase(Locale.ROOT)
        return when {
            stem == baseName -> 100
            stem.startsWith("$baseName ") -> 95
            stem.startsWith(baseName) -> 90
            stem.contains(baseName) -> 60
            else -> 0
        }
    }

    private fun isTranslationName(fileName: String, baseName: String): Boolean {
        val stem = fileName.substringBeforeLast('.', fileName).lowercase(Locale.ROOT)
        return translationSuffixes.any { suffix -> stem == "$baseName$suffix" || stem.startsWith("$baseName$suffix.") }
    }

    private fun mergeTranslations(primary: SyncedLyrics, translation: SyncedLyrics): SyncedLyrics {
        val translationMap = translation.lines.associate { line ->
            line.start to line.plainContent()
        }
        return primary.copy(
            lines = primary.lines.map { line ->
                val translated = translationMap[line.start]?.takeIf(String::isNotBlank)
                when (line) {
                    is KaraokeLine -> line.copy(translation = translated ?: line.translation)
                    is SyncedLine -> line.copy(translation = translated ?: line.translation)
                    else -> line
                }
            },
        )
    }

    private fun SyncedLyrics.enhanceForKaraoke(): SyncedLyrics {
        if (lines.isEmpty()) return this
        return copy(
            lines = lines.mapIndexed { index, line ->
                val nextStart = lines.getOrNull(index + 1)?.start
                when (line) {
                    is KaraokeLine -> line.enhanceCjkSyllables()
                    is SyncedLine -> line.toSyntheticKaraokeLine(nextStart)
                    else -> line
                }
            },
        )
    }

    private fun SyncedLine.toSyntheticKaraokeLine(nextStart: Int?): KaraokeLine {
        val fallbackEnd = nextStart ?: (start + 3_200)
        val safeEnd = end.takeIf { it > start } ?: fallbackEnd.takeIf { it > start } ?: (start + 1_200)
        val units = content.toKaraokeUnits()
        return KaraokeLine.MainKaraokeLine(
            syllables = units.toTimedSyllables(
                lineStart = start,
                lineEnd = safeEnd,
            ),
            translation = translation,
            alignment = KaraokeAlignment.Unspecified,
            start = start,
            end = safeEnd,
        )
    }

    private fun KaraokeLine.enhanceCjkSyllables(): KaraokeLine {
        val enhanced = syllables.flatMap { syllable ->
            val units = syllable.content.toKaraokeUnits(splitLatinWords = false)
            if (units.size <= 1) {
                listOf(syllable)
            } else {
                units.toTimedSyllables(
                    lineStart = syllable.start,
                    lineEnd = syllable.end.takeIf { it > syllable.start } ?: (syllable.start + units.size * 120),
                    phonetic = syllable.phonetic,
                )
            }
        }
        return if (enhanced == syllables) this else copy(syllables = enhanced)
    }

    private fun List<String>.toTimedSyllables(
        lineStart: Int,
        lineEnd: Int,
        phonetic: String? = null,
    ): List<KaraokeSyllable> {
        val visibleUnits = if (isEmpty()) listOf("") else this
        val duration = (lineEnd - lineStart).coerceAtLeast(visibleUnits.size)
        return visibleUnits.mapIndexed { index, content ->
            val startOffset = ((duration.toLong() * index) / visibleUnits.size).toInt()
            val endOffset = ((duration.toLong() * (index + 1)) / visibleUnits.size).toInt()
            KaraokeSyllable(
                content = content,
                start = lineStart + startOffset,
                end = (lineStart + endOffset).coerceAtLeast(lineStart + startOffset + 1),
                phonetic = phonetic.takeIf { visibleUnits.size == 1 },
            )
        }
    }

    private fun String.toKaraokeUnits(splitLatinWords: Boolean = true): List<String> {
        if (isBlank()) return listOf(this)
        val units = mutableListOf<String>()
        val buffer = StringBuilder()
        fun flushBuffer() {
            if (buffer.isNotEmpty()) {
                units.add(buffer.toString())
                buffer.clear()
            }
        }

        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            val char = String(Character.toChars(codePoint))
            val charType = Character.getType(codePoint)
            when {
                codePoint.isCjkCodePoint() -> {
                    flushBuffer()
                    units.add(char)
                }
                charType == Character.CONNECTOR_PUNCTUATION.toInt() ||
                    charType == Character.DASH_PUNCTUATION.toInt() ||
                    charType == Character.END_PUNCTUATION.toInt() ||
                    charType == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
                    charType == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
                    charType == Character.OTHER_PUNCTUATION.toInt() ||
                    charType == Character.START_PUNCTUATION.toInt() -> {
                    if (units.isNotEmpty() && buffer.isEmpty()) {
                        units[units.lastIndex] = units.last() + char
                    } else {
                        buffer.append(char)
                    }
                }
                Character.isWhitespace(codePoint) -> {
                    if (splitLatinWords) {
                        buffer.append(char)
                        flushBuffer()
                    } else {
                        buffer.append(char)
                    }
                }
                splitLatinWords -> buffer.append(char)
                else -> buffer.append(char)
            }
            index += Character.charCount(codePoint)
        }
        flushBuffer()
        return units.filter { it.isNotEmpty() }.ifEmpty { listOf(this) }
    }

    private suspend fun downloadText(url: String): String = withContext(dispatchers.io) {
        runCatching {
            downloadWithClient(authenticatedClient, url)
        }.recoverCatching { error ->
            if (error is PreviewDownloadException && error.code in setOf(401, 403)) {
                downloadWithClient(publicClient, url)
            } else {
                throw error
            }
        }.getOrThrow()
    }

    private suspend fun loadArtworkBytes(uri: CloudreveUri): ByteArray? = withContext(dispatchers.io) {
        withTimeoutOrNull(ARTWORK_LOAD_TIMEOUT_MILLIS) {
            runCatching {
                val thumbnailUrl = repository.createThumbnailUrl(uri)
                val request = Request.Builder()
                    .url(thumbnailUrl)
                    .build()
                authenticatedClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withTimeoutOrNull null
                    response.body
                        ?.bytes()
                        ?.takeIf { it.isNotEmpty() && it.size <= MAX_ARTWORK_BYTES }
                }
            }.getOrNull()
        }
    }

    private fun downloadWithClient(client: OkHttpClient, url: String): String {
        val request = Request.Builder()
            .url(url)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PreviewDownloadException(response.code)
            }
            return response.body?.string().orEmpty()
        }
    }

    private suspend fun extractPalette(bytes: ByteArray): List<Color> = withContext(dispatchers.default) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext emptyList()
        if (bitmap.width <= 0 || bitmap.height <= 0) return@withContext emptyList()
        val samplePoints = listOf(
            bitmap.getPixel(bitmap.width / 4, bitmap.height / 4),
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2),
            bitmap.getPixel((bitmap.width * 3) / 4, (bitmap.height * 3) / 4),
        )
        samplePoints.map { pixel ->
            val red = ((pixel shr 16) and 0xff) / 255f
            val green = ((pixel shr 8) and 0xff) / 255f
            val blue = (pixel and 0xff) / 255f
            Color(
                red = red.coerceIn(0.12f, 0.92f),
                green = green.coerceIn(0.08f, 0.88f),
                blue = blue.coerceIn(0.10f, 0.94f),
                alpha = 1f,
            )
        }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        val controller = mediaController ?: return
        positionUpdateJob = viewModelScope.launch(dispatchers.main) {
            while (isActive) {
                _state.update {
                    it.copy(
                        playbackState = it.playbackState.copy(
                            positionMs = controller.currentPosition.coerceAtLeast(0L),
                            durationMs = controller.duration.takeIf { value -> value > 0L }
                                ?: it.playbackState.durationMs,
                            lastUpdateTime = System.currentTimeMillis(),
                            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                        ),
                    )
                }
                kotlinx.coroutines.delay(250L)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    override fun onCleared() {
        persistUiState()
        preparePlaybackJob?.cancel()
        lyricsJob?.cancel()
        stopPositionUpdates()
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        mediaController = null
        runCatching {
            contentResolver.unregisterContentObserver(volumeObserver)
        }
        super.onCleared()
    }

    private data class LyricsCandidates(
        val main: com.zerostudio.cloudreve.core.domain.model.FileNode?,
        val translation: com.zerostudio.cloudreve.core.domain.model.FileNode?,
    )

    private data class LyricsLoadResult(
        val lyrics: SyncedLyrics,
        val sourceName: String,
    )

    private class PreviewDownloadException(val code: Int) :
        IllegalStateException("Music asset download failed: HTTP $code")

    private companion object {
        val lyricExtensions = setOf("lrc", "ttml", "lys", "krc", "qrc", "yrc", "vtt")
        val headsetDescriptorRegex =
            Regex("\\b(headset|headsets|headphone|headphones|earphone|earphones|earbud|earbuds|stereo|hands-free|handsfree|a2dp|sco)\\b")
        val nonNameRegex = Regex("[^\\p{L}\\p{N}]+")
        val whitespaceRegex = Regex("\\s+")
        const val ARTWORK_LOAD_TIMEOUT_MILLIS = 2_500L
        const val MAX_ARTWORK_BYTES = 2 * 1024 * 1024
        val translationSuffixes = listOf(
            "-translation",
            "_translation",
            ".translation",
            "-trans",
            "_trans",
            ".trans",
            "-zh",
            "_zh",
            ".zh",
            "-cn",
            "_cn",
            ".cn",
            "-chs",
            "_chs",
            ".chs",
            "-sc",
            "_sc",
            ".sc",
        )
    }
}

private val AudioDeviceInfo.outputId: String
    get() = "device:$id"

private fun AudioDeviceInfo.isHeadsetOutput(): Boolean = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    -> true
    else -> false
}

private fun isBluetoothOutput(type: Int?): Boolean = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    -> true
    else -> false
}

private fun Int.isCjkCodePoint(): Boolean = when (Character.UnicodeScript.of(this)) {
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.HANGUL,
    Character.UnicodeScript.BOPOMOFO,
    -> true
    else -> false
}

private fun MusicPlayerArgs.displayTitle(): String {
    val baseName = name.substringBeforeLast('.', name)
    return baseName.substringAfter(" - ", baseName).ifBlank { name }
}

private fun MusicPlayerArgs.displayArtist(): String? {
    val baseName = name.substringBeforeLast('.', name)
    return baseName.substringBefore(" - ", "").trim().takeIf { it.isNotBlank() }
}

private fun ISyncedLine.plainContent(): String = when (this) {
    is SyncedLine -> content
    is KaraokeLine -> syllables.joinToString(separator = "") { it.content }
    else -> ""
}
