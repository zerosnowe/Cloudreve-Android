package com.zerostudio.cloudreve.feature.preview

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerostudio.cloudreve.core.domain.model.CloudreveUri
import com.zerostudio.cloudreve.core.domain.model.FileDetails
import com.zerostudio.cloudreve.core.domain.repository.CloudreveRepository
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class ImagePreviewArgs(
    val uri: CloudreveUri,
    val name: String,
    val size: Long,
    val mimeType: String?,
    val updatedAtEpochMillis: Long,
    val isFavorite: Boolean,
    val thumbnailUrl: String? = null,
)

data class ImagePreviewSharePayload(
    val filePath: String,
    val name: String,
    val mimeType: String?,
)

data class ImagePreviewEditPayload(
    val filePath: String,
    val name: String,
    val mimeType: String?,
)

data class ImageGeoPoint(
    val latitude: Double,
    val longitude: Double,
)

data class ImagePreviewUiState(
    val args: ImagePreviewArgs,
    val imageFilePath: String? = null,
    val details: FileDetails? = null,
    val localMetadata: Map<String, String> = emptyMap(),
    val geoPoint: ImageGeoPoint? = null,
    val locationAddress: String? = null,
    val isLoadingLocationAddress: Boolean = false,
    val isLoadingImage: Boolean = true,
    val isLoadingDetails: Boolean = false,
    val isDeleting: Boolean = false,
    val isFavorite: Boolean = args.isFavorite,
    val showDetailsDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val errorMessage: String? = null,
)

class ImagePreviewViewModel(
    private val repository: CloudreveRepository,
    private val appContext: Context,
    private val authenticatedClient: OkHttpClient,
    args: ImagePreviewArgs,
) : ViewModel() {
    private val _state = MutableStateFlow(ImagePreviewUiState(args = args))
    val state: StateFlow<ImagePreviewUiState> = _state.asStateFlow()
    private val publicClient by lazy { OkHttpClient.Builder().build() }

    init {
        loadImageUrl()
    }

    fun share(onReady: (ImagePreviewSharePayload) -> Unit) {
        viewModelScope.launch {
            val filePath = ensureImageFile() ?: return@launch
            val snapshot = _state.value.args
            onReady(
                ImagePreviewSharePayload(
                    filePath = filePath,
                    name = snapshot.name,
                    mimeType = snapshot.mimeType,
                ),
            )
        }
    }

    fun edit(onReady: (ImagePreviewEditPayload) -> Unit) {
        viewModelScope.launch {
            val filePath = ensureImageFile() ?: return@launch
            val snapshot = _state.value.args
            onReady(
                ImagePreviewEditPayload(
                    filePath = filePath,
                    name = snapshot.name,
                    mimeType = snapshot.mimeType,
                ),
            )
        }
    }

    fun toggleFavorite() {
        val next = !_state.value.isFavorite
        _state.update { it.copy(isFavorite = next, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.setFavorite(_state.value.args.uri, next)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isFavorite = !next,
                        errorMessage = error.message,
                    )
                }
            }
        }
    }

    fun showDetails() {
        _state.update { it.copy(showDetailsDialog = true, errorMessage = null) }
        if (_state.value.details != null || _state.value.isLoadingDetails) {
            val needsLocationAddress = _state.value.geoPoint != null &&
                _state.value.locationAddress == null &&
                !_state.value.isLoadingLocationAddress
            if (_state.value.geoPoint == null || _state.value.localMetadata.isEmpty() || needsLocationAddress) {
                viewModelScope.launch {
                    loadLocalMetadata()
                    loadGeoPoint(_state.value.details)
                    loadLocationAddress(_state.value.geoPoint)
                }
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoadingDetails = true) }
            runCatching {
                repository.getFileDetails(_state.value.args.uri)
            }.onSuccess { details ->
                _state.update { it.copy(details = details) }
                loadLocalMetadata()
                loadGeoPoint(details)
            }.onFailure { error ->
                _state.update { it.copy(errorMessage = error.message) }
                loadLocalMetadata()
                loadGeoPoint(details = null)
            }
            _state.update { it.copy(isLoadingDetails = false) }
        }
    }

    fun dismissDetails() {
        _state.update { it.copy(showDetailsDialog = false) }
    }

    fun showDeletePrompt() {
        _state.update { it.copy(showDeleteDialog = true, errorMessage = null) }
    }

    fun dismissDeletePrompt() {
        _state.update { it.copy(showDeleteDialog = false) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, errorMessage = null) }
            runCatching {
                repository.delete(listOf(_state.value.args.uri))
            }.onSuccess {
                onDeleted()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isDeleting = false,
                        showDeleteDialog = false,
                        errorMessage = error.message,
                    )
                }
            }
        }
    }

    private fun loadImageUrl() {
        viewModelScope.launch {
            ensureImageFile()
        }
    }

    private suspend fun ensureImageFile(): String? {
        _state.value.imageFilePath?.let { cachedPath ->
            if (File(cachedPath).isFile) return cachedPath
        }
        _state.update { it.copy(isLoadingImage = true, errorMessage = null) }
        return runCatching {
            cachePreviewImage()
        }.onSuccess { filePath ->
            _state.update { it.copy(imageFilePath = filePath, isLoadingImage = false) }
        }.onFailure { error ->
            _state.update {
                it.copy(
                    isLoadingImage = false,
                    errorMessage = error.userFacingMessage(),
                )
            }
        }.getOrNull()
    }

    private suspend fun cachePreviewImage(): String = withContext(Dispatchers.IO) {
        val snapshot = _state.value.args
        val previewDir = File(appContext.cacheDir, PREVIEW_CACHE_DIR).apply { mkdirs() }
        val target = File(previewDir, snapshot.safePreviewCacheFileName())
        if (target.isFile && target.length() > 0L) {
            return@withContext target.absolutePath
        }
        val downloadUrl = repository.createDownloadUrl(snapshot.uri)
        val temp = File(previewDir, "${target.name}.download")
        if (temp.exists()) temp.delete()
        runCatching {
            downloadToFile(authenticatedClient, downloadUrl, temp)
        }.recoverCatching { error ->
            if (error is PreviewDownloadException && error.code in setOf(401, 403)) {
                downloadToFile(publicClient, downloadUrl, temp)
            } else {
                throw error
            }
        }.getOrThrow()
        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Image preview cache file rename failed" }
        target.absolutePath
    }

    private suspend fun loadGeoPoint(details: FileDetails?) {
        if (_state.value.geoPoint != null) return
        val point = withContext(Dispatchers.IO) {
            details?.metadata?.toGeoPoint()
                ?: _state.value.localMetadata.toGeoPoint()
                ?: ensureImageFile()?.let(::readExifGeoPoint)
        }
        point?.let { geoPoint ->
            _state.update { it.copy(geoPoint = geoPoint) }
            loadLocationAddress(geoPoint)
        }
    }

    private suspend fun loadLocationAddress(point: ImageGeoPoint?) {
        if (point == null || _state.value.locationAddress != null || _state.value.isLoadingLocationAddress) return
        _state.update { it.copy(isLoadingLocationAddress = true) }
        val address = withContext(Dispatchers.IO) {
            runCatching { fetchTiandituAddress(point) }.getOrNull()
        }
        _state.update {
            it.copy(
                locationAddress = address,
                isLoadingLocationAddress = false,
            )
        }
    }

    private suspend fun loadLocalMetadata() {
        if (_state.value.localMetadata.isNotEmpty()) return
        val filePath = ensureImageFile() ?: return
        val metadata = withContext(Dispatchers.IO) {
            readExifMetadata(filePath)
        }
        if (metadata.isNotEmpty()) {
            _state.update { it.copy(localMetadata = metadata) }
        }
    }

    private fun readExifGeoPoint(filePath: String): ImageGeoPoint? = runCatching {
        val latLong = ExifInterface(filePath).latLong ?: return@runCatching null
        if (latLong.size < 2) return@runCatching null
        ImageGeoPoint(
            latitude = latLong[0],
            longitude = latLong[1],
        ).takeIf { it.isValidGeoPoint() }
    }.getOrNull()

    private fun readExifMetadata(filePath: String): Map<String, String> = runCatching {
        val exif = ExifInterface(filePath)
        buildMap {
            putExif(exif, "make", ExifInterface.TAG_MAKE)
            putExif(exif, "model", ExifInterface.TAG_MODEL)
            putExif(exif, "fnumber", ExifInterface.TAG_F_NUMBER)
            putExif(exif, "exposuretime", ExifInterface.TAG_EXPOSURE_TIME)
            putExif(exif, "focallength", ExifInterface.TAG_FOCAL_LENGTH)
            putExif(exif, "isospeedratings", ExifInterface.TAG_ISO_SPEED_RATINGS)
            putExif(exif, "imagewidth", ExifInterface.TAG_IMAGE_WIDTH)
            putExif(exif, "imageheight", ExifInterface.TAG_IMAGE_LENGTH)
            putExif(exif, "pixelx", ExifInterface.TAG_PIXEL_X_DIMENSION)
            putExif(exif, "pixely", ExifInterface.TAG_PIXEL_Y_DIMENSION)
            putExif(exif, "colorspace", ExifInterface.TAG_COLOR_SPACE)
            putExif(exif, "datetimeoriginal", ExifInterface.TAG_DATETIME_ORIGINAL)
            exif.latLong?.takeIf { it.size >= 2 }?.let { latLong ->
                put("latitude", latLong[0].toString())
                put("longitude", latLong[1].toString())
            }
        }
    }.getOrDefault(emptyMap())

    private fun MutableMap<String, String>.putExif(
        exif: ExifInterface,
        key: String,
        tag: String,
    ) {
        exif.getAttribute(tag)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { value -> put(key, value) }
    }

    private fun downloadToFile(client: OkHttpClient, url: String, target: File) {
        val request = Request.Builder()
            .url(url)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PreviewDownloadException(response.code)
            }
            val body = response.body ?: error("Image preview download body is empty")
            target.outputStream().use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
        }
    }

    private fun fetchTiandituAddress(point: ImageGeoPoint): String? {
        val postStr = JSONObject()
            .put("lon", point.longitude)
            .put("lat", point.latitude)
            .put("ver", 1)
            .toString()
        val url = "https://api.tianditu.gov.cn/geocoder" +
            "?postStr=${Uri.encode(postStr)}" +
            "&type=geocode" +
            "&tk=$TIANDITU_KEY"
        val request = Request.Builder()
            .url(url)
            .build()
        publicClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string()?.takeIf { it.isNotBlank() } ?: return null
            val root = JSONObject(body)
            val result = root.optJSONObject("result") ?: return null
            val formattedAddress = result.optString("formatted_address").takeIf { it.isNotBlank() }
            if (formattedAddress != null) return formattedAddress
            val addressComponent = result.optJSONObject("addressComponent")
            return listOfNotNull(
                addressComponent?.optString("province")?.takeIf { it.isNotBlank() },
                addressComponent?.optString("city")?.takeIf { it.isNotBlank() },
                addressComponent?.optString("county")?.takeIf { it.isNotBlank() },
                addressComponent?.optString("address")?.takeIf { it.isNotBlank() },
                addressComponent?.optString("poi")?.takeIf { it.isNotBlank() },
            ).distinct().joinToString(separator = "").takeIf { it.isNotBlank() }
        }
    }

    private fun ImagePreviewArgs.safePreviewCacheFileName(): String {
        val identity = "${uri.value}|$size|$updatedAtEpochMillis|${thumbnailUrl.orEmpty()}".sha256Prefix()
        return "${identity}_${safePreviewFileName()}"
    }

    private fun ImagePreviewArgs.safePreviewFileName(): String {
        val cleanName = name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "image" }
        if (cleanName.substringAfterLast('.', missingDelimiterValue = "").isNotBlank()) {
            return cleanName
        }
        val extension = mimeType
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?.takeIf { it.isNotBlank() }
            ?: "jpg"
        return "$cleanName.$extension"
    }

    private fun String.sha256Prefix(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return bytes.take(12).joinToString(separator = "") { "%02x".format(it) }
    }

    private class PreviewDownloadException(val code: Int) : IllegalStateException(
        "Image preview download failed: HTTP $code",
    )

    private fun Throwable.userFacingMessage(): String {
        if (this is PreviewDownloadException) {
            return appContext.getString(R.string.image_preview_load_failed)
        }
        return message?.takeIf { it.isNotBlank() }
            ?: appContext.getString(R.string.image_preview_load_failed)
    }

    private fun Map<String, String>.toGeoPoint(): ImageGeoPoint? {
        fun findValue(vararg names: String): String? {
            val normalizedNames = names.map { it.lowercase(Locale.ROOT) }
            return entries.firstOrNull { (key, value) ->
                value.isNotBlank() && normalizedNames.any { key.lowercase(Locale.ROOT).contains(it) }
            }?.value
        }

        val directLat = findValue("gpslatitude", "gps_latitude", "latitude", "lat")
            ?.parseCoordinate(findValue("gpslatituderef", "gps_latitude_ref", "latref"))
        val directLon = findValue("gpslongitude", "gps_longitude", "longitude", "lng", "lon")
            ?.parseCoordinate(findValue("gpslongituderef", "gps_longitude_ref", "lonref", "lngref"))
        if (directLat != null && directLon != null) {
            return ImageGeoPoint(directLat, directLon).takeIf { it.isValidGeoPoint() }
        }

        val joined = entries.joinToString(separator = " ") { (key, value) -> "$key=$value" }
        val coordinatePairs = Regex("""(-?\d+(?:\.\d+)?)\s*[,，]\s*(-?\d+(?:\.\d+)?)""")
            .findAll(joined)
            .mapNotNull { match ->
                val lat = match.groupValues[1].toDoubleOrNull()
                val lon = match.groupValues[2].toDoubleOrNull()
                if (lat != null && lon != null) ImageGeoPoint(lat, lon) else null
            }
        return coordinatePairs.firstOrNull { it.isValidGeoPoint() }
    }

    private fun String.parseCoordinate(ref: String?): Double? {
        val trimmed = trim()
        val decimal = trimmed.toDoubleOrNull()
            ?: Regex("""-?\d+(?:\.\d+)?""").findAll(trimmed).map { it.value.toDouble() }.toList()
                .takeIf { it.isNotEmpty() }
                ?.let { parts ->
                    when (parts.size) {
                        1 -> parts[0]
                        2 -> parts[0] + parts[1] / 60.0
                        else -> parts[0] + parts[1] / 60.0 + parts[2] / 3600.0
                    }
                }
            ?: return null
        val shouldNegate = ref?.trim()?.uppercase(Locale.ROOT) in setOf("S", "W", "SOUTH", "WEST")
        return if (shouldNegate && decimal > 0) -decimal else decimal
    }

    private fun ImageGeoPoint.isValidGeoPoint(): Boolean =
        latitude in -90.0..90.0 && longitude in -180.0..180.0

    private companion object {
        const val PREVIEW_CACHE_DIR = "preview_share/source"
        const val TIANDITU_KEY = "1d9ceaf17fedbb67888ef51cdeafda72"
    }
}
