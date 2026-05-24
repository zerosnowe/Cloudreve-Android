package com.zerostudio.cloudreve.feature.preview

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
import coil3.size.Precision
import coil3.size.Scale
import com.zerostudio.cloudreve.core.domain.model.FileDetails
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.File as FileIcon
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

@Composable
fun ImageDetailsBottomSheet(
    show: Boolean,
    loading: Boolean,
    details: FileDetails?,
    localMetadata: Map<String, String>,
    geoPoint: ImageGeoPoint?,
    locationAddress: String?,
    loadingLocationAddress: Boolean,
    fallback: ImagePreviewArgs,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.image_preview_details_title),
        endAction = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = MiuixIcons.Regular.Close,
                    contentDescription = stringResource(R.string.common_close),
                )
            }
        },
        onDismissRequest = onDismiss,
        backgroundColor = MiuixTheme.colorScheme.background,
        cornerRadius = 28.dp,
        enableNestedScroll = true,
        renderInRootScaffold = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.image_preview_detail_location),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
            )
            TiandituMapSlot(geoPoint = geoPoint)
            DetailInfoCard(
                icon = MiuixIcons.Regular.Location,
                title = stringResource(R.string.image_preview_detail_location_title),
                subtitle = locationAddress
                    ?: when {
                        loadingLocationAddress -> stringResource(R.string.image_preview_detail_location_loading)
                        geoPoint != null -> stringResource(R.string.image_preview_detail_location_resolving_failed)
                        else -> stringResource(R.string.image_preview_detail_location_empty)
                    },
            )

            Text(
                text = stringResource(R.string.image_preview_detail_section_details),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (loading && details == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    InfiniteProgressIndicator()
                }
            }

            val metadata = details?.metadata.orEmpty() + localMetadata
            DetailInfoCard(
                icon = MiuixIcons.Regular.Phone,
                title = metadata.cameraTitle()
                    ?: stringResource(R.string.image_preview_detail_camera_unknown),
                subtitle = metadata.cameraSubtitle()
                    ?: stringResource(R.string.image_preview_detail_camera_summary_unknown),
            )
            DetailInfoCard(
                icon = MiuixIcons.Regular.Image,
                title = details?.name ?: fallback.name,
                subtitle = listOfNotNull(
                    metadata.imageDimensions(),
                    formatBytes(details?.size ?: fallback.size),
                    details?.mimeType ?: fallback.mimeType,
                ).joinToString(separator = " · "),
            )
            DetailInfoCard(
                icon = MiuixIcons.Regular.Info,
                title = details?.mimeType?.takeIf { it.isNotBlank() }
                    ?: fallback.mimeType
                    ?: stringResource(R.string.image_preview_detail_color_title),
                subtitle = metadata.colorSubtitle()
                    ?: stringResource(R.string.image_preview_detail_color_summary_unknown),
            )
            DetailInfoCard(
                icon = MiuixIcons.Regular.CloudFill,
                title = stringResource(R.string.image_preview_detail_cloudreve_title),
                subtitle = (details?.uri ?: fallback.uri).value,
            )
            DetailInfoCard(
                icon = MiuixIcons.Regular.Timer,
                title = formatEpochMillis(details?.updatedAtEpochMillis ?: fallback.updatedAtEpochMillis),
                subtitle = details?.createdAtEpochMillis
                    ?.takeIf { it > 0L }
                    ?.let { stringResource(R.string.image_preview_detail_created_at, formatEpochMillis(it)) }
                    ?: stringResource(R.string.image_preview_detail_updated),
            )
            details?.storagePolicyName?.takeIf { it.isNotBlank() }?.let { policy ->
                DetailInfoCard(
                    icon = MiuixIcons.Regular.FileIcon,
                    title = policy,
                    subtitle = details.storageUsed?.let(::formatBytes)
                        ?: stringResource(R.string.image_preview_detail_storage_policy),
                )
            }
        }
    }
}

@Composable
private fun TiandituMapSlot(
    geoPoint: ImageGeoPoint?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(228.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (geoPoint == null) {
            Text(
                text = stringResource(R.string.image_preview_detail_location_empty),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            return@Box
        }

        val density = LocalDensity.current
        var viewportSize by remember { mutableStateOf(IntSize.Zero) }
        var zoom by remember(geoPoint) { mutableFloatStateOf(TIANDITU_DEFAULT_ZOOM.toFloat()) }
        var mapCenter by remember(geoPoint) { mutableStateOf(geoPoint) }
        val tileSizeDp = with(density) { TIANDITU_TILE_SIZE_PX.toDp() }
        val tileZoom = zoom.roundToInt().coerceIn(TIANDITU_MIN_ZOOM, TIANDITU_MAX_ZOOM)
        val tiles = remember(mapCenter, viewportSize, tileZoom) {
            if (viewportSize == IntSize.Zero) {
                emptyList()
            } else {
                buildTiandituTiles(
                    point = mapCenter,
                    viewportSize = viewportSize,
                    zoom = tileZoom,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .pointerInput(geoPoint, viewportSize) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val previousZoom = tileZoom
                        zoom = (zoom * gestureZoom).coerceIn(
                            TIANDITU_MIN_ZOOM.toFloat(),
                            TIANDITU_MAX_ZOOM.toFloat(),
                        )
                        val nextZoom = zoom.roundToInt()
                            .coerceIn(TIANDITU_MIN_ZOOM, TIANDITU_MAX_ZOOM)
                        val effectiveZoom = if (gestureZoom != 1f) nextZoom else previousZoom
                        mapCenter = mapCenter.translateByScreenPan(
                            pan = pan,
                            zoom = effectiveZoom,
                        )
                    }
                }
                .background(Color(0xFFDCECF2)),
        ) {
            tiles.forEach { tile ->
                TiandituLayerTile(
                    tile = tile,
                    layer = TiandituLayer.Vector,
                    modifier = Modifier
                        .offset { IntOffset(tile.offsetX.roundToInt(), tile.offsetY.roundToInt()) }
                        .size(tileSizeDp),
                )
                TiandituLayerTile(
                    tile = tile,
                    layer = TiandituLayer.VectorAnnotation,
                    modifier = Modifier
                        .offset { IntOffset(tile.offsetX.roundToInt(), tile.offsetY.roundToInt()) }
                        .size(tileSizeDp),
                )
            }

            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(42.dp),
                cornerRadius = 21.dp,
                colors = CardDefaults.defaultColors(
                    color = Color(0xFFE9534D),
                    contentColor = Color.White,
                ),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Pin,
                        contentDescription = stringResource(R.string.image_preview_detail_location),
                        modifier = Modifier.size(24.dp),
                        tint = Color.White,
                    )
                }
            }

        }
    }
}

@Composable
private fun TiandituLayerTile(
    tile: TiandituTile,
    layer: TiandituLayer,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val url = remember(tile, layer) { tile.url(layer) }
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .size(TIANDITU_TILE_SIZE_PX, TIANDITU_TILE_SIZE_PX)
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
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
private fun DetailInfoCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 10.dp,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title.ifBlank { "-" },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle.ifBlank { "-" },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private enum class TiandituLayer(
    val endpoint: String,
    val layer: String,
) {
    Vector(endpoint = "vec_w", layer = "vec"),
    VectorAnnotation(endpoint = "cva_w", layer = "cva"),
}

private data class TiandituTile(
    val x: Int,
    val y: Int,
    val zoom: Int,
    val offsetX: Float,
    val offsetY: Float,
) {
    fun url(layer: TiandituLayer): String {
        val server = Math.floorMod(x + y + zoom, TIANDITU_SUBDOMAIN_COUNT)
        return "https://t$server.tianditu.gov.cn/${layer.endpoint}/wmts" +
            "?SERVICE=WMTS" +
            "&REQUEST=GetTile" +
            "&VERSION=1.0.0" +
            "&LAYER=${layer.layer}" +
            "&STYLE=default" +
            "&TILEMATRIXSET=w" +
            "&TILEMATRIX=$zoom" +
            "&TILEROW=$y" +
            "&TILECOL=$x" +
            "&FORMAT=tiles" +
            "&tk=$TIANDITU_KEY"
    }
}

private fun buildTiandituTiles(
    point: ImageGeoPoint,
    viewportSize: IntSize,
    zoom: Int,
): List<TiandituTile> {
    val tileCount = 2.0.pow(zoom)
    val worldX = (point.longitude + 180.0) / 360.0 * tileCount * TIANDITU_TILE_SIZE_PX
    val latRad = point.latitude.coerceIn(-85.05112878, 85.05112878) * PI / 180.0
    val worldY = (
        1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI
        ) / 2.0 * tileCount * TIANDITU_TILE_SIZE_PX
    val left = worldX - viewportSize.width / 2.0
    val top = worldY - viewportSize.height / 2.0
    val right = worldX + viewportSize.width / 2.0
    val bottom = worldY + viewportSize.height / 2.0
    val minTileX = floor(left / TIANDITU_TILE_SIZE_PX).toInt()
    val maxTileX = floor(right / TIANDITU_TILE_SIZE_PX).toInt()
    val minTileY = floor(top / TIANDITU_TILE_SIZE_PX).toInt()
    val maxTileY = floor(bottom / TIANDITU_TILE_SIZE_PX).toInt()
    val maxTileIndex = tileCount.toInt() - 1

    return buildList {
        for (tileY in minTileY..maxTileY) {
            if (tileY !in 0..maxTileIndex) continue
            for (tileX in minTileX..maxTileX) {
                val wrappedTileX = Math.floorMod(tileX, maxTileIndex + 1)
                add(
                    TiandituTile(
                        x = wrappedTileX,
                        y = tileY,
                        zoom = zoom,
                        offsetX = (tileX * TIANDITU_TILE_SIZE_PX - left).toFloat(),
                        offsetY = (tileY * TIANDITU_TILE_SIZE_PX - top).toFloat(),
                    ),
                )
            }
        }
    }
}

private fun ImageGeoPoint.translateByScreenPan(
    pan: Offset,
    zoom: Int,
): ImageGeoPoint {
    if (pan == Offset.Zero) return this
    val scale = 2.0.pow(zoom) * TIANDITU_TILE_SIZE_PX
    val currentWorld = toWorldPixel(zoom)
    val currentWorldX = currentWorld.x
    val currentWorldY = currentWorld.y
    val nextWorldX = currentWorldX - pan.x
    val nextWorldY = currentWorldY - pan.y
    val nextLongitude = (nextWorldX / scale * 360.0 - 180.0).wrapLongitude()
    val mercator = PI * (1.0 - 2.0 * nextWorldY / scale)
    val nextLatitude = (atan(sinh(mercator)) * 180.0 / PI).coerceIn(-85.05112878, 85.05112878)
    return ImageGeoPoint(latitude = nextLatitude, longitude = nextLongitude)
}

private fun ImageGeoPoint.toWorldPixel(zoom: Int): WorldPixel {
    val scale = 2.0.pow(zoom) * TIANDITU_TILE_SIZE_PX
    val latRad = latitude.coerceIn(-85.05112878, 85.05112878) * PI / 180.0
    return WorldPixel(
        x = (longitude + 180.0) / 360.0 * scale,
        y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * scale,
    )
}

private data class WorldPixel(
    val x: Double,
    val y: Double,
)

private fun Double.wrapLongitude(): Double {
    var value = this
    while (value < -180.0) value += 360.0
    while (value > 180.0) value -= 360.0
    return value
}

private fun Map<String, String>.cameraTitle(): String? =
    metadataValue("make", "camera make", "device maker")
        ?.let { make ->
            listOfNotNull(make, metadataValue("model", "camera model", "device model"))
                .distinct()
                .joinToString(separator = " ")
                .takeIf { it.isNotBlank() }
        }
        ?: metadataValue("model", "camera model", "lens model")

private fun Map<String, String>.cameraSubtitle(): String? =
    listOfNotNull(
        metadataValue("fnumber", "aperture")?.let { "f/$it" },
        metadataValue("exposuretime", "exposure time", "shutter")?.let { "$it s" },
        metadataValue("focallength", "focal length")?.let { "$it mm" },
        metadataValue("isospeedratings", "iso")?.let { "ISO $it" },
    ).joinToString(separator = " · ").takeIf { it.isNotBlank() }

private fun Map<String, String>.imageDimensions(): String? {
    val width = metadataValue("imagewidth", "pixelx", "width")?.digitsOnly()
    val height = metadataValue("imageheight", "pixely", "height")?.digitsOnly()
    return if (!width.isNullOrBlank() && !height.isNullOrBlank()) {
        "$width x $height"
    } else {
        null
    }
}

private fun Map<String, String>.colorSubtitle(): String? =
    listOfNotNull(
        metadataValue("colorspace", "color space"),
        metadataValue("profiledescription", "color profile", "profile"),
        metadataValue("bitsperpixel", "bit depth", "bits per sample"),
    ).distinct().joinToString(separator = " · ").takeIf { it.isNotBlank() }

private fun Map<String, String>.metadataValue(vararg names: String): String? {
    val normalizedNames = names.map { it.lowercase(Locale.ROOT) }
    return entries.firstOrNull { (key, value) ->
        value.isNotBlank() && normalizedNames.any { key.lowercase(Locale.ROOT).contains(it) }
    }?.value?.trim()
}

private fun String.digitsOnly(): String? =
    Regex("""\d+""").find(this)?.value

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (value >= 10 || unit == 0) {
        "${value.toInt()} ${units[unit]}"
    } else {
        "%.1f %s".format(Locale.getDefault(), value, units[unit])
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

private const val TIANDITU_KEY = "1d9ceaf17fedbb67888ef51cdeafda72"
private const val TIANDITU_MIN_ZOOM = 3
private const val TIANDITU_MAX_ZOOM = 18
private const val TIANDITU_DEFAULT_ZOOM = TIANDITU_MAX_ZOOM
private const val TIANDITU_TILE_SIZE_PX = 256
private const val TIANDITU_SUBDOMAIN_COUNT = 8
