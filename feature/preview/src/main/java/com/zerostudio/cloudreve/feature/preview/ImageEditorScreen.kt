package com.zerostudio.cloudreve.feature.preview

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityOptionsCompat
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import ja.burhanrashid52.photoeditor.OnPhotoEditorListener
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoEditorView
import ja.burhanrashid52.photoeditor.PhotoFilter
import ja.burhanrashid52.photoeditor.SaveFileResult
import ja.burhanrashid52.photoeditor.SaveSettings
import ja.burhanrashid52.photoeditor.TextStyleBuilder
import ja.burhanrashid52.photoeditor.ViewType
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder
import ja.burhanrashid52.photoeditor.shape.ShapeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Redo
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun ImageEditorScreen(
    filePath: String,
    title: String,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onSaveFailed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourceFile = remember(filePath) { File(filePath) }
    val colorPalette = remember { editorColorPalette }
    val emojiPalette = remember { editorEmojiPalette }
    val filterOptions = remember { PhotoFilter.values().toList() }

    var photoEditor by remember(filePath) { mutableStateOf<PhotoEditor?>(null) }
    var sourceBitmap by remember(filePath) { mutableStateOf<Bitmap?>(null) }
    var sourceLoading by remember(filePath) { mutableStateOf(true) }
    var sourceLoadFailed by remember(filePath) { mutableStateOf(false) }
    var saving by remember(filePath) { mutableStateOf(false) }
    var loadingSticker by remember(filePath) { mutableStateOf(false) }
    var showDrawSheet by remember(filePath) { mutableStateOf(false) }
    var showEmojiSheet by remember(filePath) { mutableStateOf(false) }
    var showFilterSheet by remember(filePath) { mutableStateOf(false) }
    var showResetDialog by remember(filePath) { mutableStateOf(false) }
    var showTextDialog by remember(filePath) { mutableStateOf(false) }
    var editingTextView by remember(filePath) { mutableStateOf<View?>(null) }
    var textInput by remember(filePath) { mutableStateOf("") }
    var textColor by remember(filePath) { mutableIntStateOf(AndroidColor.WHITE) }
    var drawMode by remember(filePath) { mutableStateOf(EditorShapeMode.Brush) }
    var drawColor by remember(filePath) { mutableIntStateOf(AndroidColor.WHITE) }
    var drawSize by remember(filePath) { mutableFloatStateOf(12f) }
    var drawOpacity by remember(filePath) { mutableIntStateOf(100) }
    var brushEnabled by remember(filePath) { mutableStateOf(false) }
    var selectedFilter by remember(filePath) { mutableStateOf(PhotoFilter.NONE) }
    val stickerPicker = rememberContextActivityResultLauncher(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            loadingSticker = false
            return@rememberContextActivityResultLauncher
        }
        scope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, IntentFlags.Read)
            }
            runCatching {
                decodeStickerBitmap(context, uri)
            }.onSuccess { bitmap ->
                photoEditor?.addImage(bitmap)
            }.onFailure {
                Toast.makeText(
                    context,
                    R.string.image_editor_sticker_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            loadingSticker = false
        }
    }

    fun disableDrawing() {
        brushEnabled = false
        photoEditor?.setBrushDrawingMode(false)
        photoEditor?.clearHelperBox()
    }

    fun enableDrawing(mode: EditorShapeMode = drawMode) {
        drawMode = mode
        brushEnabled = true
        photoEditor?.setShape(buildShape(mode, drawSize, drawColor, drawOpacity))
        photoEditor?.brushColor = drawColor
        photoEditor?.brushSize = drawSize
        photoEditor?.setOpacity(drawOpacity)
        photoEditor?.setBrushDrawingMode(true)
        photoEditor?.clearHelperBox()
    }

    LaunchedEffect(photoEditor, drawMode, drawColor, drawSize, drawOpacity) {
        val editor = photoEditor ?: return@LaunchedEffect
        editor.setShape(buildShape(drawMode, drawSize, drawColor, drawOpacity))
        editor.brushColor = drawColor
        editor.brushSize = drawSize
        editor.setOpacity(drawOpacity)
        if (brushEnabled) {
            editor.setBrushDrawingMode(true)
        }
    }

    LaunchedEffect(photoEditor, selectedFilter) {
        photoEditor?.setFilterEffect(selectedFilter)
    }

    LaunchedEffect(filePath) {
        photoEditor = null
        sourceBitmap = null
        sourceLoading = true
        sourceLoadFailed = false
        runCatching {
            decodeEditorBitmap(sourceFile)
        }.onSuccess { bitmap ->
            sourceBitmap = bitmap
        }.onFailure { error ->
            Log.e(TAG, "Unable to decode editor bitmap for $filePath", error)
            sourceLoadFailed = true
        }
        sourceLoading = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = title,
                color = MiuixTheme.colorScheme.background,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.image_preview_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = !saving && photoEditor != null && sourceBitmap != null,
                        onClick = {
                            val editor = photoEditor ?: return@IconButton
                            scope.launch {
                                saving = true
                                runCatching {
                                    val target = createEditedImageFile(context.cacheDir, title)
                                    val result = editor.saveAsFile(
                                        target.absolutePath,
                                        SaveSettings.Builder()
                                            .setTransparencyEnabled(false)
                                            .setClearViewsEnabled(false)
                                            .setCompressFormat(Bitmap.CompressFormat.PNG)
                                            .setCompressQuality(96)
                                            .build(),
                                    )
                                    if (result !is SaveFileResult.Success) {
                                        error("PhotoEditor save failed")
                                    }
                                    target.absolutePath
                                }.onSuccess(onSaved)
                                    .onFailure { onSaveFailed() }
                                saving = false
                            }
                        },
                    ) {
                        if (saving) {
                            InfiniteProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                imageVector = MiuixIcons.Regular.Ok,
                                contentDescription = stringResource(R.string.image_editor_save),
                            )
                        }
                    }
                },
                titlePadding = 16.dp,
                navigationIconPadding = 16.dp,
                actionIconPadding = 16.dp,
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
            when {
                sourceLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        InfiniteProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }

                sourceBitmap != null -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            val themedContext = ContextThemeWrapper(
                                viewContext,
                                androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar,
                            )
                            PhotoEditorView(themedContext).apply {
                                source.scaleType = ImageView.ScaleType.FIT_CENTER
                                source.adjustViewBounds = true
                                source.setImageBitmap(sourceBitmap)
                                photoEditor = PhotoEditor.Builder(themedContext, this)
                                    .setPinchTextScalable(true)
                                    .setClipSourceImage(true)
                                    .build()
                                    .apply {
                                        setShape(buildShape(drawMode, drawSize, drawColor, drawOpacity))
                                        setFilterEffect(selectedFilter)
                                        setOnPhotoEditorListener(
                                            object : OnPhotoEditorListener {
                                                override fun onEditTextChangeListener(
                                                    rootView: View,
                                                    text: String,
                                                    colorCode: Int,
                                                ) {
                                                    editingTextView = rootView
                                                    textInput = text
                                                    textColor = colorCode
                                                    disableDrawing()
                                                    showTextDialog = true
                                                }

                                                override fun onAddViewListener(viewType: ViewType, numberOfAddedViews: Int) = Unit

                                                override fun onRemoveViewListener(viewType: ViewType, numberOfAddedViews: Int) = Unit

                                                override fun onStartViewChangeListener(viewType: ViewType) = Unit

                                                override fun onStopViewChangeListener(viewType: ViewType) = Unit

                                                override fun onTouchSourceImage(event: android.view.MotionEvent) = Unit
                                            },
                                        )
                                    }
                            }
                        },
                    )
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.image_preview_load_failed),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            EditorFloatingToolbar(
                drawActive = brushEnabled,
                filterActive = selectedFilter != PhotoFilter.NONE,
                loadingSticker = loadingSticker,
                enabled = sourceBitmap != null && !sourceLoading && !sourceLoadFailed,
                onDraw = {
                    enableDrawing()
                    showDrawSheet = true
                },
                onText = {
                    disableDrawing()
                    editingTextView = null
                    textInput = ""
                    showTextDialog = true
                },
                onEmoji = {
                    disableDrawing()
                    showEmojiSheet = true
                },
                onSticker = {
                    disableDrawing()
                    loadingSticker = true
                    stickerPicker.launch(arrayOf("image/*"))
                },
                onFilter = {
                    disableDrawing()
                    showFilterSheet = true
                },
                onEraser = {
                    brushEnabled = true
                    photoEditor?.setBrushEraserSize(drawSize)
                    photoEditor?.brushEraser()
                    photoEditor?.setBrushDrawingMode(true)
                    photoEditor?.clearHelperBox()
                },
                onUndo = {
                    disableDrawing()
                    photoEditor?.undo()
                },
                onRedo = {
                    disableDrawing()
                    photoEditor?.redo()
                },
                onReset = {
                    disableDrawing()
                    showResetDialog = true
                },
            )
        }
    }

    DrawSettingsSheet(
        show = showDrawSheet,
        selectedMode = drawMode,
        selectedColor = drawColor,
        selectedSize = drawSize,
        selectedOpacity = drawOpacity,
        colorPalette = colorPalette,
        onDismiss = { showDrawSheet = false },
        onModeSelected = {
            enableDrawing(it)
        },
        onColorSelected = {
            drawColor = it
            enableDrawing(drawMode)
        },
        onSizeSelected = {
            drawSize = it
            enableDrawing(drawMode)
        },
        onOpacitySelected = {
            drawOpacity = it
            enableDrawing(drawMode)
        },
    )

    FilterSheet(
        show = showFilterSheet,
        options = filterOptions,
        selected = selectedFilter,
        onDismiss = { showFilterSheet = false },
        onSelect = { selectedFilter = it },
    )

    EmojiSheet(
        show = showEmojiSheet,
        emojis = emojiPalette,
        onDismiss = { showEmojiSheet = false },
        onEmojiSelected = { emoji ->
            photoEditor?.addEmoji(emoji)
            showEmojiSheet = false
        },
    )

    TextEditorDialog(
        show = showTextDialog,
        value = textInput,
        selectedColor = textColor,
        colorPalette = colorPalette,
        editing = editingTextView != null,
        onValueChange = { textInput = it },
        onColorSelected = { textColor = it },
        onDismiss = {
            showTextDialog = false
            editingTextView = null
            textInput = ""
        },
        onConfirm = {
            val content = textInput.trim()
            if (content.isBlank()) return@TextEditorDialog
            val style = buildTextStyle(textColor)
            val view = editingTextView
            if (view != null) {
                photoEditor?.editText(view, content, style)
            } else {
                photoEditor?.addText(content, style)
            }
            showTextDialog = false
            editingTextView = null
            textInput = ""
        },
    )

    ResetDialog(
        show = showResetDialog,
        onDismiss = { showResetDialog = false },
        onConfirm = {
            photoEditor?.clearAllViews()
            selectedFilter = PhotoFilter.NONE
            showResetDialog = false
        },
    )
}

@Composable
private fun EditorFloatingToolbar(
    drawActive: Boolean,
    filterActive: Boolean,
    loadingSticker: Boolean,
    enabled: Boolean,
    onDraw: () -> Unit,
    onText: () -> Unit,
    onEmoji: () -> Unit,
    onSticker: () -> Unit,
    onFilter: () -> Unit,
    onEraser: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
) {
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 22.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 22.dp, end = 22.dp, bottom = bottomPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(
            modifier = Modifier.height(58.dp),
            cornerRadius = 29.dp,
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorIconButton(
                    icon = MiuixIcons.Regular.Tune,
                    label = stringResource(R.string.image_editor_draw),
                    enabled = enabled,
                    tint = if (drawActive) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                    onClick = onDraw,
                )
                EditorIconButton(
                    icon = MiuixIcons.Regular.Rename,
                    label = stringResource(R.string.image_editor_text),
                    enabled = enabled,
                    onClick = onText,
                )
                EditorIconButton(
                    icon = MiuixIcons.Regular.MoreCircle,
                    label = stringResource(R.string.image_editor_emoji),
                    enabled = enabled,
                    onClick = onEmoji,
                )
                EditorIconButton(
                    icon = MiuixIcons.Regular.Album,
                    label = stringResource(R.string.image_editor_sticker),
                    enabled = enabled,
                    tint = if (loadingSticker) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                    onClick = onSticker,
                )
                EditorIconButton(
                    icon = MiuixIcons.Regular.Filter,
                    label = stringResource(R.string.image_editor_filter),
                    enabled = enabled,
                    tint = if (filterActive) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                    onClick = onFilter,
                )
                EditorIconButton(
                    icon = MiuixIcons.Regular.Clear,
                    label = stringResource(R.string.image_editor_eraser),
                    enabled = enabled,
                    onClick = onEraser,
                )
                EditorIconButton(
                    icon = MiuixIcons.Regular.Undo,
                    label = stringResource(R.string.image_editor_undo),
                    enabled = enabled,
                    onClick = onUndo,
                )
                EditorIconButton(
                    icon = MiuixIcons.Regular.Redo,
                    label = stringResource(R.string.image_editor_redo),
                    enabled = enabled,
                    onClick = onRedo,
                )
                EditorIconButton(
                    icon = MiuixIcons.Regular.Reset,
                    label = stringResource(R.string.image_editor_reset),
                    enabled = enabled,
                    tint = MiuixTheme.colorScheme.error,
                    onClick = onReset,
                )
            }
        }
    }
}

@Composable
private fun DrawSettingsSheet(
    show: Boolean,
    selectedMode: EditorShapeMode,
    selectedColor: Int,
    selectedSize: Float,
    selectedOpacity: Int,
    colorPalette: List<Int>,
    onDismiss: () -> Unit,
    onModeSelected: (EditorShapeMode) -> Unit,
    onColorSelected: (Int) -> Unit,
    onSizeSelected: (Float) -> Unit,
    onOpacitySelected: (Int) -> Unit,
) {
    WindowBottomSheet(
        show = show,
        title = stringResource(R.string.image_editor_draw_settings_title),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            EditorSectionHeader(text = stringResource(R.string.image_editor_mode))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorShapeMode.entries.forEach { option ->
                    EditorOptionChip(
                        label = stringResource(option.labelRes),
                        selected = option == selectedMode,
                        onClick = { onModeSelected(option) },
                    )
                }
            }

            EditorSectionHeader(text = stringResource(R.string.image_editor_color))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                colorPalette.forEach { color ->
                    ColorSwatch(
                        color = color,
                        selected = color == selectedColor,
                        onClick = { onColorSelected(color) },
                    )
                }
            }

            EditorSectionHeader(text = stringResource(R.string.image_editor_size))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                editorBrushSizes.forEach { size ->
                    EditorOptionChip(
                        label = size.toInt().toString(),
                        selected = size == selectedSize,
                        onClick = { onSizeSelected(size) },
                    )
                }
            }

            EditorSectionHeader(text = stringResource(R.string.image_editor_opacity))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                editorOpacitySteps.forEach { opacity ->
                    EditorOptionChip(
                        label = "$opacity%",
                        selected = opacity == selectedOpacity,
                        onClick = { onOpacitySelected(opacity) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSheet(
    show: Boolean,
    options: List<PhotoFilter>,
    selected: PhotoFilter,
    onDismiss: () -> Unit,
    onSelect: (PhotoFilter) -> Unit,
) {
    WindowBottomSheet(
        show = show,
        title = stringResource(R.string.image_editor_filter_title),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { filter ->
                        EditorOptionChip(
                            label = filter.displayName(),
                            selected = filter == selected,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(filter) },
                        )
                    }
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiSheet(
    show: Boolean,
    emojis: List<String>,
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit,
) {
    WindowBottomSheet(
        show = show,
        title = stringResource(R.string.image_editor_emoji_title),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            emojis.chunked(6).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { emoji ->
                        EmojiOption(
                            emoji = emoji,
                            modifier = Modifier.weight(1f),
                            onClick = { onEmojiSelected(emoji) },
                        )
                    }
                    repeat(6 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TextEditorDialog(
    show: Boolean,
    value: String,
    selectedColor: Int,
    colorPalette: List<Int>,
    editing: Boolean,
    onValueChange: (String) -> Unit,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = stringResource(
            if (editing) R.string.image_editor_text_title_edit else R.string.image_editor_text_title,
        ),
        onDismissRequest = onDismiss,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.image_editor_text_label),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        EditorSectionHeader(text = stringResource(R.string.image_editor_color))
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            colorPalette.forEach { color ->
                ColorSwatch(
                    color = color,
                    selected = color == selectedColor,
                    onClick = { onColorSelected(color) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
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
                enabled = value.isNotBlank(),
                onClick = onConfirm,
            ) {
                Text(
                    stringResource(
                        if (editing) R.string.image_editor_text_apply else R.string.image_editor_text_add,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ResetDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = stringResource(R.string.image_editor_reset_title),
        summary = stringResource(R.string.image_editor_reset_summary),
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
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.image_editor_reset))
            }
        }
    }
}

@Composable
private fun EditorSectionHeader(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onSurface,
    )
}

@Composable
private fun EditorOptionChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier,
        showIndication = true,
        onClick = onClick,
        cornerRadius = 18.dp,
        colors = CardDefaults.defaultColors(
            color = if (selected) {
                MiuixTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MiuixTheme.colorScheme.surfaceContainer
            },
            contentColor = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.size(36.dp),
        showIndication = true,
        onClick = onClick,
        cornerRadius = 18.dp,
        colors = CardDefaults.defaultColors(
            color = Color(color),
            contentColor = Color.White,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.White, shape = androidx.compose.foundation.shape.CircleShape),
                )
            }
        }
    }
}

@Composable
private fun EmojiOption(
    emoji: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier,
        showIndication = true,
        onClick = onClick,
        cornerRadius = 18.dp,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji)
        }
    }
}

@Composable
private fun RowScope.EditorIconButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: Color = MiuixTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    IconButton(
        modifier = Modifier.size(48.dp),
        enabled = enabled,
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(25.dp),
            tint = tint.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

private enum class EditorShapeMode(
    val labelRes: Int,
    val shapeType: ShapeType,
    val paintStyle: Paint.Style,
) {
    Brush(
        labelRes = R.string.image_editor_shape_brush,
        shapeType = ShapeType.Brush,
        paintStyle = Paint.Style.FILL,
    ),
    Line(
        labelRes = R.string.image_editor_shape_line,
        shapeType = ShapeType.Line,
        paintStyle = Paint.Style.STROKE,
    ),
    Arrow(
        labelRes = R.string.image_editor_shape_arrow,
        shapeType = ShapeType.Arrow(),
        paintStyle = Paint.Style.STROKE,
    ),
    Rectangle(
        labelRes = R.string.image_editor_shape_rectangle,
        shapeType = ShapeType.Rectangle,
        paintStyle = Paint.Style.STROKE,
    ),
    Oval(
        labelRes = R.string.image_editor_shape_oval,
        shapeType = ShapeType.Oval,
        paintStyle = Paint.Style.STROKE,
    ),
}

private fun buildShape(
    mode: EditorShapeMode,
    size: Float,
    color: Int,
    opacity: Int,
): ShapeBuilder =
    ShapeBuilder()
        .withShapeType(mode.shapeType)
        .withShapeSize(size)
        .withShapeColor(color)
        .withShapeOpacity(opacity)
        .withShapePaintStyle(mode.paintStyle)

private fun buildTextStyle(color: Int): TextStyleBuilder =
    TextStyleBuilder().apply {
        withTextColor(color)
        withTextSize(52f)
        withGravity(Gravity.CENTER)
    }

private fun PhotoFilter.displayName(): String =
    if (this == PhotoFilter.NONE) {
        "None"
    } else {
        name
            .lowercase(Locale.ROOT)
            .split('_')
            .joinToString(" ") { token ->
                token.replaceFirstChar { char -> char.titlecase(Locale.ROOT) }
            }
    }

private suspend fun decodeStickerBitmap(context: android.content.Context, uri: Uri): Bitmap =
    withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val largestEdge = max(width, height)
            if (largestEdge > MAX_STICKER_EDGE) {
                val scale = MAX_STICKER_EDGE.toFloat() / largestEdge.toFloat()
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            decoder.isMutableRequired = false
        }
    }

private suspend fun decodeEditorBitmap(file: File): Bitmap =
    withContext(Dispatchers.IO) {
        check(file.isFile) { "Editor source file is missing: ${file.absolutePath}" }
        val source = ImageDecoder.createSource(file)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val largestEdge = max(width, height)
            if (largestEdge > MAX_EDITOR_EDGE) {
                val scale = MAX_EDITOR_EDGE.toFloat() / largestEdge.toFloat()
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
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
    val key = remember { "cloudreve:editor:${UUID.randomUUID()}" }
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

private fun createEditedImageFile(cacheDir: File, title: String): File {
    val dir = File(cacheDir, "preview_share/edited").apply { mkdirs() }
    val safeName = title
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .substringBeforeLast('.', missingDelimiterValue = title)
        .ifBlank { "image" }
        .lowercase(Locale.ROOT)
    return File(dir, "edited_${safeName}_${System.currentTimeMillis()}.png")
}

private object IntentFlags {
    const val Read = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
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

private const val TAG = "ImageEditorScreen"

private val editorColorPalette = listOf(
    AndroidColor.WHITE,
    AndroidColor.BLACK,
    AndroidColor.parseColor("#FF3B30"),
    AndroidColor.parseColor("#FF9500"),
    AndroidColor.parseColor("#FFCC00"),
    AndroidColor.parseColor("#34C759"),
    AndroidColor.parseColor("#00C7BE"),
    AndroidColor.parseColor("#32ADE6"),
    AndroidColor.parseColor("#007AFF"),
    AndroidColor.parseColor("#5856D6"),
    AndroidColor.parseColor("#AF52DE"),
    AndroidColor.parseColor("#FF2D55"),
)

private val editorEmojiPalette = listOf(
    "\uD83D\uDE00",
    "\uD83D\uDE03",
    "\uD83D\uDE04",
    "\uD83D\uDE0D",
    "\uD83E\uDD73",
    "\uD83D\uDE0E",
    "\uD83E\uDD29",
    "\uD83E\uDD14",
    "\uD83D\uDE0A",
    "\uD83D\uDE22",
    "\uD83D\uDE2D",
    "\uD83D\uDE21",
    "\uD83E\uDD73",
    "\uD83E\uDEE0",
    "\uD83D\uDE80",
    "\uD83D\uDD25",
    "\u2728",
    "\uD83C\uDF08",
    "\uD83C\uDF89",
    "\uD83C\uDF1F",
    "\uD83C\uDF38",
    "\uD83C\uDF3B",
    "\uD83E\uDE90",
    "\uD83D\uDC4D",
    "\uD83D\uDC4E",
    "\uD83D\uDC4F",
    "\uD83D\uDE4C",
    "\uD83D\uDE4F",
    "\uD83D\uDC9B",
    "\uD83D\uDC99",
    "\uD83D\uDC9C",
    "\uD83E\uDD0D",
    "\uD83D\uDCAB",
    "\uD83D\uDCA5",
    "\uD83C\uDFB5",
    "\uD83C\uDFA8",
)

private val editorBrushSizes = listOf(6f, 10f, 14f, 18f, 24f, 32f)

private val editorOpacitySteps = listOf(100, 80, 60, 40)

private const val MAX_STICKER_EDGE = 1600
private const val MAX_EDITOR_EDGE = 4096
