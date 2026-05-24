package com.zerostudio.cloudreve.feature.files

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerostudio.cloudreve.core.common.DispatchersProvider
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.model.FileNode
import com.zerostudio.cloudreve.core.domain.repository.CloudreveRepository
import com.zerostudio.cloudreve.core.transfer.TransferScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FilesUiState(
    val currentUri: CloudreveUri = CloudreveUri.Root,
    val files: List<FileNode> = emptyList(),
    val visibleFiles: List<FileNode> = emptyList(),
    val selectedUris: Set<CloudreveUri> = emptySet(),
    val thumbnailUrls: Map<CloudreveUri, String> = emptyMap(),
    val thumbnailCacheGeneration: Int = 0,
    val query: String = "",
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    @param:StringRes val errorRes: Int? = null,
)

class FilesViewModel(
    private val repository: CloudreveRepository,
    private val transferScheduler: TransferScheduler,
    private val dispatchers: DispatchersProvider,
    initialUri: CloudreveUri = CloudreveUri.Root,
) : ViewModel() {
    private val _state = MutableStateFlow(FilesUiState(currentUri = initialUri))
    private val query = MutableStateFlow("")
    private val requestedThumbnailUris = linkedSetOf<CloudreveUri>()
    private var thumbnailCacheGeneration = 0
    private var refreshJob: Job? = null
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    init {
        observeFiles(initialUri)
        refresh()
    }

    fun onQueryChange(value: String) {
        query.value = value
        _state.update { it.copy(query = value) }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        thumbnailCacheGeneration += 1
        val generation = thumbnailCacheGeneration
        requestedThumbnailUris.clear()
        refreshJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isRefreshing = true,
                    errorMessage = null,
                    errorRes = null,
                    thumbnailCacheGeneration = generation,
                )
            }
            runCatching { repository.refreshFiles(_state.value.currentUri) }
                .onFailure { error ->
                    reportError(
                        error = error,
                        fallbackRes = R.string.files_error_refresh_failed,
                    )
                }
                .onSuccess {
                    loadThumbnails(
                        files = _state.value.visibleFiles,
                        generation = generation,
                        force = true,
                    )
                }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun toggleSelection(file: FileNode) {
        _state.update { state ->
            val next = state.selectedUris.toMutableSet()
            if (!next.add(file.uri)) next.remove(file.uri)
            state.copy(selectedUris = next)
        }
    }

    fun clearSelection() = _state.update { it.copy(selectedUris = emptySet()) }

    fun downloadSelected() {
        val selected = selectedFiles()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            selected.forEach { file ->
                runCatching { transferScheduler.enqueueDownload(file) }
                    .onFailure { error ->
                        reportError(
                            error = error,
                            fallbackRes = R.string.files_error_download_failed,
                        )
                    }
            }
            clearSelection()
        }
    }

    fun shareSelected(onReady: (String) -> Unit) {
        val selected = _state.value.selectedUris.toList()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.createShare(selected) }
                .onSuccess { share -> onReady(share.url) }
                .onFailure { error ->
                    reportError(
                        error = error,
                        fallbackRes = R.string.files_error_share_failed,
                    )
                }
        }
    }

    fun renameSelected(newName: String) {
        val selected = selectedFiles().singleOrNull() ?: return
        viewModelScope.launch {
            runCatching { repository.rename(selected.uri, newName.trim()) }
                .onSuccess { clearSelection() }
                .onFailure { error ->
                    reportError(
                        error = error,
                        fallbackRes = R.string.files_error_rename_failed,
                    )
                }
        }
    }

    fun copySelected() {
        val selected = selectedFiles()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                repository.copy(selected.map { it.uri }, _state.value.currentUri)
            }.onSuccess {
                clearSelection()
            }.onFailure { error ->
                reportError(
                    error = error,
                    fallbackRes = R.string.files_error_copy_failed,
                )
            }
        }
    }

    fun resolveOpenWithUrl(file: FileNode, onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.createDownloadUrl(file.uri) }
                .onSuccess(onReady)
                .onFailure { error ->
                    reportError(
                        error = error,
                        fallbackRes = R.string.files_error_open_with_failed,
                    )
                }
        }
    }

    fun enqueueUploads(sourceUris: List<String>) {
        if (sourceUris.isEmpty()) return
        viewModelScope.launch {
            sourceUris.forEach { sourceUri ->
                runCatching { transferScheduler.enqueueUpload(sourceUri, _state.value.currentUri) }
                    .onFailure { error ->
                        reportError(
                            error = error,
                            fallbackRes = R.string.files_error_upload_failed,
                        )
                    }
            }
        }
    }

    fun enqueueDirectoryUpload(sourceUri: String) {
        viewModelScope.launch {
            runCatching { transferScheduler.enqueueDirectoryUpload(sourceUri, _state.value.currentUri) }
                .onFailure { error ->
                    reportError(
                        error = error,
                        fallbackRes = R.string.files_error_upload_failed,
                    )
                }
        }
    }

    fun createFolder(name: String) {
        createNode(name = name, creator = repository::createFolder)
    }

    fun createFile(name: String) {
        createNode(name = name, creator = repository::createFile)
    }

    fun deleteSelected() {
        val selected = _state.value.selectedUris.toList()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.delete(selected) }
                .onSuccess { clearSelection() }
                .onFailure { error ->
                    reportError(
                        error = error,
                        fallbackRes = R.string.files_error_delete_failed,
                    )
                }
        }
    }

    private fun createNode(
        name: String,
        creator: suspend (CloudreveUri, String) -> Unit,
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(errorMessage = null, errorRes = null) }
            runCatching { creator(_state.value.currentUri, trimmed) }
                .onFailure { error ->
                    reportError(
                        error = error,
                        fallbackRes = R.string.files_error_create_failed,
                    )
                }
        }
    }

    private fun observeFiles(initialUri: CloudreveUri) {
        viewModelScope.launch {
            repository.listFiles(initialUri)
                .combine(query) { files, currentQuery ->
                    withContext(dispatchers.default) {
                        FilesSnapshot(
                            files = files,
                            visibleFiles = filterVisibleFiles(files, currentQuery),
                            availableUris = files.mapTo(linkedSetOf()) { it.uri },
                        )
                    }
                }
                .collect { snapshot ->
                    _state.update { current ->
                        val cachedThumbnailUrls = snapshot.files.mapNotNull { file ->
                            file.thumbnailUrl
                                ?.takeIf(String::isNotBlank)
                                ?.let { url -> file.uri to url }
                        }.toMap()
                        current.copy(
                            files = snapshot.files,
                            visibleFiles = snapshot.visibleFiles,
                            selectedUris = current.selectedUris.intersect(snapshot.availableUris),
                            thumbnailUrls = current.thumbnailUrls
                                .filterKeys { it in snapshot.availableUris } + cachedThumbnailUrls,
                        )
                    }
                    loadThumbnails(
                        files = snapshot.visibleFiles,
                        generation = _state.value.thumbnailCacheGeneration,
                        force = false,
                    )
                }
        }
    }

    private fun loadThumbnails(
        files: List<FileNode>,
        generation: Int,
        force: Boolean,
    ) {
        val currentState = _state.value
        val targets = files
            .filter { it.supportsThumbnail }
            .filter { force || it.thumbnailUrl.isNullOrBlank() }
            .filterNot {
                if (force) {
                    it.uri in requestedThumbnailUris
                } else {
                    it.uri in currentState.thumbnailUrls || it.uri in requestedThumbnailUris
                }
            }
        if (targets.isEmpty()) return
        requestedThumbnailUris += targets.map { it.uri }
        viewModelScope.launch {
            targets.forEach { file ->
                runCatching { repository.createThumbnailUrl(file.uri) }
                    .onSuccess { url ->
                        if (_state.value.thumbnailCacheGeneration != generation) return@onSuccess
                        _state.update { current ->
                            current.copy(thumbnailUrls = current.thumbnailUrls + (file.uri to url))
                        }
                    }
            }
        }
    }

    private fun selectedFiles(): List<FileNode> =
        _state.value.files.filter { it.uri in _state.value.selectedUris }

    private fun reportError(
        error: Throwable,
        @StringRes fallbackRes: Int,
    ) {
        _state.update {
            it.copy(
                errorMessage = error.message,
                errorRes = if (error.message == null) fallbackRes else null,
            )
        }
    }
}

private data class FilesSnapshot(
    val files: List<FileNode>,
    val visibleFiles: List<FileNode>,
    val availableUris: Set<CloudreveUri> = emptySet(),
)

private fun filterVisibleFiles(files: List<FileNode>, query: String): List<FileNode> {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank()) return files
    return files.asSequence()
        .filter { it.name.contains(trimmedQuery, ignoreCase = true) }
        .toList()
}

private val FileNode.supportsThumbnail: Boolean
    get() = type == com.zerostudio.cloudreve.core.domain.model.FileType.Image ||
        type == com.zerostudio.cloudreve.core.domain.model.FileType.Video ||
        type == com.zerostudio.cloudreve.core.domain.model.FileType.Audio
