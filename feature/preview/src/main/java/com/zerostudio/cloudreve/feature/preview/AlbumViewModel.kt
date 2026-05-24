package com.zerostudio.cloudreve.feature.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerostudio.cloudreve.core.common.DispatchersProvider
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumViewModel(
    private val albumRepository: CloudreveAlbumRepository,
    private val notificationController: AlbumSyncNotificationController,
    private val dispatchers: DispatchersProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(AlbumUiState())
    val state: StateFlow<AlbumUiState> = _state.asStateFlow()

    private val requestedThumbnailUris = linkedSetOf<CloudreveUri>()
    private var scanJob: Job? = null
    private var queryJob: Job? = null
    private var infoEventId = 0L

    init {
        observeCachedImages()
        viewModelScope.launch {
            val cachedImages = runCatching { albumRepository.cachedImages().first() }.getOrDefault(emptyList())
            if (cachedImages.isEmpty()) {
                refresh()
            }
        }
    }

    fun refresh() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val hasImages = _state.value.allImages.isNotEmpty()
            notificationController.showSyncing(scannedFolders = 0, photoCount = _state.value.allImages.size)
            _state.update {
                it.copy(
                    isScanning = true,
                    scannedFolders = 0,
                    allImages = if (hasImages) it.allImages else emptyList(),
                    gridItems = if (hasImages) it.gridItems else emptyList(),
                    thumbnailUrls = if (hasImages) it.thumbnailUrls else emptyMap(),
                    infoEvent = nextInfoEvent(AlbumInfoEventType.SyncStarted),
                    errorMessage = null,
                )
            }
            runCatching {
                albumRepository.scanImages().collect { snapshot ->
                    publishSnapshot(snapshot)
                    if (snapshot.complete) {
                        notificationController.showCompleted(snapshot.images.size)
                        _state.update {
                            it.copy(infoEvent = nextInfoEvent(AlbumInfoEventType.SyncCompleted))
                        }
                    } else {
                        notificationController.showSyncing(
                            scannedFolders = snapshot.scannedFolders,
                            photoCount = snapshot.images.size,
                        )
                    }
                }
            }.onFailure { error ->
                notificationController.showFailed(error.message)
                _state.update {
                    it.copy(
                        isScanning = false,
                        errorMessage = error.message,
                    )
                }
            }
        }
    }

    fun consumeInfoEvent(id: Long) {
        _state.update { state ->
            if (state.infoEvent?.id == id) state.copy(infoEvent = null) else state
        }
    }

    fun onQueryChange(value: String) {
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            val snapshot = _state.value
            val gridItems = withContext(dispatchers.default) {
                buildAlbumGridItems(
                    images = snapshot.allImages,
                    query = value,
                    thumbnailUrls = snapshot.thumbnailUrls,
                )
            }
            _state.update {
                it.copy(
                    query = value,
                    gridItems = gridItems,
                )
            }
        }
    }

    fun ensureThumbnail(media: AlbumMediaItem) {
        val uri = media.uri
        val snapshot = _state.value
        if (media.thumbnailUrl?.isNotBlank() == true) return
        if (uri in snapshot.thumbnailUrls || !requestedThumbnailUris.add(uri)) return

        viewModelScope.launch {
            runCatching {
                albumRepository.createThumbnailUrl(uri)
            }.onSuccess { url ->
                val current = _state.value
                val thumbnails = current.thumbnailUrls + (uri to url)
                val gridItems = withContext(dispatchers.default) {
                    buildAlbumGridItems(
                        images = current.allImages,
                        query = current.query,
                        thumbnailUrls = thumbnails,
                    )
                }
                _state.update {
                    it.copy(
                        thumbnailUrls = thumbnails,
                        gridItems = gridItems,
                    )
                }
            }.onFailure {
                requestedThumbnailUris.remove(uri)
            }
        }
    }

    private suspend fun publishSnapshot(snapshot: AlbumScanSnapshot) {
        val current = _state.value
        val gridItems = withContext(dispatchers.default) {
            buildAlbumGridItems(
                images = snapshot.images,
                query = current.query,
                thumbnailUrls = current.thumbnailUrls,
            )
        }
        _state.update {
            it.copy(
                allImages = snapshot.images,
                gridItems = gridItems,
                scannedFolders = snapshot.scannedFolders,
                hasCachedImages = snapshot.images.isNotEmpty(),
                isScanning = !snapshot.complete,
                errorMessage = null,
            )
        }
    }

    private fun observeCachedImages() {
        viewModelScope.launch {
            albumRepository.cachedImages().collect { images ->
                val current = _state.value
                if (images.isEmpty() && current.isScanning) return@collect
                val gridItems = withContext(dispatchers.default) {
                    buildAlbumGridItems(
                        images = images,
                        query = current.query,
                        thumbnailUrls = current.thumbnailUrls,
                    )
                }
                _state.update {
                    it.copy(
                        allImages = images,
                        gridItems = gridItems,
                        hasCachedImages = images.isNotEmpty(),
                        errorMessage = if (images.isNotEmpty()) null else it.errorMessage,
                    )
                }
            }
        }
    }

    private fun nextInfoEvent(type: AlbumInfoEventType): AlbumInfoEvent =
        AlbumInfoEvent(
            id = ++infoEventId,
            type = type,
        )
}
