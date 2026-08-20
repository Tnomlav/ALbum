package com.example.album.ui.editor

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowInsetsControllerCompat
import com.example.album.data.MediaItem
import com.example.album.data.ThumbnailRepository
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import com.example.album.ui.components.VaultChoiceConfirmationSheet
import com.example.album.ui.components.VaultConfirmationSheet
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private enum class EditorPanel(val label: String) { Compose("构图"), Adjust("调节"), Doodle("涂鸦"), Text("文字") }

private fun doodleWidthForValue(value: Int): Float {
    // Stroke widths are stored in source-image pixels. Preview rendering
    // scales them to the displayed bitmap, while export keeps the exact px
    // value selected by the user.
    return value.coerceIn(1, 100).toFloat()
}

private fun defaultDoodleWidth(brush: EditorBrush): Float {
    return doodleWidthForValue(if (brush == EditorBrush.Eraser || brush == EditorBrush.Mosaic) 30 else 5)
}

private data class EditorHistorySnapshot(
    val edit: ImageEditState,
    val outputScale: Float,
    val quality: Int
)

@Composable
fun ImageEditorDialog(
    item: MediaItem,
    enterFromViewer: Boolean = false,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(context) { EditorTypefaceRegistry.initialize(context) }
    val english = LocalAppEnglish.current
    val editorFocusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("album_settings", android.content.Context.MODE_PRIVATE) }
    val editorAlpha = remember { Animatable(if (enterFromViewer) 1f else 0f) }
    val editorImageProgress = remember { Animatable(if (enterFromViewer) 0f else 1f) }
    var editorClosing by remember { mutableStateOf(false) }
    var preview by remember(item.uri) { mutableStateOf(ThumbnailRepository.peek(item, 360, preferences)) }
    var source by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    var state by remember { mutableStateOf(ImageEditState()) }
    var undoStack by remember { mutableStateOf<List<EditorHistorySnapshot>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<EditorHistorySnapshot>>(emptyList()) }
    var panel by remember { mutableStateOf(EditorPanel.Compose) }
    var textSubtab by remember { mutableIntStateOf(0) }
    var resolutionOpen by remember { mutableStateOf(false) }
    var customRatioOpen by remember { mutableStateOf(false) }
    var customWidth by remember { mutableStateOf("16") }
    var customHeight by remember { mutableStateOf("9") }
    var customRatioConfirmed by remember { mutableStateOf(false) }
    var outputScale by remember { mutableFloatStateOf(1f) }
    var quality by remember { mutableIntStateOf(100) }
    var saving by remember { mutableStateOf(false) }
    var drawColor by remember { mutableIntStateOf(0xFFFF3B30.toInt()) }
    var drawWidth by remember { mutableFloatStateOf(defaultDoodleWidth(EditorBrush.Pen)) }
    var brushWidths by remember { mutableStateOf<Map<EditorBrush, Float>>(emptyMap()) }
    var drawBrush by remember { mutableStateOf(EditorBrush.Pen) }
    var hasPickedDrawColor by remember { mutableStateOf(false) }
    var colorPicking by remember { mutableStateOf(false) }
    var colorPickPoint by remember { mutableStateOf<Offset?>(null) }
    var colorPickPreview by remember { mutableIntStateOf(drawColor) }
    var textColorPickingStroke by remember { mutableStateOf<Boolean?>(null) }
    var textEditStart by remember { mutableStateOf<ImageEditState?>(null) }
    var textEditing by remember { mutableStateOf(true) }
    var activeStroke by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
    var saveChoiceOpen by remember { mutableStateOf(false) }
    var saveConfirmationMode by remember { mutableStateOf<Boolean?>(null) }
    var compositionGestureStart by remember { mutableStateOf<ImageEditState?>(null) }
    var resolutionGestureStart by remember { mutableStateOf<EditorHistorySnapshot?>(null) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { editorAlpha.animateTo(1f, tween(160)) }
            launch {
                editorImageProgress.animateTo(
                    1f,
                    tween(360, easing = androidx.compose.animation.core.CubicBezierEasing(.22f, .78f, .24f, 1f))
                )
            }
        }
    }

    fun leaveEditor(after: () -> Unit) {
        if (editorClosing) return
        editorClosing = true
        scope.launch {
            editorAlpha.animateTo(0f, tween(120))
            after()
        }
    }

    fun snapshot() = EditorHistorySnapshot(state, outputScale, quality)
    fun restore(snapshot: EditorHistorySnapshot) {
        state = snapshot.edit
        outputScale = snapshot.outputScale
        quality = snapshot.quality
    }
    fun commit(next: ImageEditState) {
        if (next == state) return
        undoStack = (undoStack + snapshot()).takeLast(60)
        state = next
        redoStack = emptyList()
    }
    fun applyLive(next: ImageEditState) {
        if (next != state) state = next
    }
    fun checkpoint(before: ImageEditState) {
        if (before == state) return
        undoStack = (undoStack + EditorHistorySnapshot(before, outputScale, quality)).takeLast(60)
        redoStack = emptyList()
    }
    fun checkpoint(before: EditorHistorySnapshot) {
        if (before == snapshot()) return
        undoStack = (undoStack + before).takeLast(60)
        redoStack = emptyList()
    }
    fun undo() {
        val previous = undoStack.lastOrNull() ?: return
        redoStack = (redoStack + snapshot()).takeLast(60)
        undoStack = undoStack.dropLast(1)
        restore(previous)
    }
    fun redo() {
        val next = redoStack.lastOrNull() ?: return
        undoStack = (undoStack + snapshot()).takeLast(60)
        redoStack = redoStack.dropLast(1)
        restore(next)
    }

    fun performSave(replaceOriginal: Boolean) {
        val bitmap = source ?: return
        saving = true
        scope.launch {
            val saved = saveEditedBitmap(context, bitmap, item, state, outputScale, quality, replaceOriginal)
            saving = false
            if (saved != null) {
                preferences.edit()
                    .putLong("thumbnail_cache_generation", preferences.getLong("thumbnail_cache_generation", 0L) + 1L)
                    .apply()
                Toast.makeText(
                    context,
                    if (english) {
                        if (replaceOriginal) "Original replaced" else "Saved to Pictures/相册/已编辑"
                    } else if (replaceOriginal) "已替换原图" else "已保存到 Pictures/相册/已编辑",
                    Toast.LENGTH_SHORT
                ).show()
                leaveEditor(onSaved)
            } else Toast.makeText(context, if (english) "Save failed" else "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) performSave(replaceOriginal = true)
    }

    fun saveWithMode(replaceOriginal: Boolean) {
        val supportsReplace = supportsInPlaceEdit(item.mimeType)
        if (replaceOriginal && !supportsReplace) {
            Toast.makeText(context, if (english) "This format cannot be safely replaced; an edited copy will be saved" else "当前格式不能安全替换，已改为保存编辑副本", Toast.LENGTH_LONG).show()
            performSave(false)
        } else if (!replaceOriginal) {
            performSave(false)
        } else if (
            !item.isDocument &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)) &&
            !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())
        ) {
            val request = MediaStore.createWriteRequest(context.contentResolver, listOf(item.uri))
            writeLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            performSave(true)
        }
    }

    LaunchedEffect(item.uri) {
        source = loadEditorBitmap(context, item)
        if (source != null) preview = null
    }

    // Keep the editor in the existing Compose window. Creating a platform
    // Dialog here adds a second Window and makes the editor enter noticeably
    // later than the regular page transitions.
    BackHandler(enabled = !saving) { leaveEditor(onDismiss) }
    val activity = context as? Activity
    DisposableEffect(activity) {
            val window = activity?.window
            val oldStatus = window?.statusBarColor
            val oldNavigation = window?.navigationBarColor
            val oldNavigationDivider = window?.navigationBarDividerColor
            window?.statusBarColor = android.graphics.Color.WHITE
            window?.navigationBarColor = android.graphics.Color.WHITE
            window?.navigationBarDividerColor = android.graphics.Color.WHITE
            window?.let {
                WindowInsetsControllerCompat(it, it.decorView).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
            }
            onDispose {
                if (oldStatus != null) window?.statusBarColor = oldStatus
                if (oldNavigation != null) window?.navigationBarColor = oldNavigation
                if (oldNavigationDivider != null) window?.navigationBarDividerColor = oldNavigationDivider
            }
    }
    Surface(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = editorAlpha.value
            },
            color = Color.White.copy(alpha = if (enterFromViewer) editorImageProgress.value else 1f)
        ) {
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                val neutralState = ImageEditState()
                val saveEnabled = state != neutralState || outputScale != 1f || quality != 100
                val resetEnabled = when (panel) {
                    EditorPanel.Compose -> state.rotation != neutralState.rotation ||
                        state.straighten != neutralState.straighten ||
                        state.flipHorizontal != neutralState.flipHorizontal ||
                        state.flipVertical != neutralState.flipVertical ||
                        state.crop != neutralState.crop ||
                        state.cropRect != neutralState.cropRect ||
                        state.customCropRatio != neutralState.customCropRatio ||
                        state.composeScale != neutralState.composeScale ||
                        state.composeX != neutralState.composeX ||
                        state.composeY != neutralState.composeY
                    EditorPanel.Adjust -> state.exposure != neutralState.exposure ||
                        state.brightness != neutralState.brightness ||
                        state.contrast != neutralState.contrast ||
                        state.tint != neutralState.tint ||
                        state.temperature != neutralState.temperature ||
                        state.highlights != neutralState.highlights ||
                        state.shadows != neutralState.shadows ||
                        state.saturation != neutralState.saturation ||
                        state.vibrance != neutralState.vibrance ||
                        state.fade != neutralState.fade ||
                        state.sharpness != neutralState.sharpness ||
                        state.enhance != neutralState.enhance
                    EditorPanel.Doodle -> state.strokes.isNotEmpty()
                    EditorPanel.Text -> state.texts != neutralState.texts
                }
                Box(Modifier.fillMaxWidth().zIndex(100f)) {
                    EditorTopBar(
                    undoEnabled = undoStack.isNotEmpty(),
                    redoEnabled = redoStack.isNotEmpty(),
                    resetEnabled = resetEnabled,
                    saveEnabled = saveEnabled,
                    resetLabel = appText("重置${panel.label}", english),
                    resolutionOpen = resolutionOpen,
                    saving = saving,
                    onClose = { leaveEditor(onDismiss) },
                    onUndo = ::undo,
                    onRedo = ::redo,
                    onReset = {
                        val reset = when (panel) {
                            EditorPanel.Compose -> state.copy(rotation = 0, straighten = 0f, flipHorizontal = false, flipVertical = false, crop = CropPreset.Free, cropRect = NormalizedRect(), customCropRatio = null, composeScale = 1f, composeX = 0f, composeY = 0f)
                            EditorPanel.Adjust -> state.copy(exposure = 0f, brightness = 0f, contrast = 1f, tint = 0f, temperature = 0f, highlights = 0f, shadows = 0f, saturation = 1f, vibrance = 0f, fade = 0f, sharpness = 0f, enhance = 0f)
                            EditorPanel.Doodle -> state.copy(strokes = emptyList())
                            EditorPanel.Text -> state.copy(texts = neutralState.texts)
                        }
                        commit(reset)
                    },
                    onResolution = { resolutionOpen = !resolutionOpen },
                    onSave = {
                        when (preferences.getString("edit_save", "每次询问")) {
                            "保留二者" -> saveConfirmationMode = false
                            "替换原图" -> saveConfirmationMode = true
                            else -> saveChoiceOpen = true
                        }
                    }
                    )
                }

                val bitmap = source ?: preview
                if (bitmap == null) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .zIndex(if (panel == EditorPanel.Text && textEditing) 150f else 0f)
                    ) {
                    EditorStage(
                        source = bitmap,
                        state = state,
                        composeEnabled = panel == EditorPanel.Compose,
                        doodleEnabled = panel == EditorPanel.Doodle,
                        colorPickEnabled = (panel == EditorPanel.Doodle && colorPicking) ||
                            (panel == EditorPanel.Text && textColorPickingStroke != null),
                        // With no text yet, the canvas must remain tappable so the
                        // first tap can create the inline text box. Once a box
                        // exists, textEditing switches the stage to move/edit mode.
                        textMoveEnabled = panel == EditorPanel.Text && textEditing && state.texts.isNotEmpty(),
                        textGestureEnabled = panel == EditorPanel.Text,
                        drawColor = drawColor,
                        colorPickPoint = colorPickPoint,
                        colorPickPreview = colorPickPreview,
                        drawWidth = drawWidth,
                        drawBrush = drawBrush,
                        activeStroke = activeStroke,
                        onStrokeChanged = { activeStroke = it },
                        onStrokeFinished = { finished, normalizedWidth ->
                            if (finished.size > 1) {
                                commit(state.copy(strokes = state.strokes + EditorStroke(finished, drawColor, normalizedWidth, drawBrush)))
                            }
                            activeStroke = emptyList()
                        },
                        onColorPicked = { picked ->
                            val strokeTarget = textColorPickingStroke
                            if (panel == EditorPanel.Text && strokeTarget != null) {
                                state.texts.lastOrNull()?.let { text ->
                                    val updated = if (strokeTarget) text.copy(strokeColor = picked) else text.copy(color = picked)
                                    commit(state.copy(texts = state.texts.dropLast(1) + updated))
                                }
                                textColorPickingStroke = null
                                colorPickPoint = null
                            } else {
                                drawColor = picked
                                hasPickedDrawColor = true
                                colorPicking = false
                                colorPickPoint = null
                            }
                        },
                        onColorPickPreview = { point, sampled ->
                            colorPickPoint = point
                            colorPickPreview = sampled
                        },
                        onCropFinished = { rect -> commit(state.copy(cropRect = rect)) },
                        onCompositionChanged = { scale, x, y ->
                            applyLive(state.copy(composeScale = scale, composeX = x, composeY = y))
                        },
                        onCompositionStarted = { compositionGestureStart = state },
                        onCompositionFinished = {
                            compositionGestureStart?.let(::checkpoint)
                            compositionGestureStart = null
                        },
                        onTextChanged = { updated ->
                            if (state.texts.isNotEmpty()) {
                                if (textEditStart == null) {
                                    commit(state.copy(texts = state.texts.dropLast(1) + updated))
                                } else {
                                    textEditStart?.let(::checkpoint)
                                }
                            }
                            textEditStart = null
                        },
                        onTextLiveChanged = { updated ->
                            if (state.texts.isNotEmpty()) {
                                applyLive(state.copy(texts = state.texts.dropLast(1) + updated))
                            }
                        },
                        onTextEditStarted = { textEditStart = state; textEditing = true },
                        onTextCreate = { x, y ->
                            val created = EditorText("", x = x, y = y, color = android.graphics.Color.WHITE)
                            commit(state.copy(texts = state.texts + created))
                            textEditing = true
                        },
                        onCompletedTextMoved = { index, dx, dy ->
                            state.texts.getOrNull(index)?.let { text ->
                                val width = if (text.vertical) text.boxHeight else text.boxWidth
                                val height = if (text.vertical) text.boxWidth else text.boxHeight
                                applyLive(state.copy(texts = state.texts.toMutableList().also {
                                    it[index] = text.copy(
                                        x = (text.x + dx).coerceIn(width / 2f, 1f - width / 2f),
                                        y = (text.y + dy).coerceIn(height / 2f, 1f - height / 2f)
                                    )
                                }))
                            }
                        },
                        onCompletedTextMoveFinished = { before -> checkpoint(before) },
                        enterProgress = editorImageProgress.value,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (panel == EditorPanel.Text && textEditing) {
                        Surface(
                            onClick = {
                                if (state.texts.lastOrNull() != null) {
                                    editorFocusManager.clearFocus(force = true)
                                    textEditing = false
                                    textEditStart = null
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = (-8).dp)
                                .zIndex(200f)
                                .width(64.dp)
                                .height(34.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = EditorAccent,
                            contentColor = Color.White
                        ) {
                            Box(
                                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(appText("确认", english), color = Color.White, fontSize = 16.sp, maxLines = 1)
                            }
                        }
                    }
                    }
                }

                val controlsHeight = when (panel) {
                    EditorPanel.Doodle -> 188.dp
                    EditorPanel.Text -> if (textSubtab == 1) 180.dp else 264.dp
                    EditorPanel.Compose -> 188.dp
                    EditorPanel.Adjust -> 188.dp
                    else -> 172.dp
                }
                Surface(color = Color.White, modifier = Modifier.fillMaxWidth().height(controlsHeight).zIndex(100f)) {
                    when (panel) {
                        EditorPanel.Compose -> ComposeControls(
                            state = state,
                            commit = ::commit,
                            applyLive = ::applyLive,
                            checkpoint = ::checkpoint,
                            onCustomRatioOpenChange = {
                                customRatioOpen = it
                                if (it) customRatioConfirmed = false
                            }
                        )
                        EditorPanel.Adjust -> AdjustControls(state, ::applyLive, ::checkpoint)
                        EditorPanel.Doodle -> DoodleControls(
                            color = drawColor,
                            width = drawWidth,
                            brush = drawBrush,
                            colorPicking = colorPicking,
                            hasPickedColor = hasPickedDrawColor,
                            onColor = {
                                drawColor = it
                                hasPickedDrawColor = false
                                colorPicking = false
                                colorPickPoint = null
                            },
                            onWidth = {
                                drawWidth = it
                                val sharedBrush = drawBrush != EditorBrush.Mosaic && drawBrush != EditorBrush.Eraser
                                brushWidths = brushWidths + ((if (sharedBrush) EditorBrush.Pen else drawBrush) to it)
                            },
                            onBrush = { nextBrush ->
                                val currentKey = if (drawBrush != EditorBrush.Mosaic && drawBrush != EditorBrush.Eraser) {
                                    EditorBrush.Pen
                                } else drawBrush
                                val savedWidths = brushWidths + (currentKey to drawWidth)
                                brushWidths = savedWidths
                                drawBrush = nextBrush
                                val nextKey = if (nextBrush != EditorBrush.Mosaic && nextBrush != EditorBrush.Eraser) {
                                    EditorBrush.Pen
                                } else nextBrush
                                drawWidth = savedWidths[nextKey] ?: defaultDoodleWidth(nextBrush)
                                if (nextBrush == EditorBrush.Mosaic || nextBrush == EditorBrush.Eraser) {
                                    colorPicking = false
                                    colorPickPoint = null
                                }
                            },
                            onPickColor = {
                                colorPicking = !colorPicking
                                colorPickPoint = null
                            }
                        )
                        EditorPanel.Text -> TextControls(
                            state,
                            subtab = textSubtab,
                            editing = textEditing,
                            onSubtab = { textSubtab = it },
                            colorPickingStroke = textColorPickingStroke,
                            onUpdate = { updated -> commit(state.copy(texts = state.texts.dropLast(1) + updated)) },
                            onLiveUpdate = { updated -> applyLive(state.copy(texts = state.texts.dropLast(1) + updated)) },
                            onCheckpoint = ::checkpoint,
                            onPickColor = { stroke ->
                                colorPicking = false
                                colorPickPoint = null
                                textColorPickingStroke = if (textColorPickingStroke == stroke) null else stroke
                            }
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().background(Color.White).zIndex(100f)) {
                    Column(Modifier.fillMaxWidth().background(Color.White)) {
                        EditorTabs(panel) {
                            panel = it
                            colorPicking = false
                            textColorPickingStroke = null
                        }
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                                .background(Color.White)
                        )
                    }
                }
            }
            /*
            if (customRatioOpen && panel == EditorPanel.Compose) {
                EditorRatioPopoverNew(
                    title = appText("自定义裁剪比例", english),
                    width = customWidth,
                    height = customHeight,
                    onWidthChange = { customWidth = it.filter(Char::isDigit).take(4) },
                    onHeightChange = { customHeight = it.filter(Char::isDigit).take(4) },
                    onDismiss = { customRatioOpen = false },
                    onConfirm = {
                        val width = customWidth.toFloatOrNull()
                        val height = customHeight.toFloatOrNull()
                        if (width != null && height != null && width > 0f && height > 0f) {
                            commit(state.copy(crop = CropPreset.Custom, customCropRatio = width / height, cropRect = NormalizedRect()))
                            customRatioOpen = false
                        }
                    }
                )
            }
                */
            if (customRatioOpen && panel == EditorPanel.Compose) {
                EditorRatioPopoverNew(
                    width = customWidth,
                    height = customHeight,
                    confirmed = customRatioConfirmed,
                    verticalOffset = (-256).dp,
                    onWidthChange = {
                        customWidth = it.filter(Char::isDigit).take(4)
                        customRatioConfirmed = false
                    },
                    onHeightChange = {
                        customHeight = it.filter(Char::isDigit).take(4)
                        customRatioConfirmed = false
                    },
                    onDismiss = { customRatioOpen = false },
                    onConfirm = {
                        val width = customWidth.toFloatOrNull()
                        val height = customHeight.toFloatOrNull()
                        if (width != null && height != null && width > 0f && height > 0f) {
                            commit(state.copy(crop = CropPreset.Custom, customCropRatio = width / height, cropRect = NormalizedRect()))
                            customRatioConfirmed = true
                        }
                    }
                )
            }
            if (resolutionOpen) {
                ResolutionOverlay(
                    source = source ?: preview,
                    originalWidth = item.width,
                    originalHeight = item.height,
                    state = state,
                    scale = outputScale,
                    quality = quality,
                    onDismiss = { resolutionOpen = false },
                    onScale = { outputScale = it },
                    onQuality = { quality = it },
                    onChangeStarted = { resolutionGestureStart = snapshot() },
                    onChangeFinished = {
                        resolutionGestureStart?.let { before -> checkpoint(before) }
                        resolutionGestureStart = null
                    }
                )
            }
            }
        }

        if (saveChoiceOpen) {
            val keepBoth = appText("保留二者", english)
            val replace = appText("替换原图", english)
            VaultChoiceConfirmationSheet(
                title = appText("如何处理新图", english),
                choices = listOf(keepBoth, replace),
                onDismiss = { saveChoiceOpen = false },
                onChoice = { choice ->
                    saveChoiceOpen = false
                    saveWithMode(choice == replace)
                }
            )
        }
        saveConfirmationMode?.let { replaceOriginal ->
            VaultConfirmationSheet(
                title = appText(if (replaceOriginal) "替换原图" else "保留二者", english),
                body = if (english) {
                    if (replaceOriginal) "The edited image will replace the current file. Confirm before saving."
                    else "The original will be kept and the edited image will be saved as a new copy. Confirm before saving."
                } else {
                    if (replaceOriginal) "编辑后的图片将替换当前文件，请确认后保存。"
                    else "原图将保留，编辑后的图片会另存为新副本，请确认后保存。"
                },
                confirmLabel = appText(if (replaceOriginal) "替换原图" else "保存副本", english),
                danger = replaceOriginal,
                onDismiss = { saveConfirmationMode = null },
                onConfirm = {
                    saveConfirmationMode = null
                    saveWithMode(replaceOriginal)
                }
            )
        }
}

internal fun supportsInPlaceEdit(mimeType: String): Boolean =
    mimeType.equals("image/jpeg", true) ||
        mimeType.equals("image/png", true) ||
        mimeType.equals("image/webp", true)

@Composable
private fun EditorTopBar(
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    resetEnabled: Boolean,
    saveEnabled: Boolean,
    resetLabel: String,
    resolutionOpen: Boolean,
    saving: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onResolution: () -> Unit,
    onSave: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val english = LocalAppEnglish.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).background(Color.White).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier.size(42.dp).clickable(enabled = !saving, onClick = onClose),
            color = Color(0xFFF4F4F2),
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(EditorPrototypeIcons.Close, appText("关闭", english), tint = Color(0xFF232727), modifier = Modifier.size(24.dp))
            }
        }
        Row(Modifier.width(114.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            EditorTopIcon(EditorPrototypeIcons.Undo, appText("撤回", english), undoEnabled, onUndo)
            EditorTopIcon(EditorPrototypeIcons.Redo, appText("重做", english), redoEnabled, onRedo)
            EditorTopIcon(EditorPrototypeIcons.Repeat, resetLabel, resetEnabled, onReset)
        }
        Spacer(Modifier.weight(1f))
        Surface(
            modifier = Modifier.height(32.dp).offset(x = (-8).dp).clickable(onClick = onResolution),
            color = if (resolutionOpen) Color(0xFFE9E9E7) else Color(0xFFF4F4F2),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(appText("分辨率", english), color = Color(0xFF232727))
                Icon(
                    if (resolutionOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF232727),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Surface(
            modifier = Modifier.width(50.dp).height(32.dp).clickable(enabled = saveEnabled && !saving, onClick = onSave),
            color = if (saveEnabled && !saving) accent else Color(0xFFE9E9E7),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    appText(if (saving) "保存中" else "完成", english),
                    color = if (saveEnabled && !saving) Color.White else Color(0xFF9A9D9B),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun EditorTopIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(38.dp)) {
        Icon(icon, label, tint = if (enabled) Color(0xFF232727) else Color(0xFFB9BCBA), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ResolutionOverlay(
    source: Bitmap?,
    originalWidth: Int,
    originalHeight: Int,
    state: ImageEditState,
    scale: Float,
    quality: Int,
    onDismiss: () -> Unit,
    onScale: (Float) -> Unit,
    onQuality: (Int) -> Unit,
    onChangeStarted: () -> Unit,
    onChangeFinished: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 58.dp, end = 12.dp).widthIn(max = 358.dp),
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 18.dp
        ) {
            ResolutionPanel(
                source,
                originalWidth,
                originalHeight,
                state,
                scale,
                quality,
                onScale,
                onQuality,
                onChangeStarted,
                onChangeFinished
            )
        }
    }
}

@Composable
private fun ResolutionPanel(
    source: Bitmap?,
    originalWidth: Int,
    originalHeight: Int,
    state: ImageEditState,
    scale: Float,
    quality: Int,
    onScale: (Float) -> Unit,
    onQuality: (Int) -> Unit,
    onChangeStarted: () -> Unit,
    onChangeFinished: () -> Unit
) {
    val english = LocalAppEnglish.current
    val fullWidth = originalWidth.takeIf { it > 0 } ?: source?.width ?: 0
    val fullHeight = originalHeight.takeIf { it > 0 } ?: source?.height ?: 0
    val exportSample = editorExportSampleSize(fullWidth, fullHeight)
    val sourceWidth = (fullWidth / exportSample).coerceAtLeast(1)
    val sourceHeight = (fullHeight / exportSample).coerceAtLeast(1)
    val (outputWidth, outputHeight) = editorOutputDimensions(sourceWidth, sourceHeight, state, scale)
    val estimatedMb = outputWidth.toLong() * outputHeight * (.10f + quality / 100f * .22f) / (1024f * 1024f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(appText("分辨率", english), color = EditorInk, fontWeight = FontWeight.SemiBold)
            Text("$outputWidth × $outputHeight", color = EditorMuted, style = MaterialTheme.typography.labelMedium)
        }
        EditorThinSlider(
            scale,
            onScale,
            valueRange = .2f..1f,
            steps = 3,
            onValueChangeStarted = onChangeStarted,
            onValueChangeFinished = onChangeFinished,
            modifier = Modifier.fillMaxWidth().height(42.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("20%", "40%", "60%", "80%", "100%").forEach { Text(it, color = EditorMuted, style = MaterialTheme.typography.labelSmall) }
        }
        Spacer(Modifier.height(18.dp))
        Text(appText("图片质量", english), color = EditorInk, fontWeight = FontWeight.SemiBold)
        EditorThinSlider(
            quality.toFloat(),
            { onQuality((it / 10f).roundToInt() * 10) },
            valueRange = 10f..100f,
            steps = 8,
            onValueChangeStarted = onChangeStarted,
            onValueChangeFinished = onChangeFinished,
            modifier = Modifier.fillMaxWidth().height(42.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(appText("最低", english), color = EditorMuted, style = MaterialTheme.typography.labelSmall)
            Text(appText("最高", english), color = EditorMuted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(appText("图片大小", english), color = EditorInk, fontWeight = FontWeight.SemiBold)
            Text(if (english) "about ${"%.2f".format(estimatedMb)} MB" else "约 ${"%.2f".format(estimatedMb)} MB", color = EditorMuted)
        }
    }
}

@Composable
private fun EditorStage(
    source: Bitmap,
    state: ImageEditState,
    composeEnabled: Boolean,
    doodleEnabled: Boolean,
    colorPickEnabled: Boolean,
    textMoveEnabled: Boolean,
    drawColor: Int,
    colorPickPoint: Offset?,
    colorPickPreview: Int,
    drawWidth: Float,
    drawBrush: EditorBrush,
    activeStroke: List<NormalizedPoint>,
    onStrokeChanged: (List<NormalizedPoint>) -> Unit,
    onStrokeFinished: (List<NormalizedPoint>, Float) -> Unit,
    onColorPicked: (Int) -> Unit,
    onColorPickPreview: (Offset, Int) -> Unit,
    onCropFinished: (NormalizedRect) -> Unit,
    onCompositionChanged: (Float, Float, Float) -> Unit,
    onCompositionStarted: () -> Unit,
    onCompositionFinished: () -> Unit,
    onTextChanged: (EditorText) -> Unit,
    onTextLiveChanged: (EditorText) -> Unit,
    onTextEditStarted: (EditorText) -> Unit,
    textGestureEnabled: Boolean,
    onTextCreate: (Float, Float) -> Unit,
    onCompletedTextMoved: (Int, Float, Float) -> Unit,
    onCompletedTextMoveFinished: (ImageEditState) -> Unit,
    enterProgress: Float = 1f,
    modifier: Modifier
) {
    val english = LocalAppEnglish.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val pickScope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary
    val latestState by rememberUpdatedState(state)
    val latestOnCompositionChanged by rememberUpdatedState(onCompositionChanged)
    val latestOnCompositionStarted by rememberUpdatedState(onCompositionStarted)
    val latestOnCompositionFinished by rememberUpdatedState(onCompositionFinished)
    val latestOnTextCreate by rememberUpdatedState(onTextCreate)
    val latestOnCompletedTextMoved by rememberUpdatedState(onCompletedTextMoved)
    val latestOnCompletedTextMoveFinished by rememberUpdatedState(onCompletedTextMoveFinished)
    val textTouchSlop = with(LocalDensity.current) { 8.dp.toPx() }
    val geometry = remember(source, state.rotation, state.flipHorizontal, state.flipVertical) {
        geometryBitmap(source, state.copy(straighten = 0f))
    }
    val displayGeometry = remember(source, state.rotation, state.straighten, state.flipHorizontal, state.flipVertical) {
        geometryBitmap(source, state)
    }
    val displayBitmap = remember(geometry, displayGeometry, state.crop, state.cropRect, state.composeScale, state.composeX, state.composeY, composeEnabled) {
        if (!composeEnabled) {
            if (state.crop != CropPreset.Original) {
                croppedGeometryBitmap(displayGeometry, state, geometry.width, geometry.height)
            } else displayGeometry
        } else {
            geometry
        }
    }
    // Each mosaic stroke samples the image as it looked immediately before
    // that stroke, including all earlier doodle strokes.
    val mosaicSources = remember(displayBitmap, state.strokes) {
        state.strokes.mapIndexed { index, stroke ->
            if (stroke.brush == EditorBrush.Mosaic) {
                renderDoodleComposite(displayBitmap, state.strokes.take(index))
            } else null
        }
    }
    val activeMosaicSource = remember(displayBitmap, state.strokes) {
        renderDoodleComposite(displayBitmap, state.strokes)
    }
    val targetRatio = when {
        state.crop == CropPreset.Free && composeEnabled -> geometry.width.toFloat() / geometry.height
        state.crop == CropPreset.Custom -> state.customCropRatio ?: displayBitmap.width.toFloat() / displayBitmap.height
        else -> state.crop.ratio ?: displayBitmap.width.toFloat() / displayBitmap.height
    }
    var liveText by remember(state.texts) { mutableStateOf(state.texts.lastOrNull()) }
    val latestLiveText by rememberUpdatedState(liveText)
    val enterScale = 1.38f + (1f - 1.38f) * enterProgress
    val enterTranslationY = with(androidx.compose.ui.platform.LocalDensity.current) { 62.dp.toPx() * (1f - enterProgress) }
    BoxWithConstraints(
        modifier
            .graphicsLayer {
                scaleX = enterScale
                scaleY = enterScale
                translationY = enterTranslationY
            }
            .background(Color(0xFFF6F6F4))
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        val boundedWidth = if (maxWidth > 370.dp) 370.dp else maxWidth
        val availableRatio = boundedWidth.value / maxHeight.value
        val sourceRatio = if (!composeEnabled) {
            displayBitmap.width.toFloat() / displayBitmap.height.coerceAtLeast(1)
        } else {
            geometry.width.toFloat() / geometry.height.coerceAtLeast(1)
        }
        val viewportWidth = if (availableRatio > sourceRatio) maxHeight * sourceRatio else boundedWidth
        val viewportHeight = if (availableRatio > sourceRatio) maxHeight else boundedWidth / sourceRatio.coerceAtLeast(.0001f)
        val viewportRatio = viewportWidth.value / viewportHeight.value
        val cropWidth = if (viewportRatio > targetRatio) viewportHeight.value * targetRatio else viewportWidth.value
        val cropHeight = if (viewportRatio > targetRatio) viewportHeight.value else viewportWidth.value / targetRatio.coerceAtLeast(.0001f)
        val cropWidthFraction = (cropWidth / viewportWidth.value).coerceIn(.01f, 1f)
        val cropHeightFraction = (cropHeight / viewportHeight.value).coerceIn(.01f, 1f)
        val centeredFrame = NormalizedRect(
            left = (1f - cropWidthFraction) / 2f,
            top = (1f - cropHeightFraction) / 2f,
            right = (1f + cropWidthFraction) / 2f,
            bottom = (1f + cropHeightFraction) / 2f
        )
        // A full rect is the reset value. Preset ratios use their centered
        // frame until the user has explicitly moved or resized the frame.
        val initialFrame = if (state.crop == CropPreset.Free || state.cropRect != NormalizedRect()) {
            state.cropRect
        } else centeredFrame
        var liveFrame by remember(state.crop, state.cropRect, cropWidthFraction, cropHeightFraction) {
            mutableStateOf(initialFrame)
        }
        // The source is the outer layer. The crop border is a smaller overlay
        // inside it, so content outside the border remains visible normally.
        val angle = Math.toRadians(state.straighten.toDouble())
        val cosine = abs(cos(angle)).toFloat()
        val sine = abs(sin(angle)).toFloat()
        val compositionScale = if (composeEnabled) state.composeScale.coerceAtLeast(1f) else 1f
        val frameWidth = viewportWidth.value * liveFrame.width
        val frameHeight = viewportHeight.value * liveFrame.height
        val imageWidth = viewportWidth.value * compositionScale
        val imageHeight = viewportHeight.value * compositionScale
        // For an axis-aligned crop frame inside a rotated rectangle, each
        // image axis constrains the sum of the frame sides projected onto it.
        // Use the frame dimensions (rather than the whole viewport) so 16:9,
        // 9:16 and custom ratios remain fully inscribed at every angle.
        val safeFrameScale = minOf(
            1f,
            imageWidth / (frameWidth * cosine + frameHeight * sine).coerceAtLeast(.0001f),
            imageHeight / (frameWidth * sine + frameHeight * cosine).coerceAtLeast(.0001f)
        ).coerceIn(.01f, 1f)
        // Keep the image on the full viewport. The crop frame is an overlay
        // and may be smaller after rotation, so it must not clip the image.
        val stageWidth = viewportWidth
        val stageHeight = viewportHeight
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        val previewShortSidePx = with(androidx.compose.ui.platform.LocalDensity.current) {
            min(stageWidth.toPx(), stageHeight.toPx()).coerceAtLeast(1f)
        }
        val gesture = when {
            colorPickEnabled -> Modifier.pointerInput(displayBitmap) {
                var pendingPick: kotlinx.coroutines.Job? = null
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pendingPick?.cancel()
                    pendingPick = null
                    var sampled = displayBitmap.getPixel(
                        (down.position.x / size.width * displayBitmap.width).toInt().coerceIn(0, displayBitmap.width - 1),
                        (down.position.y / size.height * displayBitmap.height).toInt().coerceIn(0, displayBitmap.height - 1)
                    )
                    onColorPickPreview(down.position, sampled)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        val position = change.position
                        sampled = displayBitmap.getPixel(
                            (position.x / size.width * displayBitmap.width).toInt().coerceIn(0, displayBitmap.width - 1),
                            (position.y / size.height * displayBitmap.height).toInt().coerceIn(0, displayBitmap.height - 1)
                        )
                        onColorPickPreview(position, sampled)
                        change.consume()
                        if (!change.pressed) {
                            val picked = sampled
                            pendingPick = pickScope.launch {
                                kotlinx.coroutines.delay(1000L)
                                onColorPicked(picked)
                            }
                            break
                        }
                    }
                }
            }
            doodleEnabled -> Modifier.pointerInput(drawColor, drawWidth, drawBrush, state.strokes.size) {
                val points = ArrayList<NormalizedPoint>(256)
                var lastPublishedAt = 0L
                detectDragGestures(
                    onDragStart = { offset ->
                        points.clear()
                        points += NormalizedPoint((offset.x / size.width).coerceIn(0f, 1f), (offset.y / size.height).coerceIn(0f, 1f))
                        lastPublishedAt = SystemClock.uptimeMillis()
                        onStrokeChanged(points.toList())
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val point = NormalizedPoint(
                            (change.position.x / size.width).coerceIn(0f, 1f),
                            (change.position.y / size.height).coerceIn(0f, 1f)
                        )
                        val previous = points.lastOrNull()
                        val dx = point.x - (previous?.x ?: point.x)
                        val dy = point.y - (previous?.y ?: point.y)
                        if (dx * dx + dy * dy >= .000004f && points.size < 4_000) {
                            points += point
                            val now = SystemClock.uptimeMillis()
                            if (now - lastPublishedAt >= 16L) {
                                onStrokeChanged(points.toList())
                                lastPublishedAt = now
                            }
                        }
                    },
                    onDragEnd = {
                        val finished = points.toList()
                        onStrokeChanged(finished)
                        onStrokeFinished(finished, drawWidth / previewShortSidePx)
                    }
                )
            }
            composeEnabled -> Modifier
                // The frame gesture is deliberately separate from the image
                // gesture. It only consumes a drag after a frame handle, edge,
                // or the inside of the frame has been hit.
                .pointerInput(state.crop, state.cropRect) {
                    var handle = -1 // 0..3 corners, 4..7 edges, 8 move
                    var rect = liveFrame
                    fun shownRect(value: NormalizedRect) = NormalizedRect(
                        left = .5f + (value.left - .5f) * safeFrameScale,
                        top = .5f + (value.top - .5f) * safeFrameScale,
                        right = .5f + (value.right - .5f) * safeFrameScale,
                        bottom = .5f + (value.bottom - .5f) * safeFrameScale
                    )
                    fun distanceSquared(a: NormalizedPoint, b: NormalizedPoint): Float {
                        val dx = a.x - b.x
                        val dy = a.y - b.y
                        return dx * dx + dy * dy
                    }
                    val fixedRatio = state.crop != CropPreset.Free
                    val normalizedRatio = (targetRatio * viewportHeight.value / viewportWidth.value).coerceAtLeast(.0001f)
                    val minSize = .06f
                    fun resizeWithRatio(base: NormalizedRect, handle: Int, x: Float, y: Float): NormalizedRect {
                        val ratio = normalizedRatio
                        fun fit(rawWidth: Float, rawHeight: Float, maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
                            val minimumWidth = maxOf(minSize, minSize * ratio)
                            val minimumHeight = maxOf(minSize, minSize / ratio)
                            var width = rawWidth.coerceAtLeast(minimumWidth)
                            var height = width / ratio
                            if (height < minimumHeight) {
                                height = minimumHeight
                                width = height * ratio
                            }
                            val scale = minOf(1f, maxWidth / width, maxHeight / height)
                            return (width * scale) to (height * scale)
                        }
                        return when (handle) {
                            0, 1, 2, 3 -> {
                                val anchorX = if (handle == 0 || handle == 2) base.right else base.left
                                val anchorY = if (handle == 0 || handle == 1) base.bottom else base.top
                                val maxWidth = if (anchorX >= .5f) anchorX else 1f - anchorX
                                val maxHeight = if (anchorY >= .5f) anchorY else 1f - anchorY
                                // Keep the drag distance directional. Once the
                                // pointer crosses the opposite anchor, further
                                // movement must stay at the minimum instead of
                                // turning into a resize in the other direction.
                                val rawWidth = if (handle == 0 || handle == 2) anchorX - x else x - anchorX
                                val rawHeight = rawWidth / ratio
                                val (width, height) = fit(rawWidth, rawHeight, maxWidth, maxHeight)
                                val left = if (handle == 0 || handle == 2) anchorX - width else anchorX
                                val top = if (handle == 0 || handle == 1) anchorY - height else anchorY
                                NormalizedRect(left, top, left + width, top + height)
                            }
                            4, 5 -> {
                                val anchorY = if (handle == 4) base.bottom else base.top
                                val centerX = (base.left + base.right) / 2f
                                val maxHeight = if (handle == 4) anchorY else 1f - anchorY
                                val maxWidth = 2f * min(centerX, 1f - centerX)
                                val rawHeight = if (handle == 4) anchorY - y else y - anchorY
                                val (width, height) = fit(rawHeight * ratio, rawHeight, maxWidth, maxHeight)
                                val top = if (handle == 4) anchorY - height else anchorY
                                NormalizedRect(centerX - width / 2f, top, centerX + width / 2f, top + height)
                            }
                            6, 7 -> {
                                val anchorX = if (handle == 6) base.right else base.left
                                val centerY = (base.top + base.bottom) / 2f
                                val maxWidth = if (handle == 6) anchorX else 1f - anchorX
                                val maxHeight = 2f * min(centerY, 1f - centerY)
                                val rawWidth = if (handle == 6) anchorX - x else x - anchorX
                                val (width, height) = fit(rawWidth, rawWidth / ratio, maxWidth, maxHeight)
                                val left = if (handle == 6) anchorX - width else anchorX
                                NormalizedRect(left, centerY - height / 2f, left + width, centerY + height / 2f)
                            }
                            else -> base
                        }
                    }
                    detectDragGestures(
                        onDragStart = { offset ->
                            val point = NormalizedPoint(offset.x / size.width, offset.y / size.height)
                            val visible = shownRect(rect)
                            // Keep the hit target wider than the visible 2dp
                            // line so a slight touch outside the frame still
                            // starts the intended edge/corner gesture.
                            val radiusX = (48f * density / size.width).coerceAtLeast(.024f)
                            val radiusY = (48f * density / size.height).coerceAtLeast(.024f)
                            val innerRadiusX = (18f * density / size.width).coerceAtLeast(.012f)
                            val innerRadiusY = (18f * density / size.height).coerceAtLeast(.012f)
                            val radiusSquared = maxOf(radiusX, radiusY) * maxOf(radiusX, radiusY)
                            val compactFrame = (visible.right - visible.left) * size.width <= 132f * density ||
                                (visible.bottom - visible.top) * size.height <= 132f * density
                            val insideFrame = point.x in visible.left..visible.right && point.y in visible.top..visible.bottom
                            if (compactFrame && insideFrame) {
                                // A compact frame keeps its entire inner area
                                // for moving; resize handles remain outside.
                                handle = 8
                            } else {
                                val corners = listOf(
                                    NormalizedPoint(visible.left, visible.top), NormalizedPoint(visible.right, visible.top),
                                    NormalizedPoint(visible.left, visible.bottom), NormalizedPoint(visible.right, visible.bottom)
                                )
                                handle = corners.indices.minByOrNull { distanceSquared(corners[it], point) }
                                    ?.takeIf { distanceSquared(corners[it], point) <= radiusSquared } ?: -1
                                if (handle < 0) {
                                    val midX = (visible.left + visible.right) / 2f
                                    val midY = (visible.top + visible.bottom) / 2f
                                    // Reserve a central touch target for moving the
                                    // whole frame, even when the frame is compact.
                                    val moveHalfX = (44f * density / size.width).coerceAtMost((visible.right - visible.left) / 2f)
                                    val moveHalfY = (44f * density / size.height).coerceAtMost((visible.bottom - visible.top) / 2f)
                                    if (point.x in midX - moveHalfX..midX + moveHalfX && point.y in midY - moveHalfY..midY + moveHalfY) {
                                        handle = 8
                                    }
                                }
                            }
                            if (handle < 0) {
                                val midX = (visible.left + visible.right) / 2f
                                val midY = (visible.top + visible.bottom) / 2f
                                val edgeDistances = listOf(
                                    if (point.x in visible.left - radiusX..visible.right + radiusX && point.y in visible.top - innerRadiusY..visible.top + radiusY) abs(point.y - visible.top) else Float.POSITIVE_INFINITY,
                                    if (point.x in visible.left - radiusX..visible.right + radiusX && point.y in visible.bottom - radiusY..visible.bottom + innerRadiusY) abs(point.y - visible.bottom) else Float.POSITIVE_INFINITY,
                                    if (point.y in visible.top - radiusY..visible.bottom + radiusY && point.x in visible.left - innerRadiusX..visible.left + radiusX) abs(point.x - visible.left) else Float.POSITIVE_INFINITY,
                                    if (point.y in visible.top - radiusY..visible.bottom + radiusY && point.x in visible.right - radiusX..visible.right + innerRadiusX) abs(point.x - visible.right) else Float.POSITIVE_INFINITY
                                )
                                handle = edgeDistances.indices.minByOrNull { edgeDistances[it] }
                                    ?.takeIf { edgeDistances[it] <= maxOf(radiusX, radiusY) }
                                    ?.plus(4) ?: -1
                            }
                            if (handle < 0 && point.x in visible.left..visible.right && point.y in visible.top..visible.bottom) {
                                handle = 8
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (handle < 0) return@detectDragGestures
                            change.consume()
                            val dx = dragAmount.x / size.width / safeFrameScale
                            val dy = dragAmount.y / size.height / safeFrameScale
                            val x = (.5f + (change.position.x / size.width - .5f) / safeFrameScale).coerceIn(0f, 1f)
                            val y = (.5f + (change.position.y / size.height - .5f) / safeFrameScale).coerceIn(0f, 1f)
                            rect = if (fixedRatio && handle in 0..7) {
                                resizeWithRatio(rect, handle, x, y)
                            } else {
                                when (handle) {
                                    0 -> rect.copy(left = x.coerceIn(0f, rect.right - minSize), top = y.coerceIn(0f, rect.bottom - minSize))
                                    1 -> rect.copy(right = x.coerceIn(rect.left + minSize, 1f), top = y.coerceIn(0f, rect.bottom - minSize))
                                    2 -> rect.copy(left = x.coerceIn(0f, rect.right - minSize), bottom = y.coerceIn(rect.top + minSize, 1f))
                                    3 -> rect.copy(right = x.coerceIn(rect.left + minSize, 1f), bottom = y.coerceIn(rect.top + minSize, 1f))
                                    4 -> rect.copy(top = y.coerceIn(0f, rect.bottom - minSize))
                                    5 -> rect.copy(bottom = y.coerceIn(rect.top + minSize, 1f))
                                    6 -> rect.copy(left = x.coerceIn(0f, rect.right - minSize))
                                    7 -> rect.copy(right = x.coerceIn(rect.left + minSize, 1f))
                                    else -> {
                                        val width = rect.width
                                        val height = rect.height
                                        val left = (rect.left + dx).coerceIn(0f, 1f - width)
                                        val top = (rect.top + dy).coerceIn(0f, 1f - height)
                                        NormalizedRect(left, top, left + width, top + height)
                                    }
                                }
                            }
                            liveFrame = rect
                        },
                        onDragEnd = { if (handle >= 0) onCropFinished(rect) },
                        onDragCancel = { handle = -1 }
                    )
                }
                .pointerInput(state.crop, state.cropRect, state.composeScale) {
                    var lastTapAt = 0L
                    var lastTapPosition = androidx.compose.ui.geometry.Offset.Zero
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var moved = false
                        var multiTouch = false
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                            if (event.changes.count { it.pressed } > 1) multiTouch = true
                            if (event.changes.any { it.position != it.previousPosition }) moved = true
                            pressed = event.changes.any { it.pressed }
                        }
                        if (!moved && !multiTouch) {
                            val now = SystemClock.uptimeMillis()
                            val doubleTap = now - lastTapAt <= 320L &&
                                (down.position - lastTapPosition).getDistance() <= 48f * density
                            if (doubleTap) {
                                val point = NormalizedPoint(down.position.x / size.width, down.position.y / size.height)
                                val visible = NormalizedRect(
                                    left = .5f + (liveFrame.left - .5f) * safeFrameScale,
                                    top = .5f + (liveFrame.top - .5f) * safeFrameScale,
                                    right = .5f + (liveFrame.right - .5f) * safeFrameScale,
                                    bottom = .5f + (liveFrame.bottom - .5f) * safeFrameScale
                                )
                                if (point.x in visible.left..visible.right && point.y in visible.top..visible.bottom) {
                                    onCropFinished(if (state.crop == CropPreset.Free) NormalizedRect() else centeredFrame)
                                } else {
                                    val targetScale = if (latestState.composeScale > 1.01f) 1f else 2.5f
                                    latestOnCompositionStarted()
                                    latestOnCompositionChanged(
                                        targetScale,
                                        if (targetScale <= 1.01f) 0f else latestState.composeX,
                                        if (targetScale <= 1.01f) 0f else latestState.composeY
                                    )
                                    latestOnCompositionFinished()
                                }
                                lastTapAt = 0L
                            } else {
                                lastTapAt = now
                                lastTapPosition = down.position
                            }
                        }
                    }
                }
                .pointerInput(state.crop) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var liveScale = latestState.composeScale
                    var liveX = latestState.composeX
                    var liveY = latestState.composeY
                    var compositionStarted = false
                    var pressed: Boolean
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val frameConsumed = event.changes.any { it.isConsumed }
                        val multiTouch = event.changes.count { it.pressed } >= 2
                        if (!frameConsumed && multiTouch && (zoom != 1f || pan != androidx.compose.ui.geometry.Offset.Zero)) {
                            if (!compositionStarted) {
                                latestOnCompositionStarted()
                                compositionStarted = true
                            }
                            liveScale = (liveScale * zoom).coerceIn(1f, 4f)
                            val maxX = (size.width * (liveScale - 1f) / 2f).coerceAtLeast(1f)
                            val maxY = (size.height * (liveScale - 1f) / 2f).coerceAtLeast(1f)
                            liveX = (liveX - pan.x / maxX).coerceIn(-1f, 1f)
                            liveY = (liveY - pan.y / maxY).coerceIn(-1f, 1f)
                            event.changes.forEach { it.consume() }
                            latestOnCompositionChanged(liveScale, liveX, liveY)
                        }
                        pressed = event.changes.any { it.pressed }
                    } while (pressed)
                    if (compositionStarted) latestOnCompositionFinished()
                }
            }
            else -> Modifier
        }
        val textDismissModifier = if (textMoveEnabled) {
            Modifier.pointerInput(textMoveEnabled) {
                detectTapGestures { point ->
                    val overlay = latestLiveText ?: return@detectTapGestures
                    val width = size.width * (if (overlay.vertical) overlay.boxHeight else overlay.boxWidth).coerceIn(.12f, .96f)
                    val height = size.height * (if (overlay.vertical) overlay.boxWidth else overlay.boxHeight).coerceIn(.08f, .96f)
                    val left = (size.width * overlay.x - width / 2f).coerceIn(0f, size.width - width)
                    val top = (size.height * overlay.y - height / 2f).coerceIn(0f, size.height - height)
                    if (!Rect(left, top, left + width, top + height).contains(point)) {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                    }
                }
            }
        } else Modifier
        val textGestureModifier = if (textGestureEnabled && !textMoveEnabled) {
            Modifier.pointerInput(textGestureEnabled, textMoveEnabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val before = latestState
                    fun bounds(text: EditorText): Rect {
                        val width = size.width * (if (text.vertical) text.boxHeight else text.boxWidth).coerceIn(.12f, .96f)
                        val height = size.height * (if (text.vertical) text.boxWidth else text.boxHeight).coerceIn(.08f, .96f)
                        val left = (size.width * text.x - width / 2f).coerceIn(0f, size.width - width)
                        val top = (size.height * text.y - height / 2f).coerceIn(0f, size.height - height)
                        return Rect(left, top, left + width, top + height)
                    }
                    val hitIndex = before.texts.indices.reversed().firstOrNull { bounds(before.texts[it]).contains(down.position) }
                    var previous = down.position
                    var totalDistance = 0f
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            val delta = change.position - previous
                            totalDistance += delta.getDistance()
                            if (totalDistance > textTouchSlop) {
                                if (hitIndex != null) {
                                    moved = true
                                    change.consume()
                                    latestOnCompletedTextMoved(hitIndex, delta.x / size.width, delta.y / size.height)
                                }
                                previous = change.position
                            }
                        } else {
                            if (!moved && hitIndex == null) {
                                latestOnTextCreate(
                                    (down.position.x / size.width).coerceIn(0f, 1f),
                                    (down.position.y / size.height).coerceIn(0f, 1f)
                                )
                            } else if (moved && hitIndex != null) {
                                latestOnCompletedTextMoveFinished(before)
                            }
                            break
                        }
                    }
                }
            }
        } else Modifier
        Box(Modifier.size(stageWidth, stageHeight).then(textDismissModifier).then(textGestureModifier)) {
            androidx.compose.foundation.Image(
                displayBitmap.asImageBitmap(),
                contentDescription = itemDescription(state, english),
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    // Compose renders straighten live on the source geometry.
                    // Other panels receive a bitmap with straighten already
                    // baked in, so applying rotation again would tilt it twice.
                    rotationZ = if (composeEnabled) state.straighten else 0f
                    if (composeEnabled) {
                        scaleX = state.composeScale
                        scaleY = state.composeScale
                        translationX = -state.composeX * size.width * (state.composeScale - 1f) / 2f
                        translationY = -state.composeY * size.height * (state.composeScale - 1f) / 2f
                    } else {
                        scaleX = 1f
                        scaleY = 1f
                    }
                },
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(previewColorMatrix(state))
            )
            val visibleStrokes = state.strokes + if (activeStroke.size > 1) {
                listOf(EditorStroke(activeStroke, drawColor, drawWidth / previewShortSidePx, drawBrush))
            } else emptyList()
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
            ) {
                val canvasShortSide = min(size.width, size.height).coerceAtLeast(1f)
                visibleStrokes.forEachIndexed { strokeIndex, stroke ->
                    if (stroke.points.isEmpty()) return@forEachIndexed
                    val path = Path().apply {
                        moveTo(stroke.points.first().x * size.width, stroke.points.first().y * size.height)
                        stroke.points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                    }
                    // Stroke widths are normalized to the preview canvas short
                    // side, matching the HTML canvas CSS-pixel behavior.
                    val strokeWidth = stroke.width * canvasShortSide
                    if (stroke.brush == EditorBrush.Eraser) {
                        drawPath(
                            path,
                            Color.Transparent,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth * 1.8f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            ),
                            blendMode = BlendMode.Clear
                        )
                        return@forEachIndexed
                    }
                    if (stroke.brush == EditorBrush.Mosaic) {
                        val tile = (strokeWidth / 3f).coerceAtLeast(2f)
                        val mosaicSource = mosaicSources.getOrNull(strokeIndex) ?: activeMosaicSource
                        stroke.points.filterIndexed { pointIndex, _ -> pointIndex % 2 == 0 }.forEach { point ->
                            for (row in -1..1) {
                                for (column in -1..1) {
                                    val offsetX = column * tile
                                    val offsetY = row * tile
                                    val sampleX = (point.x * mosaicSource.width + offsetX * mosaicSource.width / size.width)
                                        .toInt().coerceIn(0, mosaicSource.width - 1)
                                    val sampleY = (point.y * mosaicSource.height + offsetY * mosaicSource.height / size.height)
                                        .toInt().coerceIn(0, mosaicSource.height - 1)
                                    drawRect(
                                        Color(mosaicSource.getPixel(sampleX, sampleY)),
                                        topLeft = androidx.compose.ui.geometry.Offset(
                                            point.x * size.width + offsetX - tile / 2f,
                                            point.y * size.height + offsetY - tile / 2f
                                        ),
                                        size = androidx.compose.ui.geometry.Size(tile, tile)
                                    )
                                }
                            }
                        }
                        return@forEachIndexed
                    }
                    if (stroke.brush == EditorBrush.Spray) {
                        val radius = maxOf(4f, strokeWidth * 1.15f)
                        val dotCount = maxOf(8, (strokeWidth * 1.4f).toInt())
                        val sprayColor = Color(stroke.color).copy(alpha = editorBrushAlpha(EditorBrush.Spray))
                        stroke.points.forEachIndexed { pointIndex, point ->
                            repeat(dotCount) { dotIndex ->
                                val seed = pointIndex * 131 + dotIndex * 37
                                val angle = Math.toRadians((seed * 47 % 360).toDouble())
                                val distance = radius * sqrt(((seed * 17 % 1000) / 1000f).coerceIn(0f, 1f))
                                drawCircle(
                                    sprayColor,
                                    radius = 1.4f,
                                    center = androidx.compose.ui.geometry.Offset(
                                        point.x * size.width + cos(angle).toFloat() * distance,
                                        point.y * size.height + sin(angle).toFloat() * distance
                                    )
                                )
                            }
                        }
                        return@forEachIndexed
                    }
                    if (stroke.brush == EditorBrush.Neon) {
                        drawPath(path, Color(stroke.color).copy(alpha = .28f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth * 3.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    }
                    val cap = when (stroke.brush) {
                        EditorBrush.Marker, EditorBrush.Highlighter, EditorBrush.Fountain -> androidx.compose.ui.graphics.StrokeCap.Square
                        else -> androidx.compose.ui.graphics.StrokeCap.Round
                    }
                    drawPath(
                        path,
                        Color(stroke.color).copy(alpha = editorBrushAlpha(stroke.brush)),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = strokeWidth * editorBrushWidth(stroke.brush),
                            cap = cap,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round,
                            pathEffect = if (stroke.brush == EditorBrush.Dashed) {
                                androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                    floatArrayOf(strokeWidth * 2.4f, strokeWidth * 1.7f)
                                )
                            } else if (stroke.brush == EditorBrush.Crayon) {
                                androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                    floatArrayOf(strokeWidth * .32f, strokeWidth * .12f)
                                )
                            } else null
                        )
                    )
                }
                state.texts.forEachIndexed { index, overlay ->
                    // The editable text field renders the last overlay while
                    // the text panel is active. Do not paint the same text on
                    // the canvas or it will appear as a duplicated row.
                    if (!(textMoveEnabled && index == state.texts.lastIndex)) {
                        drawEditorTextOverlay(
                            drawContext.canvas.nativeCanvas,
                            overlay,
                            size.width,
                            size.height
                        )
                    }
                }
                if (composeEnabled) {
                    // The image is the base layer; the frame is always a
                    // visible overlay, scaled around its own center when a
                    // rotation requires extra safety margin.
                    val left = (.5f + (liveFrame.left - .5f) * safeFrameScale) * size.width
                    val top = (.5f + (liveFrame.top - .5f) * safeFrameScale) * size.height
                    val right = (.5f + (liveFrame.right - .5f) * safeFrameScale) * size.width
                    val bottom = (.5f + (liveFrame.bottom - .5f) * safeFrameScale) * size.height
                    drawEditorCropFrame(left, top, right, bottom, accent)
                }
            }
            if (colorPickEnabled) {
                colorPickPoint?.let { point ->
                    Box(
                        Modifier
                            .offset(
                                x = (point.x / density - 19.5f).dp,
                                y = (point.y / density - 47f).dp
                            )
                            .size(39.dp)
                            .zIndex(50f)
                            .shadow(5.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color(colorPickPreview))
                            .border(1.dp, Color.Black.copy(alpha = .5f), CircleShape)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                    Box(
                        Modifier
                            .offset(
                                x = (point.x / density - 5.5f).dp,
                                y = (point.y / density - 5.5f).dp
                            )
                            .size(11.dp)
                            .zIndex(51f)
                            .shadow(3.dp, CircleShape)
                            .border(1.dp, Color.Black.copy(alpha = .5f), CircleShape)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                }
            }
            if (textMoveEnabled && !colorPickEnabled) {
                liveText?.let { overlay ->
                    EditorInlineText(
                        overlay = overlay,
                        frameWidth = stageWidth,
                        frameHeight = stageHeight,
                        onLiveChange = { liveText = it; onTextLiveChanged(it) },
                        onBeginEdit = onTextEditStarted,
                        onCommit = onTextChanged
                    )
                }
            }
            if (composeEnabled || doodleEnabled || colorPickEnabled) {
                // Keep editor gestures above the rendered image. This is
                // also required for doodle strokes and color-pick taps.
                Box(Modifier.fillMaxSize().then(gesture))
            }
        }
    }
}

@Composable
private fun EditorInlineText(
    overlay: EditorText,
    frameWidth: androidx.compose.ui.unit.Dp,
    frameHeight: androidx.compose.ui.unit.Dp,
    onLiveChange: (EditorText) -> Unit,
    onBeginEdit: (EditorText) -> Unit,
    onCommit: (EditorText) -> Unit
) {
    val latestOverlay by rememberUpdatedState(overlay)
    val latestOnLive by rememberUpdatedState(onLiveChange)
    val latestOnBegin by rememberUpdatedState(onBeginEdit)
    val latestOnCommit by rememberUpdatedState(onCommit)
    val density = LocalDensity.current
    var textFocusStart by remember { mutableStateOf<EditorText?>(null) }
    val actualWidth = frameWidth * (if (overlay.vertical) overlay.boxHeight else overlay.boxWidth).coerceIn(.12f, .96f)
    val actualHeight = frameHeight * (if (overlay.vertical) overlay.boxWidth else overlay.boxHeight).coerceIn(.08f, .96f)
    val left = (frameWidth * overlay.x - actualWidth / 2f).coerceIn(0.dp, frameWidth - actualWidth)
    val top = (frameHeight * overlay.y - actualHeight / 2f).coerceIn(0.dp, frameHeight - actualHeight)
    Box(
        Modifier.offset(x = left, y = top).size(actualWidth, actualHeight)
            .pointerInput(frameWidth, frameHeight) {
                var handle = "move"
                var start = latestOverlay
                var working = start
                var totalX = 0f
                var totalY = 0f
                val frameWidthPx = frameWidth.toPx().coerceAtLeast(1f)
                val frameHeightPx = frameHeight.toPx().coerceAtLeast(1f)
                val edge = 34.dp.toPx()
                detectDragGestures(
                    onDragStart = { point ->
                        start = latestOverlay
                        working = start
                        latestOnBegin(start)
                        totalX = 0f
                        totalY = 0f
                        val horizontal = when {
                            point.x <= edge -> "l"
                            point.x >= size.width - edge -> "r"
                            else -> "m"
                        }
                        val vertical = when {
                            point.y <= edge -> "t"
                            point.y >= size.height - edge -> "b"
                            else -> "m"
                        }
                        handle = if (horizontal == "m" && vertical == "m") "move" else vertical + horizontal
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        totalX += drag.x
                        totalY += drag.y
                        if (handle == "move") {
                            val shownWidth = if (start.vertical) start.boxHeight else start.boxWidth
                            val shownHeight = if (start.vertical) start.boxWidth else start.boxHeight
                            working = start.copy(
                                x = (start.x + totalX / frameWidthPx).coerceIn(shownWidth / 2f, 1f - shownWidth / 2f),
                                y = (start.y + totalY / frameHeightPx).coerceIn(shownHeight / 2f, 1f - shownHeight / 2f)
                            )
                        } else {
                            val startWidth = (if (start.vertical) start.boxHeight else start.boxWidth) * frameWidthPx
                            val startHeight = (if (start.vertical) start.boxWidth else start.boxHeight) * frameHeightPx
                            val fromLeft = handle.endsWith("l")
                            val fromRight = handle.endsWith("r")
                            val fromTop = handle.startsWith("t")
                            val fromBottom = handle.startsWith("b")
                            val newWidth = (startWidth + when { fromLeft -> -totalX; fromRight -> totalX; else -> 0f })
                                .coerceIn(72.dp.toPx(), frameWidthPx - 16.dp.toPx())
                            val newHeight = (startHeight + when { fromTop -> -totalY; fromBottom -> totalY; else -> 0f })
                                .coerceIn(40.dp.toPx(), frameHeightPx - 16.dp.toPx())
                            val centerX = (start.x * frameWidthPx + when { fromLeft -> -(newWidth - startWidth) / 2f; fromRight -> (newWidth - startWidth) / 2f; else -> 0f })
                                .coerceIn(newWidth / 2f + 8.dp.toPx(), frameWidthPx - newWidth / 2f - 8.dp.toPx())
                            val centerY = (start.y * frameHeightPx + when { fromTop -> -(newHeight - startHeight) / 2f; fromBottom -> (newHeight - startHeight) / 2f; else -> 0f })
                                .coerceIn(newHeight / 2f + 8.dp.toPx(), frameHeightPx - newHeight / 2f - 8.dp.toPx())
                            val corner = (fromLeft || fromRight) && (fromTop || fromBottom)
                            val scale = if (corner) {
                                (start.boxScale * sqrt(newWidth / startWidth * newHeight / startHeight)).coerceIn(.35f, 3f)
                            } else start.boxScale
                            working = if (start.vertical) start.copy(
                                x = centerX / frameWidthPx,
                                y = centerY / frameHeightPx,
                                boxWidth = newHeight / frameHeightPx,
                                boxHeight = newWidth / frameWidthPx,
                                boxScale = scale
                            ) else start.copy(
                                x = centerX / frameWidthPx,
                                y = centerY / frameHeightPx,
                                boxWidth = newWidth / frameWidthPx,
                                boxHeight = newHeight / frameHeightPx,
                                boxScale = scale
                            )
                        }
                        latestOnLive(working)
                    },
                    onDragEnd = { latestOnCommit(working) },
                    onDragCancel = { latestOnLive(start) }
                )
            }
    ) {
        val accent = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxSize()) { drawEditorTextFrame(accent) }
        val font = editorComposeFont(overlay.font)
        val contentTextStyle = TextStyle(
            color = Color(overlay.color).copy(alpha = overlay.opacity),
            fontSize = (min(frameWidth.value, frameHeight.value) * overlay.size * overlay.boxScale).sp,
            fontFamily = font,
            fontWeight = if (overlay.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (overlay.italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
            letterSpacing = overlay.letterSpacing.sp,
            textAlign = when (overlay.align) {
                EditorTextAlign.Left -> TextAlign.Left
                EditorTextAlign.Center -> TextAlign.Center
                EditorTextAlign.Right -> TextAlign.Right
            },
            lineHeight = (min(frameWidth.value, frameHeight.value) * overlay.size * overlay.boxScale * overlay.lineSpacing).sp
        )
        val shadowStyle = if (overlay.shadowEnabled) {
            Shadow(
                color = Color(overlay.shadowColor).copy(alpha = overlay.shadowOpacity.coerceIn(0f, 1f)),
                offset = Offset(
                    overlay.shadowDistance.coerceIn(-.2f, .2f) * with(density) { frameWidth.toPx() },
                    overlay.shadowDistance.coerceIn(-.2f, .2f) * with(density) { frameHeight.toPx() }
                ),
                blurRadius = overlay.shadowBlur.coerceIn(0f, .2f) * min(
                    with(density) { frameWidth.toPx() },
                    with(density) { frameHeight.toPx() }
                )
            )
        } else null
        val renderedTextStyle = contentTextStyle.copy(shadow = shadowStyle)
        val outlineTextStyle = contentTextStyle.copy(
            color = Color(overlay.strokeColor).copy(alpha = overlay.strokeOpacity),
            drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                width = (contentTextStyle.fontSize.value * overlay.strokeWidth.coerceIn(.01f, .3f)).coerceAtLeast(1f)
            )
        )
        BasicTextField(
            value = overlay.text,
            onValueChange = { value ->
                val normalized = value.replace("\n\n\n", "\n\n").take(80)
                val updated = latestOverlay.copy(text = normalized)
                latestOnLive(updated)
            },
            modifier = Modifier.fillMaxSize()
                .onFocusChanged { focus ->
                    if (focus.isFocused) {
                        if (textFocusStart == null) {
                            textFocusStart = latestOverlay
                            latestOnBegin(latestOverlay)
                        }
                    } else {
                        val before = textFocusStart
                        textFocusStart = null
                        if (before != null && before != latestOverlay) latestOnCommit(latestOverlay)
                    }
                }
                .padding(5.dp),
            textStyle = renderedTextStyle,
            decorationBox = { field ->
                val backgroundModifier = if (overlay.background) {
                    Modifier
                        .background(
                            Color(overlay.backgroundColor).copy(alpha = overlay.backgroundOpacity.coerceIn(0f, 1f)),
                            RoundedCornerShape(with(density) { (overlay.backgroundRadius.coerceIn(0f, .2f) * min(
                                with(density) { frameWidth.toPx() },
                                with(density) { frameHeight.toPx() }
                            )).toDp() })
                        )
                        .padding(with(density) { (overlay.backgroundPadding.coerceIn(0f, .2f) * min(
                            with(density) { frameWidth.toPx() },
                            with(density) { frameHeight.toPx() }
                        )).toDp() })
                } else Modifier
                Box(backgroundModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (overlay.outline) {
                        Text(
                            if (overlay.text.isEmpty()) appText("请输入文本", LocalAppEnglish.current) else overlay.text,
                            style = outlineTextStyle,
                            textAlign = contentTextStyle.textAlign,
                            modifier = Modifier.fillMaxSize().padding(5.dp)
                        )
                    }
                    if (overlay.text.isEmpty()) {
                        Text(
                            appText("请输入文本", LocalAppEnglish.current),
                            color = contentTextStyle.color,
                            style = renderedTextStyle,
                            modifier = Modifier.fillMaxSize().padding(5.dp)
                        )
                    }
                    field()
                }
            }
        )
    }
}

@Composable
private fun EditorTabs(selected: EditorPanel, onSelect: (EditorPanel) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val english = LocalAppEnglish.current
    Row(
        Modifier.fillMaxWidth().height(68.dp).background(Color.White),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditorPanel.entries.forEach { panel ->
            val icon = when (panel) {
                EditorPanel.Compose -> EditorPrototypeIcons.RotateRight
                EditorPanel.Adjust -> EditorPrototypeIcons.EditPhoto
                EditorPanel.Doodle -> EditorPrototypeIcons.Pencil
                EditorPanel.Text -> EditorPrototypeIcons.Type
            }
            Column(
                Modifier.weight(1f).fillMaxSize().clickable(
                    interactionSource = remember(panel) { MutableInteractionSource() },
                    indication = null
                ) { onSelect(panel) }.padding(top = 5.dp, bottom = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, appText(panel.label, english), tint = if (panel == selected) accent else Color(0xFF777B79), modifier = Modifier.size(24.dp))
                Text(
                    appText(panel.label, english),
                    color = if (panel == selected) accent else Color(0xFF777B79),
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = true)
                    )
                )
            }
        }
    }
}

@Composable
private fun ComposeControls(
    state: ImageEditState,
    commit: (ImageEditState) -> Unit,
    applyLive: (ImageEditState) -> Unit,
    checkpoint: (ImageEditState) -> Unit,
    onCustomRatioOpenChange: (Boolean) -> Unit
) {
    val english = LocalAppEnglish.current
    var straightenStart by remember { mutableStateOf<ImageEditState?>(null) }
    val presets = listOf(
        CropPreset.Free, CropPreset.Device, CropPreset.Original, CropPreset.Custom,
        CropPreset.Square, CropPreset.ThreeTwo, CropPreset.TwoThree, CropPreset.FourThree,
        CropPreset.ThreeFour, CropPreset.SixteenNine, CropPreset.NineSixteen
    )
    var presetOrder by remember { mutableStateOf(presets) }
    var presetOrderDialogOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(78.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.offset(y = 5.dp)) {
                EditorRoundAction(EditorPrototypeIcons.RotateLeft, appText("左转", english)) { commit(state.copy(rotation = (state.rotation + 270) % 360)) }
            }
            Box(Modifier.offset(y = 5.dp)) {
                EditorRoundAction(EditorPrototypeIcons.RotateRight, appText("右转", english)) { commit(state.copy(rotation = (state.rotation + 90) % 360)) }
            }
            Box(Modifier.weight(1f).height(64.dp)) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clickable { commit(state.copy(straighten = 0f)) }
                ) {
                    Text(
                        state.straighten.roundToInt().toString(),
                        color = EditorInk,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .offset(x = 13.dp, y = (-5).dp)
                            .size(6.dp)
                            .border(1.dp, EditorInk, CircleShape)
                    )
                    if (state.straighten != 0f) {
                        Icon(
                            EditorPrototypeIcons.Repeat,
                            appText("点击恢复", english),
                            tint = EditorAccent,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(x = 31.dp)
                                .size(14.dp)
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(38.dp)) {
                    EditorRuler(
                    value = state.straighten,
                    valueRange = -45f..45f,
                    onValueChange = { applyLive(state.copy(straighten = it)) },
                    onValueChangeStarted = { straightenStart = state },
                    onValueChangeFinished = {
                        straightenStart?.let(checkpoint)
                        straightenStart = null
                    },
                    // Keep a small safety gap from the rotate/flip buttons while
                    // using the remaining width for a more comfortable ruler.
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .padding(horizontal = 4.dp)
                        .offset(y = (-3).dp),
                    tickSpacing = 5.dp
                    )
                }
                }
                /*
                        Icon(
                            EditorPrototypeIcons.Repeat,
                            appText("点击复原", english),
                            tint = EditorAccent,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(14.dp)
                        )
                */
            }
            Box(Modifier.offset(y = 5.dp)) {
                EditorRoundAction(EditorPrototypeIcons.FlipHorizontal, appText("水平翻转", english)) { commit(state.copy(flipHorizontal = !state.flipHorizontal)) }
            }
            Box(Modifier.offset(y = 5.dp)) {
                EditorRoundAction(EditorPrototypeIcons.FlipVertical, appText("垂直翻转", english)) { commit(state.copy(flipVertical = !state.flipVertical)) }
            }
        }
        fun activateCustomRatio() {
            onCustomRatioOpenChange(true)
            if (state.crop != CropPreset.Custom) {
                commit(state.copy(
                    crop = CropPreset.Custom,
                    customCropRatio = state.customCropRatio ?: (16f / 9f),
                    cropRect = NormalizedRect(),
                    composeScale = 1f,
                    composeX = 0f,
                    composeY = 0f
                ))
            }
        }
        val selectedIndex = presetOrder.indexOf(state.crop).coerceAtLeast(0)
        EditorCenterCarousel(
            itemCount = presetOrder.size + 1,
            selectedIndex = selectedIndex,
            centerLastItem = false,
            onCentered = { index ->
                if (index >= presetOrder.size) return@EditorCenterCarousel
                val preset = presetOrder[index]
                if (preset == CropPreset.Custom) {
                    activateCustomRatio()
                } else {
                    onCustomRatioOpenChange(false)
                }
                if (preset != state.crop && preset != CropPreset.Custom) {
                    commit(state.copy(
                        crop = preset,
                        cropRect = NormalizedRect(),
                        composeScale = 1f,
                        composeX = 0f,
                        composeY = 0f
                    ))
                }
            },
            outlineWidth = 2.dp,
            itemWidth = 94.dp,
            itemHeight = 98.dp,
            itemSpacing = 10.dp,
            modifier = Modifier.fillMaxWidth().height(106.dp).padding(vertical = 4.dp)
        ) { index, centerOnClick ->
            if (index == presetOrder.size) {
                EditorToolSettingsCard(selected = false) { presetOrderDialogOpen = true }
            } else {
                val preset = presetOrder[index]
                CropPresetCard(
                    preset = preset,
                    selected = preset == state.crop,
                    accent = EditorAccent,
                    width = 94.dp,
                    height = 98.dp
                ) {
                    centerOnClick()
                    if (preset == CropPreset.Custom) {
                        activateCustomRatio()
                    } else {
                        onCustomRatioOpenChange(false)
                        commit(state.copy(
                            crop = preset,
                            cropRect = NormalizedRect(),
                            composeScale = 1f,
                            composeX = 0f,
                            composeY = 0f
                        ))
                    }
                }
            }
        }
        }
        if (presetOrderDialogOpen) {
            EditorToolOrderDialog(
                title = appText("设置构图比例顺序", english),
                items = presetOrder,
                label = { appText(it.label, english) },
                onDismiss = { presetOrderDialogOpen = false },
                onConfirm = { presetOrder = it; presetOrderDialogOpen = false }
            )
        }
        /*
            title = appText("自定义裁剪比例", english),
            width = customWidth,
            height = customHeight,
            onWidthChange = { customWidth = it.filter(Char::isDigit).take(4) },
            onHeightChange = { customHeight = it.filter(Char::isDigit).take(4) },
            onDismiss = { customRatioOpen = false },
            onConfirm = {
                val width = customWidth.toFloatOrNull()
                val height = customHeight.toFloatOrNull()
                if (width != null && height != null && width > 0f && height > 0f) {
                    commit(state.copy(crop = CropPreset.Custom, customCropRatio = width / height, cropRect = NormalizedRect()))
                    customRatioOpen = false
                }
            }
        )
    }
    */
}

}

/*
@Composable
private fun EditorRatioPopover(
    title: String,
    width: String,
    height: String,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val english = LocalAppEnglish.current
    val valid = (width.toFloatOrNull() ?: 0f) > 0f && (height.toFloatOrNull() ?: 0f) > 0f
    Box(Modifier.fillMaxSize().zIndex(20f)) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(21f)
                .offset(y = (-172).dp)
                .widthIn(max = 330.dp)
                .padding(horizontal = 10.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            color = Color.White,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 18.dp
        ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, modifier = Modifier.weight(1f), color = EditorInk, fontWeight = FontWeight.SemiBold)
                Text(
                    appText("关闭", english),
                    color = EditorMuted,
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorRatioField(width, appText("瀹?, english), onWidthChange, Modifier.weight(1f))
                Text(":", color = EditorMuted)
                EditorRatioField(height, appText("楂?, english), onHeightChange, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                Text(
                    appText("搴旂敤", english),
                    color = if (valid) EditorAccent else EditorMuted,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = valid, onClick = onConfirm).padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        }
    }
}

@Composable
private fun EditorRatioField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = EditorInk, fontSize = 16.sp, textAlign = TextAlign.Center),
        modifier = modifier
            .height(42.dp)
            .background(Color(0xFFF4F4F2), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFD8DAD7), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 9.dp),
        decorationBox = { field ->
            Box(contentAlignment = Alignment.Center) {
                if (value.isEmpty()) Text(label, color = EditorMuted, fontSize = 13.sp)
                field()
            }
        }
    )
}

*/
@Composable
private fun EditorRatioPopoverNew(
    width: String,
    height: String,
    confirmed: Boolean,
    verticalOffset: androidx.compose.ui.unit.Dp = (-116).dp,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val english = LocalAppEnglish.current
    val valid = (width.toFloatOrNull() ?: 0f) > 0f && (height.toFloatOrNull() ?: 0f) > 0f
    // A compact in-layout bar keeps the ratio editor attached to the
    // function area instead of presenting a modal dialog.
    Box(
        Modifier.fillMaxSize().zIndex(50f)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = verticalOffset)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            color = Color.White,
            shape = RoundedCornerShape(0.dp),
            shadowElevation = 0.dp
        ) {
            /*
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(if (english) "Ratio" else "\u6bd4\u4f8b", color = EditorMuted, style = MaterialTheme.typography.labelSmall)
                BasicTextField(
                    value = width,
                    onValueChange = onWidthChange,
                    singleLine = true,
                    textStyle = TextStyle(color = EditorInk, fontSize = 15.sp, textAlign = TextAlign.Center),
                    modifier = Modifier.width(82.dp).fillMaxHeight().background(Color(0xFFF4F4F2), RoundedCornerShape(5.dp)).border(1.dp, Color(0xFFD8DAD7), RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 7.dp)
                )
                Text(":", color = EditorMuted)
                BasicTextField(
                    value = height,
                    onValueChange = onHeightChange,
                    singleLine = true,
                    textStyle = TextStyle(color = EditorInk, fontSize = 15.sp, textAlign = TextAlign.Center),
                    modifier = Modifier.width(82.dp).fillMaxHeight().background(Color(0xFFF4F4F2), RoundedCornerShape(5.dp)).border(1.dp, Color(0xFFD8DAD7), RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 7.dp)
                )
                Text(
                    "✓",
                    color = if (valid) EditorAccent else EditorMuted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = valid, onClick = onConfirm)
                )
            }
            */
            Box(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    EditorPrototypeIcons.Close,
                    if (english) "Close" else "\u5173\u95ed",
                    tint = EditorMuted,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(20.dp)
                        .clickable(onClick = onDismiss)
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = width,
                            onValueChange = onWidthChange,
                            singleLine = true,
                            textStyle = TextStyle(color = EditorInk, fontSize = 15.sp, textAlign = TextAlign.Center),
                            modifier = Modifier
                                .width(82.dp)
                                .height(36.dp)
                                .background(Color(0xFFF4F4F2), RoundedCornerShape(5.dp))
                                .border(1.dp, Color(0xFFD8DAD7), RoundedCornerShape(5.dp))
                                .padding(horizontal = 8.dp, vertical = 7.dp)
                        )
                        Text(":", color = EditorMuted, modifier = Modifier.width(12.dp), textAlign = TextAlign.Center)
                        BasicTextField(
                            value = height,
                            onValueChange = onHeightChange,
                            singleLine = true,
                            textStyle = TextStyle(color = EditorInk, fontSize = 15.sp, textAlign = TextAlign.Center),
                            modifier = Modifier
                                .width(82.dp)
                                .height(36.dp)
                                .background(Color(0xFFF4F4F2), RoundedCornerShape(5.dp))
                                .border(1.dp, Color(0xFFD8DAD7), RoundedCornerShape(5.dp))
                                .padding(horizontal = 8.dp, vertical = 7.dp)
                        )
                    }
                }
                Text(
                    if (confirmed) "\u2713" else if (english) "Confirm" else "\u786e\u8ba4",
                    color = if (valid || confirmed) EditorAccent else EditorMuted,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(48.dp)
                        .clickable(enabled = valid && !confirmed, onClick = onConfirm),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EditorRoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    verticalFlip: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(34.dp).clickable(onClick = onClick),
        color = Color(0xFFF0F0EE),
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                label,
                tint = Color(0xFF232727),
                modifier = Modifier.size(18.dp).graphicsLayer { if (verticalFlip) rotationZ = 90f }
            )
        }
    }
}

@Composable
private fun EditorToolSettingsCard(selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(94.dp).height(98.dp).padding(3.dp).clickable(onClick = onClick),
        color = if (selected) Color(0xFFF7FFF9) else EditorTile,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Settings, appText("设置工具顺序", LocalAppEnglish.current), tint = if (selected) EditorAccent else Color(0xFF777B79), modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(5.dp))
            Text(appText("设置", LocalAppEnglish.current), color = if (selected) EditorAccent else Color(0xFF353937), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun <T> EditorToolOrderDialog(
    title: String,
    items: List<T>,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onConfirm: (List<T>) -> Unit
) {
    var clicked by remember(items) { mutableStateOf<List<T>>(emptyList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
        title = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(EditorPrototypeIcons.Close, appText("取消", LocalAppEnglish.current))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { item ->
                    val number = clicked.indexOf(item).takeIf { it >= 0 }?.plus(1)
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            clicked = if (item in clicked) clicked - item else clicked + item
                        },
                        color = if (number != null) Color(0xFFE9F8EE) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label(item), modifier = Modifier.weight(1f))
                            Text(number?.toString().orEmpty(), color = EditorAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(clicked + items.filterNot { it in clicked }) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(appText("确认", LocalAppEnglish.current), color = Color.White)
            }
        }
    )
}

@Composable
private fun CropPresetCard(
    preset: CropPreset,
    selected: Boolean,
    accent: Color,
    width: androidx.compose.ui.unit.Dp = 68.dp,
    height: androidx.compose.ui.unit.Dp = 74.dp,
    onClick: () -> Unit
) {
    val english = LocalAppEnglish.current
    val ratio = when (preset) {
        CropPreset.Original -> 1.5f
        CropPreset.Free, CropPreset.Custom -> 4f / 3f
        else -> preset.ratio ?: 4f / 3f
    }
    val iconWidth = if (ratio >= 1f) 28.dp else 28.dp * ratio
    val iconHeight = if (ratio >= 1f) 28.dp / ratio else 28.dp
    Surface(
        modifier = Modifier.width(width).height(height).padding(3.dp).clickable(onClick = onClick),
        color = if (selected) Color(0xFFF7FFF9) else EditorTile,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.width(iconWidth).height(iconHeight)
                    .border(1.5.dp, if (selected) EditorAccent else Color(0xFF777B79), RoundedCornerShape(1.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (preset == CropPreset.Custom) Text("+", color = if (selected) EditorAccent else Color(0xFF777B79))
            }
            Spacer(Modifier.height(5.dp))
            Text(
                appText(preset.label, english),
                color = if (selected) EditorAccent else Color(0xFF353937),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AdjustControls(
    state: ImageEditState,
    applyLive: (ImageEditState) -> Unit,
    checkpoint: (ImageEditState) -> Unit
) {
    val english = LocalAppEnglish.current
    val tools = listOf("曝光", "亮度", "对比度", "色调", "色温", "高光", "阴影", "饱和度", "自然饱和度", "褪色", "锐度", "增强")
    var toolOrder by remember { mutableStateOf(tools) }
    var toolOrderDialogOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(tools.first()) }
    var dragStart by remember { mutableStateOf<ImageEditState?>(null) }
    val latestState by rememberUpdatedState(state)
    val latestSelected by rememberUpdatedState(selected)
    val value = when (selected) {
        "亮度" -> state.brightness
        "对比度" -> state.contrast.coerceIn(0f, 2f)
        "色调" -> state.tint
        "色温" -> state.temperature
        "高光" -> state.highlights
        "阴影" -> state.shadows
        "饱和度" -> state.saturation.coerceIn(0f, 2f)
        "自然饱和度" -> state.vibrance
        "褪色" -> state.fade
        "锐度" -> state.sharpness
        "增强" -> state.enhance
        else -> state.exposure
    }
    fun neutralFor(tool: String): Float = if (tool == "对比度" || tool == "饱和度") 1f else 0f
    val neutral = neutralFor(selected)
    val displayValue = (value - neutral) * 100f
    val displayRange = if (selected == "褪色" || selected == "锐度" || selected == "增强") 0f..100f else -100f..100f
    fun update(display: Float) {
        val activeTool = latestSelected
        val activeNeutral = neutralFor(activeTool)
        val next = (activeNeutral + display / 100f).let {
            if (activeTool == "对比度" || activeTool == "饱和度") it.coerceIn(0f, 2f) else it
        }
        val current = latestState
        applyLive(when (activeTool) {
            "亮度" -> current.copy(brightness = next)
            "对比度" -> current.copy(contrast = next)
            "色调" -> current.copy(tint = next)
            "色温" -> current.copy(temperature = next)
            "高光" -> current.copy(highlights = next)
            "阴影" -> current.copy(shadows = next)
            "饱和度" -> current.copy(saturation = next)
            "自然饱和度" -> current.copy(vibrance = next)
            "褪色" -> current.copy(fade = next)
            "锐度" -> current.copy(sharpness = next)
            "增强" -> current.copy(enhance = next)
            else -> current.copy(exposure = next)
        })
    }
    fun resetSelected() {
        val before = latestState
        val reset = when (latestSelected) {
            "亮度" -> before.copy(brightness = 0f)
            "对比度" -> before.copy(contrast = 1f)
            "色调" -> before.copy(tint = 0f)
            "色温" -> before.copy(temperature = 0f)
            "高光" -> before.copy(highlights = 0f)
            "阴影" -> before.copy(shadows = 0f)
            "饱和度" -> before.copy(saturation = 1f)
            "自然饱和度" -> before.copy(vibrance = 0f)
            "褪色" -> before.copy(fade = 0f)
            "锐度" -> before.copy(sharpness = 0f)
            "增强" -> before.copy(enhance = 0f)
            else -> before.copy(exposure = 0f)
        }
        applyLive(reset)
        checkpoint(before)
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.fillMaxWidth().height(78.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().height(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().height(28.dp).clickable { resetSelected() },
                contentAlignment = Alignment.Center
            ) {
                Text(displayValue.roundToInt().toString(), color = EditorInk, style = MaterialTheme.typography.bodyMedium)
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .offset(x = 13.dp, y = (-5).dp)
                        .size(6.dp)
                        .border(1.dp, EditorInk, CircleShape)
                )
                if (abs(displayValue) > .01f) {
                    Icon(
                        EditorPrototypeIcons.Repeat,
                        appText("点击复原", english),
                        tint = EditorAccent,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = 31.dp)
                            .size(14.dp)
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(38.dp)) {
                EditorRuler(
                    value = displayValue,
                    valueRange = displayRange,
                    onValueChange = ::update,
                    onValueChangeStarted = { dragStart = latestState },
                    onValueChangeFinished = {
                        dragStart?.let(checkpoint)
                        dragStart = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .padding(horizontal = 4.dp)
                        .align(Alignment.Center)
                        .offset(y = (-3).dp),
                    majorEvery = 5,
                    tickStep = 2f,
                    tickSpacing = 5.dp,
                    edgeInset = 50.dp
                )
                Text(
                    displayRange.start.roundToInt().toString(),
                    color = EditorInk,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 16.sp, lineHeight = 20.sp),
                    modifier = Modifier.align(Alignment.CenterStart).offset(y = (-3).dp).padding(start = 12.dp)
                )
                Text(
                    displayRange.endInclusive.roundToInt().toString(),
                    color = EditorInk,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 16.sp, lineHeight = 20.sp),
                    modifier = Modifier.align(Alignment.CenterEnd).offset(y = (-3).dp).padding(end = 12.dp)
                )
            }
            }
        }
        EditorCenterCarousel(
            itemCount = toolOrder.size + 1,
            selectedIndex = toolOrder.indexOf(selected).coerceAtLeast(0),
            centerLastItem = false,
            onCentered = {
                if (it < toolOrder.size) selected = toolOrder[it]
            },
            outlineWidth = 2.dp,
            itemWidth = 94.dp,
            itemHeight = 98.dp,
            itemSpacing = 10.dp,
            modifier = Modifier.fillMaxWidth().height(106.dp).padding(vertical = 4.dp)
        ) { index, centerOnClick ->
            if (index == toolOrder.size) {
                EditorToolSettingsCard(selected = false) { toolOrderDialogOpen = true }
            } else {
            val tool = toolOrder[index]
            val isSelected = tool == selected
            Surface(
                modifier = Modifier.width(94.dp).height(98.dp).padding(3.dp).clickable {
                                centerOnClick()
                                if (isSelected) resetSelected() else selected = tool
                },
                color = if (isSelected) Color(0xFFF7FFF9) else EditorTile,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        appText(tool, english),
                        color = if (isSelected) EditorAccent else Color(0xFF353937),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 2
                    )
                }
            }
            }
        }
    }
    if (toolOrderDialogOpen) {
        EditorToolOrderDialog(
            title = appText("设置调节选项顺序", english),
            items = toolOrder,
            label = { appText(it, english) },
            onDismiss = { toolOrderDialogOpen = false },
            onConfirm = { toolOrder = it; toolOrderDialogOpen = false }
        )
    }
}

@Composable
private fun DoodleControls(
    color: Int,
    width: Float,
    brush: EditorBrush,
    colorPicking: Boolean,
    hasPickedColor: Boolean,
    onColor: (Int) -> Unit,
    onWidth: (Float) -> Unit,
    onBrush: (EditorBrush) -> Unit,
    onPickColor: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val english = LocalAppEnglish.current
    val minBrushWidth = 1f
    val maxBrushWidth = 100f
    val brushWidthFraction = ((width - minBrushWidth) / (maxBrushWidth - minBrushWidth)).coerceIn(0f, 1f)
    var customColorOpen by remember { mutableStateOf(false) }
    val colorEnabled = brush != EditorBrush.Mosaic && brush != EditorBrush.Eraser
    LaunchedEffect(brush) {
        if (!colorEnabled) customColorOpen = false
    }
    val colors = listOf(
        android.graphics.Color.BLACK, android.graphics.Color.WHITE, 0xFFFF3B30.toInt(),
        0xFFFF9500.toInt(), android.graphics.Color.YELLOW, 0xFF34C759.toInt(), 0xFF007AFF.toInt(), 0xFFAF52DE.toInt()
    )
    val defaultBrushes: List<EditorBrush> = remember {
        EditorBrush.entries.filter { it != EditorBrush.Eraser && it != EditorBrush.Fountain && it != EditorBrush.Mosaic }
            .toMutableList().also { available ->
                val replacementIndex = EditorBrush.entries.indexOf(EditorBrush.Fountain).coerceIn(0, available.size)
                available.add(replacementIndex, EditorBrush.Mosaic)
            }.toList()
    }
    var brushOrder by remember { mutableStateOf(defaultBrushes) }
    var brushOrderDialogOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color.White).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Row(
            Modifier.fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(38.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0x22000000), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(2.dp + 22.dp * brushWidthFraction)
                            .clip(CircleShape)
                            .background(
                                when (brush) {
                                    EditorBrush.Mosaic -> Color.Black
                                    EditorBrush.Eraser -> Color(0xFF777B79)
                                    else -> Color(color)
                                }
                            )
                    )
                }
            }
            EditorThinSlider(width, onWidth, valueRange = minBrushWidth..maxBrushWidth, modifier = Modifier.weight(1f).height(28.dp))
            val brushSize = (1f + brushWidthFraction * 99f).roundToInt().coerceIn(1, 100)
            Text(
                brushSize.toString(),
                color = Color(0xFF555957),
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            EditorEyedropperButton(
                picking = colorPicking,
                enabled = colorEnabled,
                pickedColor = if (colorEnabled && hasPickedColor) color else null,
                onClick = onPickColor
            )
            colors.forEach { swatch ->
                EditorColorSwatch(swatch, colorEnabled && color == swatch, enabled = colorEnabled) { onColor(swatch) }
            }
            Box(
                Modifier.size(24.dp).graphicsLayer { alpha = if (colorEnabled) 1f else .3f }.clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                    .border(1.dp, Color(0x22000000), CircleShape)
                    .clickable(enabled = colorEnabled) { customColorOpen = true }
            ) { if (colorEnabled && !hasPickedColor && color !in colors) EditorSwatchCheck(Color.White) }
        }
        Box(Modifier.fillMaxWidth().height(106.dp).padding(vertical = 4.dp)) {
            EditorCenterCarousel(
                itemCount = brushOrder.size + 1,
                selectedIndex = brushOrder.indexOf(brush).coerceAtLeast(0),
                centerLastItem = false,
                onCentered = {
                    if (it < brushOrder.size && brush != EditorBrush.Eraser) onBrush(brushOrder[it])
                },
                modifier = Modifier.fillMaxSize(),
                itemWidth = 94.dp,
                itemHeight = 98.dp,
                itemSpacing = 10.dp,
                outlineWidth = 2.dp,
                showCenterOutline = brush != EditorBrush.Eraser
            ) { index, centerOnClick ->
                    if (index == brushOrder.size) {
                        EditorToolSettingsCard(selected = false) { brushOrderDialogOpen = true }
                    } else {
                    val option = brushOrder[index]
                    Surface(
                        modifier = Modifier.width(94.dp).height(98.dp).padding(3.dp)
                            .clickable {
                                centerOnClick()
                                onBrush(option)
                            },
                        color = if (option == brush) Color(0xFFF7FFF9) else EditorTile,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            EditorBrushMark(option, if (option == brush) accent else Color(0xFF777B79))
                            Spacer(Modifier.height(5.dp))
                            Text(appText(option.label, english), color = if (option == brush) accent else Color(0xFF353937), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                    }
            }
            // Opaque backing for the fixed eraser card, above the moving
            // brush layer and below the eraser itself.
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(100.dp)
                    .background(Color.White)
            )
            // Keep the fixed eraser separated from the moving brush cards.
            // The right edge fades outward across the same 10dp spacing as
            // the gray tool cards; the left edge stays fully opaque.
            Canvas(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-10).dp)
                    .size(114.dp, 98.dp)
            ) {
                val fade = 10.dp.toPx()
                drawRect(
                    color = Color.White,
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(fade, size.height)
                )
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = .55f), Color.White),
                        startX = size.width,
                        endX = size.width - fade
                    ),
                    topLeft = Offset(size.width - fade, 0f),
                    size = androidx.compose.ui.geometry.Size(fade, size.height)
                )
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(94.dp)
                    .height(98.dp)
                    .zIndex(10f)
                    .clickable {
                        onBrush(EditorBrush.Eraser)
                    }
                    .border(
                        2.dp,
                        if (brush == EditorBrush.Eraser) accent else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(3.dp),
                color = if (brush == EditorBrush.Eraser) Color(0xFFF7FFF9) else EditorTile,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    EditorBrushMark(EditorBrush.Eraser, if (brush == EditorBrush.Eraser) accent else Color(0xFF777B79))
                    Spacer(Modifier.height(5.dp))
                    Text(appText("橡皮", english), color = if (brush == EditorBrush.Eraser) accent else Color(0xFF353937), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
    if (brushOrderDialogOpen) {
        EditorToolOrderDialog(
            title = appText("设置笔型顺序", english),
            items = brushOrder,
            label = { appText(it.label, english) },
            onDismiss = { brushOrderDialogOpen = false },
            onConfirm = { brushOrder = it; brushOrderDialogOpen = false }
        )
    }
    if (customColorOpen) {
        EditorColorPickerDialog(
            title = appText("自选画笔颜色", english),
            initialColor = color,
            onDismiss = { customColorOpen = false },
            onConfirm = { selectedColor ->
                onColor(selectedColor)
                customColorOpen = false
            }
        )
    }
}

@Composable
private fun EditorBrushMark(brush: EditorBrush, color: Color) {
    if (brush == EditorBrush.Eraser) {
        Box(
            Modifier
                .size(34.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                EditorPrototypeIcons.Eraser,
                appText("橡皮", LocalAppEnglish.current),
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }
        return
    }
    val accent = EditorAccent
    Canvas(Modifier.size(28.dp, 28.dp)) {
        val centerY = size.height / 2f
        when (brush) {
            EditorBrush.Spray -> repeat(12) { index ->
                val column = index % 4
                val row = index / 4
                drawCircle(color.copy(alpha = .72f), 1.2.dp.toPx(), androidx.compose.ui.geometry.Offset(5.dp.toPx() + column * 6.dp.toPx(), 6.dp.toPx() + row * 6.dp.toPx()))
            }
            EditorBrush.Mosaic -> repeat(3) { row -> repeat(3) { column ->
                if ((row + column) % 2 == 0) drawRect(color.copy(alpha = .72f), androidx.compose.ui.geometry.Offset(column * 8.dp.toPx() + 2.dp.toPx(), row * 8.dp.toPx() + 2.dp.toPx()), androidx.compose.ui.geometry.Size(7.dp.toPx(), 7.dp.toPx()))
            } }
            EditorBrush.Dashed -> repeat(3) { index ->
                val startX = (3 + index * 9).dp.toPx()
                drawLine(
                    color,
                    androidx.compose.ui.geometry.Offset(startX, centerY + 3.dp.toPx() - index * 3.dp.toPx()),
                    androidx.compose.ui.geometry.Offset(startX + 5.dp.toPx(), centerY + 1.dp.toPx() - index * 3.dp.toPx()),
                    3.dp.toPx(),
                    androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            else -> {
                val thickness = when (brush) {
                    EditorBrush.Pen -> 2.dp
                    EditorBrush.Fountain -> 3.dp
                    EditorBrush.Brush -> 7.dp
                    EditorBrush.Marker -> 6.dp
                    EditorBrush.Highlighter -> 9.dp
                    EditorBrush.Pencil -> 1.dp
                    EditorBrush.Crayon -> 5.dp
                    EditorBrush.Neon -> 3.dp
                    else -> 3.dp
                }.toPx()
                if (brush == EditorBrush.Neon) {
                    drawLine(accent.copy(alpha = .25f), androidx.compose.ui.geometry.Offset(3.dp.toPx(), centerY + 3.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width - 3.dp.toPx(), centerY - 3.dp.toPx()), thickness * 3f, androidx.compose.ui.graphics.StrokeCap.Round)
                }
                drawLine(
                    if (brush == EditorBrush.Neon) accent else color.copy(alpha = if (brush == EditorBrush.Highlighter) .38f else 1f),
                    androidx.compose.ui.geometry.Offset(3.dp.toPx(), centerY + 3.dp.toPx()),
                    androidx.compose.ui.geometry.Offset(size.width - 3.dp.toPx(), centerY - 3.dp.toPx()),
                    thickness,
                    androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun TextControls(
    state: ImageEditState,
    subtab: Int,
    editing: Boolean,
    onSubtab: (Int) -> Unit,
    colorPickingStroke: Boolean?,
    onUpdate: (EditorText) -> Unit,
    onLiveUpdate: (EditorText) -> Unit,
    onCheckpoint: (ImageEditState) -> Unit,
    onPickColor: (Boolean) -> Unit
) {
    val selected = if (editing) state.texts.lastOrNull() else null
    val accent = Color(0xFF00E673)
    val english = LocalAppEnglish.current
    var customColorTarget by remember { mutableStateOf<Boolean?>(null) }
    var rangeStart by remember { mutableStateOf<ImageEditState?>(null) }
    fun beginRange() { rangeStart = state }
    fun finishRange() {
        rangeStart?.let(onCheckpoint)
        rangeStart = null
    }
    val swatches = listOf(
        android.graphics.Color.BLACK, android.graphics.Color.WHITE, 0xFFFF3B30.toInt(),
        0xFFFF9500.toInt(), android.graphics.Color.YELLOW, 0xFF34C759.toInt(), 0xFF007AFF.toInt(), 0xFFAF52DE.toInt()
    )
    val fonts = remember { EditorFont.entries.toList() }
    var fontOrder by remember { mutableStateOf(fonts) }
    var fontOrderDialogOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 4.dp)) {
            when (subtab) {
                0 -> {
                    EditorColorStrip(
                        colors = swatches,
                        selected = selected?.color,
                        enabled = selected != null,
                        picking = colorPickingStroke == false,
                        onPickColor = { onPickColor(false) },
                        onCustom = { customColorTarget = false }
                    ) { color -> selected?.let { onUpdate(it.copy(color = color)) } }
                    EditorVisualRange(
                        value = selected?.size ?: .089f,
                        valueRange = .044f..0.2f,
                        enabled = selected != null,
                        displayValue = { (it * 360f).roundToInt().toString() },
                        preview = {
                            val currentSize = (selected?.size ?: .089f) * 360f
                            // Re-map the original 16..60px visual range onto
                            // the current 28..72px controls. Values below 28
                            // continue linearly to a smaller glyph.
                            val remappedProgress = (currentSize - 28f) / 44f
                            val originalMinScale = .48f
                            val originalSize60Scale = originalMinScale +
                                sqrt(((60f - 16f) / 56f).coerceIn(0f, 1f)) * .52f
                            val extrapolatedMinScale = originalMinScale -
                                (12f / 44f) * (originalSize60Scale - originalMinScale)
                            val glyphScale = (originalMinScale + remappedProgress *
                                (originalSize60Scale - originalMinScale))
                                .coerceIn(extrapolatedMinScale, originalSize60Scale)
                            val previewSize = 20f * glyphScale
                            Surface(color = EditorTile, shape = CircleShape, modifier = Modifier.size(24.dp).clip(CircleShape)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "字",
                                        color = EditorInk,
                                        fontFamily = selected?.font?.let(::editorComposeFont),
                                        fontSize = previewSize.sp,
                                        lineHeight = previewSize.sp,
                                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                    )
                                }
                            }
                        },
                        onValueChangeStarted = ::beginRange,
                        onValueChangeFinished = ::finishRange
                    ) { selected?.let { text -> onLiveUpdate(text.copy(size = it)) } }
                    EditorVisualRange(
                        value = selected?.opacity ?: 1f,
                        valueRange = 0f..1f,
                        enabled = selected != null,
                        displayValue = { (it * 100f).roundToInt().toString() },
                        preview = {
                            Box(
                                Modifier.size(24.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = selected?.opacity ?: 1f))
                                    .border(1.dp, Color(0x22000000), CircleShape)
                            )
                        },
                        onValueChangeStarted = ::beginRange,
                        onValueChangeFinished = ::finishRange
                    ) { selected?.let { text -> onLiveUpdate(text.copy(opacity = it)) } }
                    EditorCenterCarousel(
                        itemCount = fontOrder.size + 1,
                        selectedIndex = fontOrder.indexOf(selected?.font).coerceAtLeast(0),
                        centerLastItem = false,
                        onCentered = { index ->
                            if (index < fontOrder.size) selected?.let { onUpdate(it.copy(font = fontOrder[index])) }
                        },
                        modifier = Modifier.fillMaxWidth().height(106.dp).padding(vertical = 4.dp),
                        itemWidth = 94.dp,
                        itemHeight = 98.dp,
                        itemSpacing = 10.dp,
                        outlineWidth = 2.dp
                    ) { index, centerOnClick ->
                        if (index == fontOrder.size) {
                            EditorToolSettingsCard(selected = false) { fontOrderDialogOpen = true }
                        } else {
                        val font = fontOrder[index]
                        Surface(
                                modifier = Modifier.width(94.dp).height(98.dp).clickable(enabled = selected != null) {
                                    centerOnClick()
                                    selected?.let { onUpdate(it.copy(font = font)) }
                                },
                                color = if (selected?.font == font) Color(0xFFF7FFF9) else EditorTile,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        appText(editorFontDisplayName(font), english),
                                        color = if (selected?.font == font) EditorInk else EditorMuted,
                                        fontFamily = editorComposeFont(font),
                                        fontSize = 16.sp,
                                        lineHeight = 20.sp,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                             }
                         }
                    }
                }
                1 -> {
                    EditorColorStrip(
                        colors = swatches,
                        selected = selected?.strokeColor,
                        enabled = selected?.outline == true,
                        picking = colorPickingStroke == true,
                        toggleState = selected?.outline,
                        onToggle = { selected?.let { onUpdate(it.copy(outline = !it.outline)) } },
                        onPickColor = { onPickColor(true) },
                        onCustom = { customColorTarget = true }
                    ) { color -> selected?.let { onUpdate(it.copy(strokeColor = color)) } }
                    EditorVisualRange(
                        value = selected?.strokeWidth ?: .05f,
                        valueRange = 0f..0.3f,
                        enabled = selected?.outline == true,
                        displayValue = { ((it / .3f) * 12f).let { value -> "%.1f".format(value) }.removeSuffix(".0") },
                        preview = {
                            Box(
                                Modifier.size(24.dp).clip(CircleShape).background(Color.White)
                                    .border(1.dp, Color(0x22000000), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier.size((4.dp + 12.dp * ((selected?.strokeWidth ?: .05f) / .3f)).coerceIn(4.dp, 16.dp))
                                        .clip(CircleShape).background(Color.Black)
                                )
                            }
                        },
                        onValueChangeStarted = ::beginRange,
                        onValueChangeFinished = ::finishRange
                    ) { selected?.let { text -> onLiveUpdate(text.copy(strokeWidth = it)) } }
                    EditorVisualRange(
                        value = selected?.strokeOpacity ?: 1f,
                        valueRange = 0f..1f,
                        enabled = selected?.outline == true,
                        displayValue = { (it * 100f).roundToInt().toString() },
                        preview = {
                            Box(
                                Modifier.size(24.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = selected?.strokeOpacity ?: 1f))
                                    .border(1.dp, Color(0x22000000), CircleShape)
                            )
                        },
                        onValueChangeStarted = ::beginRange,
                        onValueChangeFinished = ::finishRange
                    ) { selected?.let { text -> onLiveUpdate(text.copy(strokeOpacity = it)) } }
                }
                else -> {
                    val alignments = listOf(
                        Triple(appText("横向", english), selected?.vertical == false, 0),
                        Triple(appText("竖向", english), selected?.vertical == true, 1),
                        Triple(appText("居左", english), selected?.align == EditorTextAlign.Left, 2),
                        Triple(appText("居中", english), selected?.align == EditorTextAlign.Center, 3),
                        Triple(appText("居右", english), selected?.align == EditorTextAlign.Right, 4)
                    )
                    Row(
                        Modifier.fillMaxWidth().height(82.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        alignments.forEachIndexed { index, option ->
                            TextAlignmentCard(
                                label = option.first,
                                selected = option.second,
                                iconType = option.third,
                                enabled = selected != null,
                                modifier = Modifier.weight(1f).height(78.dp),
                                onClick = {
                                    selected?.let { text -> when (index) {
                                        0 -> onUpdate(text.copy(vertical = false))
                                        1 -> onUpdate(text.copy(vertical = true))
                                        2 -> onUpdate(text.copy(align = EditorTextAlign.Left))
                                        3 -> onUpdate(text.copy(align = EditorTextAlign.Center))
                                        4 -> onUpdate(text.copy(align = EditorTextAlign.Right))
                                    } }
                                }
                            )
                        }
                    }
                    EditorCompactRange(appText("字间距", english), selected?.letterSpacing ?: 0f, -2f..20f, selected != null, { "%.1f".format(it).removeSuffix(".0") }, ::beginRange, ::finishRange) { selected?.let { text -> onLiveUpdate(text.copy(letterSpacing = it)) } }
                    EditorCompactRange(appText("行间距", english), selected?.lineSpacing ?: 1f, 1f..2.5f, selected != null, { "%.1f".format(it).removeSuffix(".0") }, ::beginRange, ::finishRange) { selected?.let { text -> onLiveUpdate(text.copy(lineSpacing = it)) } }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("文本", "描边", "对齐").forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember(index) { MutableInteractionSource() },
                            indication = null
                        ) { onSubtab(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        appText(label, english),
                        color = if (subtab == index) accent else Color(0xFF777B79),
                        fontWeight = if (subtab == index) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = true)
                        )
                    )
                }
            }
        }
    }
    if (fontOrderDialogOpen) {
        EditorToolOrderDialog(
            title = appText("设置字体顺序", english),
            items = fontOrder,
            label = { appText(editorFontDisplayName(it), english) },
            onDismiss = { fontOrderDialogOpen = false },
            onConfirm = { fontOrder = it; fontOrderDialogOpen = false }
        )
    }
    customColorTarget?.let { stroke ->
        EditorColorPickerDialog(
            title = appText(if (stroke) "自选描边颜色" else "自选文字颜色", english),
            initialColor = if (stroke) selected?.strokeColor ?: android.graphics.Color.BLACK else selected?.color ?: android.graphics.Color.WHITE,
            onDismiss = { customColorTarget = null },
            onConfirm = { color ->
                if (selected != null) {
                    onUpdate(if (stroke) selected.copy(strokeColor = color) else selected.copy(color = color))
                }
                customColorTarget = null
            }
        )
    }
}

@Composable
private fun TextAlignmentCard(
    label: String,
    selected: Boolean,
    iconType: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize().padding(3.dp)
            .border(2.dp, if (selected) EditorAccent else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = if (selected) Color(0xFFF7FFF9) else EditorTile.copy(alpha = if (enabled) 1f else .45f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val iconColor = if (selected) EditorAccent else EditorMuted
            Canvas(Modifier.size(20.dp, 20.dp)) {
                val line = 2.dp.toPx()
                if (iconType == 1) {
                    repeat(4) { index ->
                        val x = (index + .5f) * size.width / 4f
                        drawLine(iconColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), line)
                    }
                } else {
                    repeat(4) { index ->
                        val y = (index + .5f) * size.height / 4f
                        val short = index % 2 == 1 && iconType >= 2
                        val width = if (short) size.width * .72f else size.width
                        val start = when (iconType) {
                            3 -> (size.width - width) / 2f
                            4 -> size.width - width
                            else -> 0f
                        }
                        drawLine(iconColor, androidx.compose.ui.geometry.Offset(start, y), androidx.compose.ui.geometry.Offset(start + width, y), line)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                color = if (selected) EditorAccent else EditorMuted,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EditorColorStrip(
    colors: List<Int>,
    selected: Int?,
    enabled: Boolean,
    picking: Boolean,
    toggleState: Boolean? = null,
    onToggle: () -> Unit = {},
    onPickColor: () -> Unit,
    onCustom: () -> Unit,
    onSelect: (Int) -> Unit
) {
    Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (toggleState != null) {
            Box(
                Modifier.size(25.dp).clip(CircleShape)
                    .background(if (toggleState) EditorAccent else Color.White)
                    .border(2.dp, if (toggleState) EditorAccent else Color(0xFFF01827), CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Text(if (toggleState) "✓" else "/", color = if (toggleState) Color.White else Color(0xFFF01827), style = MaterialTheme.typography.labelMedium)
            }
        }
        EditorEyedropperButton(picking = picking, enabled = enabled, onClick = onPickColor)
        colors.forEach { swatch ->
            EditorColorSwatch(swatch, selected == swatch, enabled) { onSelect(swatch) }
        }
        Box(
            Modifier.size(24.dp).graphicsLayer { alpha = if (enabled) 1f else .3f }.clip(CircleShape)
                .background(androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                .border(1.dp, Color(0x22000000), CircleShape)
                .clickable(enabled = enabled, onClick = onCustom)
        ) { if (selected != null && selected !in colors) EditorSwatchCheck(Color.White) }
    }
}

@Composable
private fun EditorColorSwatch(color: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val checkColor = if (
        android.graphics.Color.red(color) * .299f +
        android.graphics.Color.green(color) * .587f +
        android.graphics.Color.blue(color) * .114f > 165f
    ) Color(0xFF111111) else Color.White
    Box(
        Modifier.size(24.dp).graphicsLayer { alpha = if (enabled) 1f else .3f }
            .clip(CircleShape).background(Color(color))
            .border(1.dp, Color(0x22000000), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        if (selected) EditorSwatchCheck(checkColor)
    }
}

@Composable
private fun EditorSwatchCheck(color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 2.dp.toPx()
        val first = Offset(size.width * .34f, size.height * .51f)
        val middle = Offset(size.width * .46f, size.height * .63f)
        val last = Offset(size.width * .69f, size.height * .37f)
        drawLine(color, first, middle, stroke, androidx.compose.ui.graphics.StrokeCap.Square)
        drawLine(color, middle, last, stroke, androidx.compose.ui.graphics.StrokeCap.Square)
    }
}

@Composable
private fun EditorEyedropperButton(
    picking: Boolean,
    enabled: Boolean,
    pickedColor: Int? = null,
    onClick: () -> Unit
) {
    val hasPickedColor = pickedColor != null
    val accent = EditorAccent
    val checkColor = pickedColor?.let { color ->
        val luminance = android.graphics.Color.red(color) * .299f +
            android.graphics.Color.green(color) * .587f +
            android.graphics.Color.blue(color) * .114f
        if (luminance > 165f) Color(0xFF111111) else Color.White
    } ?: Color.White
    Box(
        Modifier.size(24.dp).graphicsLayer { alpha = if (enabled) 1f else .3f }
            .clip(CircleShape)
            .background(if (hasPickedColor) Color(pickedColor!!) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (!hasPickedColor) {
                val cell = 4.dp.toPx()
                repeat(6) { row ->
                    repeat(6) { column ->
                        drawRect(
                            if ((row + column) % 2 == 0) Color.White else Color(0xFFC9C9C9),
                            Offset(column * cell, row * cell),
                            androidx.compose.ui.geometry.Size(cell, cell)
                        )
                    }
                }
            }
            drawCircle(Color(0x22000000), radius = size.minDimension / 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            if (picking) {
                drawCircle(
                    accent,
                    radius = size.minDimension / 2f - 1.5.dp.toPx(),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
                )
            }
        }
        if (hasPickedColor && !picking) {
            EditorSwatchCheck(checkColor)
        } else {
            Icon(
                EditorPrototypeIcons.Eyedropper,
                appText("吸管取色", LocalAppEnglish.current),
                tint = if (picking) EditorAccent else EditorInk,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun EditorColorPickerDialog(
    initialColor: Int,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
    }
    var hue by remember(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(initialColor) { mutableFloatStateOf(initialHsv[2]) }
    val selectedColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val progress by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(220, easing = CubicBezierEasing(.22f, .8f, .28f, 1f)),
        label = "color-picker-dialog"
    )
    val slideDistance = with(LocalDensity.current) { 80.dp.toPx() }
    var panelBounds by remember { mutableStateOf<Rect?>(null) }
    fun dismissAnimated(after: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        shown = false
        scope.launch {
            delay(220)
            (after ?: onDismiss).invoke()
        }
    }
    LaunchedEffect(Unit) { shown = true }
    Dialog(
        onDismissRequest = { dismissAnimated() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = .34f * progress))
                .pointerInput(panelBounds, closing) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var upPosition: Offset? = null
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                upPosition = change.position
                                break
                            }
                        }
                        if (!closing && upPosition != null && panelBounds?.contains(down.position) != true) {
                            dismissAnimated()
                        }
                        }
                    }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 34.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp)
                    .onGloballyPositioned { panelBounds = it.boundsInParent() }
                    .graphicsLayer {
                        alpha = progress
                        translationY = (1f - progress) * slideDistance
                    },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 20.dp
            ) {
                Column(
                    Modifier.padding(start = 22.dp, top = 28.dp, end = 22.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(selectedColor)).border(1.dp, Color(0x22000000), CircleShape))
                        Text(title, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), color = EditorInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { dismissAnimated() }) { Icon(EditorPrototypeIcons.Close, appText("关闭", LocalAppEnglish.current), tint = EditorInk) }
                    }
                    Canvas(
                        Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))
                            .pointerInput(Unit) {
                                fun update(point: androidx.compose.ui.geometry.Offset) {
                                    saturation = (point.x / size.width).coerceIn(0f, 1f)
                                    brightness = (1f - point.y / size.height).coerceIn(0f, 1f)
                                }
                                awaitEachGesture {
                                    update(awaitFirstDown(requireUnconsumed = false).position)
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                                        update(change.position)
                                        change.consume()
                                        if (!change.pressed) break
                                    }
                                }
                            }
                    ) {
                        val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                        drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.White, hueColor)))
                        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        val marker = androidx.compose.ui.geometry.Offset(saturation * size.width, (1f - brightness) * size.height)
                        drawCircle(Color.White, 14.dp.toPx(), marker, style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
                        drawCircle(Color.Black.copy(alpha = .45f), 16.dp.toPx(), marker, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    }
                    Spacer(Modifier.height(18.dp))
                    Canvas(
                        Modifier.fillMaxWidth().height(38.dp)
                            .pointerInput(Unit) {
                                fun update(x: Float) { hue = (x / size.width).coerceIn(0f, 1f) * 360f }
                                awaitEachGesture {
                                    update(awaitFirstDown(requireUnconsumed = false).position.x)
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                                        update(change.position.x)
                                        change.consume()
                                        if (!change.pressed) break
                                    }
                                }
                            }
                    ) {
                        val colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                        val trackHeight = 30.dp.toPx().coerceAtMost(size.height)
                        drawRoundRect(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(colors),
                            topLeft = Offset(0f, (size.height - trackHeight) / 2f),
                            size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f)
                        )
                        val x = hue / 360f * size.width
                        drawCircle(Color(selectedColor), 13.dp.toPx(), androidx.compose.ui.geometry.Offset(x, size.height / 2f))
                        drawCircle(Color.White, 13.dp.toPx(), androidx.compose.ui.geometry.Offset(x, size.height / 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(5.dp.toPx()))
                        drawCircle(EditorInk, 15.dp.toPx(), androidx.compose.ui.geometry.Offset(x, size.height / 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    }
                    Spacer(Modifier.height(22.dp))
                    TextButton(
                        onClick = { dismissAnimated { onConfirm(selectedColor) } },
                        modifier = Modifier.fillMaxWidth(.8f).height(54.dp),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(2.dp, EditorAccent)
                    ) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(appText("应用", LocalAppEnglish.current), color = EditorAccent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    TextButton(onClick = { dismissAnimated() }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Text(
                            if (LocalAppEnglish.current) "Cancel" else "取消",
                            color = EditorInk,
                            fontSize = 17.sp
                        )
                    }
                }
            }
        }
    }
}

private fun editorComposeFont(font: EditorFont): FontFamily = when (font) {
    EditorFont.System -> FontFamily(androidx.compose.ui.text.font.Font(com.example.album.R.font.noto_sans_sc))
    EditorFont.Serif -> FontFamily(androidx.compose.ui.text.font.Font(com.example.album.R.font.noto_serif_sc))
    EditorFont.Monospace -> FontFamily(androidx.compose.ui.text.font.Font(com.example.album.R.font.ma_shan_zheng))
    EditorFont.Kai -> FontFamily(androidx.compose.ui.text.font.Font(com.example.album.R.font.zhi_mang_xing))
    EditorFont.Song -> FontFamily(androidx.compose.ui.text.font.Font(com.example.album.R.font.zcool_xiaowei))
    EditorFont.Hei -> FontFamily(androidx.compose.ui.text.font.Font(com.example.album.R.font.zcool_qingke_huangyou))
    EditorFont.Fang -> FontFamily(androidx.compose.ui.text.font.Font(com.example.album.R.font.dotgothic16))
    EditorFont.Cursive -> FontFamily(androidx.compose.ui.text.font.Font(com.example.album.R.font.long_cang))
    EditorFont.Wide -> FontFamily(androidx.compose.ui.text.font.Font(com.example.album.R.font.liu_jian_mao_cao))
    EditorFont.Rounded -> FontFamily(androidx.compose.ui.text.font.Font(com.example.album.R.font.zcool_kuaile))
}

private fun editorFontDisplayName(font: EditorFont): String = when (font) {
    EditorFont.System -> "黑体"
    EditorFont.Serif -> "宋体"
    EditorFont.Monospace -> "楷书"
    EditorFont.Kai -> "行书"
    EditorFont.Song -> "小薇体"
    EditorFont.Hei -> "黄油体"
    EditorFont.Fang -> "点阵体"
    EditorFont.Cursive -> "龙藏体"
    EditorFont.Wide -> "毛草"
    EditorFont.Rounded -> "快乐体"
}

@Composable
private fun EditorVisualRange(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    displayValue: (Float) -> String,
    preview: @Composable () -> Unit,
    onValueChangeStarted: () -> Unit = {},
    onValueChangeFinished: () -> Unit = {},
    onValue: (Float) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(42.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(38.dp), contentAlignment = Alignment.Center) { preview() }
        EditorThinSlider(
            value = value,
            onValueChange = onValue,
            valueRange = valueRange,
            enabled = enabled,
            onValueChangeStarted = onValueChangeStarted,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.weight(1f).height(28.dp)
        )
        Text(
            displayValue(value),
            color = if (enabled) Color(0xFF555957) else Color(0xFFB6B8B7),
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun EditorCompactRange(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    displayValue: (Float) -> String = { (((it - range.start) / (range.endInclusive - range.start)) * 100f).roundToInt().toString() },
    onValueChangeStarted: () -> Unit = {},
    onValueChangeFinished: () -> Unit = {},
    onValue: (Float) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (enabled) Color(0xFF555957) else Color(0xFFB6B8B7), modifier = Modifier.width(38.dp), style = MaterialTheme.typography.labelSmall)
        EditorThinSlider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            enabled = enabled,
            onValueChangeStarted = onValueChangeStarted,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.weight(1f).height(28.dp)
        )
        Text(displayValue(value), color = Color(0xFF777B79), modifier = Modifier.width(34.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EditorThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    onValueChangeStarted: () -> Unit = {},
    onValueChangeFinished: () -> Unit = {}
) {
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeStarted by rememberUpdatedState(onValueChangeStarted)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(.0001f)
    val gestureModifier = if (enabled) {
        Modifier.pointerInput(valueRange, steps) {
            fun updateFromX(x: Float) {
                val inset = 12.dp.toPx().coerceAtMost(size.width / 4f)
                val trackWidth = (size.width - inset * 2f).coerceAtLeast(1f)
                val fraction = ((x - inset) / trackWidth).coerceIn(0f, 1f)
                val raw = valueRange.start + span * fraction
                val snapped = if (steps > 0) {
                    val intervals = steps + 1
                    valueRange.start + (fraction * intervals).roundToInt() / intervals.toFloat() * span
                } else raw
                latestOnValueChange(snapped.coerceIn(valueRange))
            }
            detectDragGestures(
                onDragStart = {
                    latestOnValueChangeStarted()
                    updateFromX(it.x)
                },
                onDrag = { change, _ ->
                    change.consume()
                    updateFromX(change.position.x)
                },
                onDragEnd = latestOnValueChangeFinished,
                onDragCancel = latestOnValueChangeFinished
            )
        }
    } else Modifier
    val accent = EditorAccent
    Canvas(
        modifier.then(gestureModifier).progressSemantics(value.coerceIn(valueRange), valueRange, steps)
    ) {
        val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
        val centerY = size.height / 2f
        val inset = 12.dp.toPx().coerceAtMost(size.width / 4f)
        val trackWidth = (size.width - inset * 2f).coerceAtLeast(1f)
        val thumbX = inset + fraction * trackWidth
        val inactive = if (enabled) Color(0xFF3E4140) else Color(0xFFD5D7D5)
        val active = if (enabled) accent else Color(0xFFB9BCBA)
        drawLine(inactive, Offset(inset, centerY), Offset(size.width - inset, centerY), 4.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(active, Offset(inset, centerY), Offset(thumbX, centerY), 4.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(active, 7.dp.toPx(), Offset(thumbX, centerY))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEditorCropFrame(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    color: Color
) {
    val thin = 2.dp.toPx()
    val thick = 5.dp.toPx()
    val corner = 22.dp.toPx()
    val middle = 24.dp.toPx()
    drawRect(
        color,
        topLeft = androidx.compose.ui.geometry.Offset(left, top),
        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = thin)
    )
    listOf(
        androidx.compose.ui.geometry.Offset(left, top) to listOf(
            androidx.compose.ui.geometry.Offset(left + corner, top),
            androidx.compose.ui.geometry.Offset(left, top + corner)
        ),
        androidx.compose.ui.geometry.Offset(right, top) to listOf(
            androidx.compose.ui.geometry.Offset(right - corner, top),
            androidx.compose.ui.geometry.Offset(right, top + corner)
        ),
        androidx.compose.ui.geometry.Offset(left, bottom) to listOf(
            androidx.compose.ui.geometry.Offset(left + corner, bottom),
            androidx.compose.ui.geometry.Offset(left, bottom - corner)
        ),
        androidx.compose.ui.geometry.Offset(right, bottom) to listOf(
            androidx.compose.ui.geometry.Offset(right - corner, bottom),
            androidx.compose.ui.geometry.Offset(right, bottom - corner)
        )
    ).forEach { (origin, ends) ->
        ends.forEach { end -> drawLine(color, origin, end, thick) }
    }
    drawLine(color, androidx.compose.ui.geometry.Offset((left + right - middle) / 2f, top), androidx.compose.ui.geometry.Offset((left + right + middle) / 2f, top), thick)
    drawLine(color, androidx.compose.ui.geometry.Offset((left + right - middle) / 2f, bottom), androidx.compose.ui.geometry.Offset((left + right + middle) / 2f, bottom), thick)
    drawLine(color, androidx.compose.ui.geometry.Offset(left, (top + bottom - middle) / 2f), androidx.compose.ui.geometry.Offset(left, (top + bottom + middle) / 2f), thick)
    drawLine(color, androidx.compose.ui.geometry.Offset(right, (top + bottom - middle) / 2f), androidx.compose.ui.geometry.Offset(right, (top + bottom + middle) / 2f), thick)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEditorTextFrame(color: Color) {
    val thin = 2.dp.toPx()
    val thick = 4.dp.toPx()
    val corner = 18.dp.toPx()
    val middle = 24.dp.toPx()
    drawRect(color, style = androidx.compose.ui.graphics.drawscope.Stroke(thin))
    listOf(
        androidx.compose.ui.geometry.Offset.Zero to listOf(androidx.compose.ui.geometry.Offset(corner, 0f), androidx.compose.ui.geometry.Offset(0f, corner)),
        androidx.compose.ui.geometry.Offset(size.width, 0f) to listOf(androidx.compose.ui.geometry.Offset(size.width - corner, 0f), androidx.compose.ui.geometry.Offset(size.width, corner)),
        androidx.compose.ui.geometry.Offset(0f, size.height) to listOf(androidx.compose.ui.geometry.Offset(corner, size.height), androidx.compose.ui.geometry.Offset(0f, size.height - corner)),
        androidx.compose.ui.geometry.Offset(size.width, size.height) to listOf(androidx.compose.ui.geometry.Offset(size.width - corner, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height - corner))
    ).forEach { (origin, ends) -> ends.forEach { drawLine(color, origin, it, thick) } }
    drawLine(color, androidx.compose.ui.geometry.Offset((size.width - middle) / 2f, 0f), androidx.compose.ui.geometry.Offset((size.width + middle) / 2f, 0f), thick)
    drawLine(color, androidx.compose.ui.geometry.Offset((size.width - middle) / 2f, size.height), androidx.compose.ui.geometry.Offset((size.width + middle) / 2f, size.height), thick)
    drawLine(color, androidx.compose.ui.geometry.Offset(0f, (size.height - middle) / 2f), androidx.compose.ui.geometry.Offset(0f, (size.height + middle) / 2f), thick)
    drawLine(color, androidx.compose.ui.geometry.Offset(size.width, (size.height - middle) / 2f), androidx.compose.ui.geometry.Offset(size.width, (size.height + middle) / 2f), thick)
}

private fun previewColorMatrix(state: ImageEditState): androidx.compose.ui.graphics.ColorMatrix {
    return androidx.compose.ui.graphics.ColorMatrix(editorColorMatrix(state).array.copyOf())
}

private fun itemDescription(state: ImageEditState, english: Boolean): String =
    if (english) "Edit preview, rotated ${state.rotation} degrees" else "编辑预览，旋转 ${state.rotation} 度"
