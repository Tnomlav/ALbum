package com.example.album.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.album.ui.theme.ThemeAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.util.Calendar

@Composable
fun VaultOptionSheet(
    title: String,
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    VaultBottomSheet(title, onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            options.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onSelect(option) }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(option, modifier = Modifier.weight(1f), fontSize = 15.sp)
                    if (option == selected) Text("✓", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun VaultApplyChoiceSheet(
    title: String,
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var draft by remember(selected, options) { mutableStateOf(selected.takeIf { it in options } ?: options.firstOrNull().orEmpty()) }
    VaultBottomSheet(title, onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            options.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { draft = option }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(option, modifier = Modifier.weight(1f), fontSize = 15.sp)
                    if (option == draft) Text("✓", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onApply(draft) }, enabled = draft.isNotBlank(), modifier = Modifier.height(48.dp)) {
                Text(appText("应用", LocalAppEnglish.current), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun VaultSortChoiceSheet(
    title: String,
    methods: List<String>,
    selectedMethod: String,
    selectedDirection: String,
    directions: List<String>,
    onDismiss: () -> Unit,
    onApply: (String, String) -> Unit
) {
    var draftMethod by remember(selectedMethod, methods) { mutableStateOf(selectedMethod) }
    var draftDirection by remember(selectedDirection, directions) { mutableStateOf(selectedDirection) }
    VaultBottomSheet(title, onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            methods.forEach { option ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { draftMethod = option }.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(option, Modifier.weight(1f), fontSize = 15.sp)
                    if (option == draftMethod) Text("✓", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(46.dp))
            directions.forEach { option ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { draftDirection = option }.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(option, Modifier.weight(1f), fontSize = 15.sp)
                    if (option == draftDirection) Text("✓", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 12.dp, end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onApply(draftMethod, draftDirection) }, modifier = Modifier.height(48.dp)) {
                Text(appText("应用", LocalAppEnglish.current), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun VaultSortWheelSheet(
    title: String,
    methods: List<String>,
    selectedMethod: String,
    selectedDirection: String,
    directions: List<String>,
    onDismiss: () -> Unit,
    onApply: (String, String) -> Unit
) {
    val english = LocalAppEnglish.current
    var draftMethod by remember(selectedMethod, methods) { mutableStateOf(selectedMethod) }
    var draftDirection by remember(selectedDirection, directions) { mutableStateOf(selectedDirection) }
    VaultBottomSheet(title, onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                ChoiceWheel(methods, draftMethod, { draftMethod = it }, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurface.copy(alpha = .16f))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                ChoiceWheel(directions, draftDirection, { draftDirection = it }, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurface.copy(alpha = .16f))
            }
        }
        Spacer(Modifier.height(34.dp))
        Box(Modifier.fillMaxWidth().height(74.dp), contentAlignment = Alignment.Center) {
            TextButton(onClick = { onApply(draftMethod, draftDirection) }, modifier = Modifier.fillMaxWidth(.8f).height(54.dp), shape = CircleShape, border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)) {
                Text(if (english) "Apply" else "应用", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun VaultColorSheet(
    title: String,
    options: List<ThemeAccent>,
    selected: ThemeAccent,
    onDismiss: () -> Unit,
    onSelect: (ThemeAccent) -> Unit,
    optionLabel: (ThemeAccent) -> String = { it.label }
) {
    VaultBottomSheet(title, onDismiss) {
        options.chunked(3).forEach { rowOptions ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rowOptions.forEach { option ->
                    val selectedScale by animateFloatAsState(
                        if (option == selected) 1.12f else 1f,
                        tween(140),
                        label = "theme-color-${option.name}"
                    )
                    Column(
                        Modifier.widthIn(min = 82.dp).clickable { onSelect(option) }.padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp).graphicsLayer { scaleX = selectedScale; scaleY = selectedScale },
                            color = option.color,
                            shape = CircleShape,
                            border = if (option == selected) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null
                        ) {}
                        Text(optionLabel(option), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                repeat(3 - rowOptions.size) { Spacer(Modifier.widthIn(min = 82.dp)) }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun VaultInfoSheet(title: String, body: String, dismissLabel: String = "知道了", onDismiss: () -> Unit) {
    VaultBottomSheet(title, onDismiss) {
        Text(
            body,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 21.sp
        )
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).height(48.dp)) {
            Text(dismissLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun VaultConfirmationSheet(
    title: String,
    body: String = "",
    confirmLabel: String,
    danger: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    VaultConfirmationFrame(title = title, body = body, onDismiss = onDismiss) {
        TextButton(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(.8f).height(54.dp),
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(
                2.dp,
                if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                confirmLabel,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text(appText("取消", LocalAppEnglish.current), color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp)
        }
    }
}

@Composable
fun VaultChoiceConfirmationSheet(
    title: String,
    choices: List<String>,
    onDismiss: () -> Unit,
    onChoice: (String) -> Unit
) {
    VaultConfirmationFrame(title = title, onDismiss = onDismiss) {
        choices.forEach { choice ->
            TextButton(onClick = { onChoice(choice) }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text(choice, color = MaterialTheme.colorScheme.primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text(appText("取消", LocalAppEnglish.current), color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp)
        }
    }
}

@Composable
private fun VaultConfirmationFrame(
    title: String,
    body: String = "",
    onDismiss: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val progress by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(220, easing = CubicBezierEasing(.22f, .8f, .28f, 1f)),
        label = "confirmation-sheet"
    )
    val slideDistance = with(LocalDensity.current) { 80.dp.toPx() }
    var panelBounds by remember { mutableStateOf<Rect?>(null) }
    fun dismissAnimated() {
        if (closing) return
        closing = true
        shown = false
        scope.launch { delay(220); onDismiss() }
    }
    LaunchedEffect(Unit) { shown = true }
    Dialog(
        onDismissRequest = ::dismissAnimated,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .34f * progress))
                .pointerInput(panelBounds, closing) {
                    detectTapGestures { position ->
                        if (!closing && panelBounds?.contains(position) != true) dismissAnimated()
                    }
                }
                .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp, vertical = 34.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp)
                    .onGloballyPositioned { panelBounds = it.boundsInParent() }
                    .graphicsLayer { alpha = progress; translationY = (1f - progress) * slideDistance },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 20.dp
            ) {
                Column(
                    Modifier.padding(start = 22.dp, top = 28.dp, end = 22.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 60.dp), contentAlignment = Alignment.Center) {
                        Text(title, textAlign = TextAlign.Center, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (body.isNotBlank()) {
                        Text(
                            body,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = actions
                    )
                }
            }
        }
    }
}

@Composable
fun VaultWheelChoiceSheet(
    title: String,
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
    playerStyle: Boolean = false
) {
    var draft by remember(selected, options) { mutableStateOf(if (selected in options) selected else options.firstOrNull().orEmpty()) }
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val progress by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(220, easing = CubicBezierEasing(.22f, .8f, .28f, 1f)),
        label = "wheel-sheet"
    )
    val slideDistance = with(LocalDensity.current) { 80.dp.toPx() }
    var panelBounds by remember { mutableStateOf<Rect?>(null) }
    val foreground = if (playerStyle) Color.White else MaterialTheme.colorScheme.onSurface
    val muted = if (playerStyle) Color.White.copy(alpha = .72f) else MaterialTheme.colorScheme.onSurfaceVariant
    val divider = if (playerStyle) Color.White.copy(alpha = .18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .16f)
    fun dismissAnimated(after: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        shown = false
        scope.launch { delay(220); after?.invoke() ?: onDismiss() }
    }
    LaunchedEffect(Unit) { shown = true }
    Dialog(
        onDismissRequest = { dismissAnimated() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = (if (playerStyle) .34f else .30f) * progress))
                .pointerInput(panelBounds, closing) {
                    detectTapGestures { position ->
                        if (!closing && panelBounds?.contains(position) != true) dismissAnimated()
                    }
                }
                .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp, vertical = 28.dp),
            contentAlignment = if (playerStyle) Alignment.Center else Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp)
                    .onGloballyPositioned { panelBounds = it.boundsInParent() }
                    .graphicsLayer {
                        alpha = progress
                        translationY = if (playerStyle) 0f else (1f - progress) * slideDistance
                        if (playerStyle) { scaleX = .94f + .06f * progress; scaleY = .94f + .06f * progress }
                    },
                color = if (playerStyle) Color(0xFF141414).copy(alpha = .88f) else MaterialTheme.colorScheme.surface,
                contentColor = foreground,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 20.dp
            ) {
                Column(
                    Modifier.padding(start = 22.dp, top = 28.dp, end = 22.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 60.dp), contentAlignment = Alignment.Center) {
                        Text(title, color = foreground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(
                            onClick = { dismissAnimated() },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(40.dp)
                                .background((if (playerStyle) Color.White else foreground).copy(alpha = .08f), CircleShape)
                        ) { Icon(Icons.Outlined.Close, appText("关闭", LocalAppEnglish.current), tint = foreground) }
                    }
                    ChoiceWheel(options, draft, onSelected = { draft = it }, foreground = foreground, muted = muted, divider = divider)
                    Box(Modifier.fillMaxWidth().height(74.dp), contentAlignment = Alignment.Center) {
                        TextButton(
                            onClick = { dismissAnimated { onApply(draft) } },
                            modifier = Modifier.fillMaxWidth(.8f).height(54.dp),
                            shape = CircleShape,
                            border = androidx.compose.foundation.BorderStroke(2.dp, if (playerStyle) Color.White else MaterialTheme.colorScheme.primary)
                        ) {
                            Text(appText("应用", LocalAppEnglish.current), color = if (playerStyle) Color.White else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceWheel(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    foreground: Color,
    muted: Color,
    divider: Color
) {
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val scope = rememberCoroutineScope()
    val rowHeight = 48.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.roundToPx() }
    val centerPadding = 76.dp
    LaunchedEffect(state, options) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .filter { !it }
            .collect {
                val nearest = state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset >= rowHeightPx / 2) 1 else 0
                options.getOrNull(nearest)?.let(onSelected)
            }
    }
    Box(
        Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 18.dp).drawBehind {
            val top = centerPadding.toPx()
            val bottom = (centerPadding + rowHeight).toPx()
            val stroke = 1.dp.toPx()
            drawLine(divider, androidx.compose.ui.geometry.Offset(0f, top), androidx.compose.ui.geometry.Offset(size.width, top), stroke)
            drawLine(divider, androidx.compose.ui.geometry.Offset(0f, bottom), androidx.compose.ui.geometry.Offset(size.width, bottom), stroke)
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = centerPadding)
        ) {
            itemsIndexed(options, key = { _, option -> option }) { index, option ->
                Text(
                    option,
                    modifier = Modifier.fillMaxWidth().height(rowHeight).clickable {
                        onSelected(option)
                        scope.launch { state.animateScrollToItem(index) }
                    }.padding(top = 13.dp),
                    color = if (option == selected) foreground else muted,
                    textAlign = TextAlign.Center,
                    fontSize = if (option == selected) 16.sp else 14.sp
                )
            }
        }
    }
}

@Composable
fun VaultTextInputSheet(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    confirmLabel: String,
    confirmEnabled: Boolean = value.isNotBlank(),
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    VaultBottomSheet(title, onDismiss) {
        VaultActionInput(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.widthIn(min = 64.dp).height(48.dp)) {
                Text(confirmLabel, color = if (confirmEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun VaultTextInputDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    confirmLabel: String,
    confirmEnabled: Boolean = value.isNotBlank(),
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val progress by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(180, easing = CubicBezierEasing(.22f, .8f, .28f, 1f)),
        label = "text-input-dialog"
    )
    var panelBounds by remember { mutableStateOf<Rect?>(null) }
    fun dismissAnimated(after: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        shown = false
        scope.launch {
            delay(180)
            after?.invoke() ?: onDismiss()
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
                    detectTapGestures { position ->
                        if (!closing && panelBounds?.contains(position) != true) dismissAnimated()
                    }
                }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp)
                    .onGloballyPositioned { panelBounds = it.boundsInParent() }
                    .graphicsLayer { alpha = progress; scaleX = .96f + .04f * progress; scaleY = .96f + .04f * progress },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 20.dp
            ) {
                Column {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        Text(title, textAlign = TextAlign.Center, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(
                            onClick = { dismissAnimated() },
                            modifier = Modifier.align(Alignment.CenterEnd).size(40.dp)
                        ) {
                            Icon(Icons.Outlined.Close, appText("关闭", LocalAppEnglish.current))
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)) {
                        VaultActionInput(value, onValueChange, label, Modifier.fillMaxWidth(), singleLine)
                    }
                    Row(
                        Modifier.fillMaxWidth().height(54.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { dismissAnimated(onConfirm) },
                            enabled = confirmEnabled,
                            modifier = Modifier.widthIn(min = 64.dp).height(48.dp)
                        ) {
                            Text(
                                confirmLabel,
                                color = if (confirmEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultActionInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    val shape = RoundedCornerShape(6.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
        modifier = modifier
            .height(if (singleLine) 44.dp else 132.dp)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 10.dp)
            .semantics { contentDescription = label }
    )
}

@Composable
fun VaultRatioInputSheet(
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
    VaultBottomSheet(title, onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VaultActionInput(width, onWidthChange, appText("宽", english), Modifier.weight(1f))
            Text(":")
            VaultActionInput(height, onHeightChange, appText("高", english), Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = onConfirm, enabled = valid, modifier = Modifier.widthIn(min = 64.dp).height(48.dp)) {
                Text(
                    appText("应用", english),
                    color = if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun VaultDateSheet(initialMillis: Long, onDismiss: () -> Unit, onSelect: (Long) -> Unit) {
    val english = LocalAppEnglish.current
    val initial = remember(initialMillis) { Calendar.getInstance().apply { timeInMillis = initialMillis } }
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val years = remember(currentYear) { (currentYear downTo currentYear - 14).toList() }
    var year by remember { mutableIntStateOf(initial.get(Calendar.YEAR).coerceIn(years.last(), years.first())) }
    var month by remember { mutableIntStateOf(initial.get(Calendar.MONTH) + 1) }
    var day by remember { mutableIntStateOf(initial.get(Calendar.DAY_OF_MONTH)) }
    val dayCount = remember(year, month) {
        Calendar.getInstance().apply { set(year, month, 0) }.get(Calendar.DAY_OF_MONTH)
    }
    LaunchedEffect(dayCount) {
        if (day > dayCount) day = dayCount
    }

    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val progress by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(220, easing = CubicBezierEasing(.22f, .8f, .28f, 1f)),
        label = "date-sheet"
    )
    val slideDistance = with(LocalDensity.current) { 80.dp.toPx() }
    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .16f)
    var panelBounds by remember { mutableStateOf<Rect?>(null) }
    fun dismissAnimated(after: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        shown = false
        scope.launch {
            delay(220)
            after?.invoke() ?: onDismiss()
        }
    }
    LaunchedEffect(Unit) { shown = true }

    Dialog(
        onDismissRequest = { dismissAnimated() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .30f * progress))
                .pointerInput(panelBounds, closing) {
                    detectTapGestures { position ->
                        if (!closing && panelBounds?.contains(position) != true) dismissAnimated()
                    }
                }
                .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp, vertical = 28.dp),
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
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 20.dp
            ) {
                Column {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        Text(appText("跳转日期", english), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(
                            onClick = { dismissAnimated() },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                                .size(40.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), CircleShape)
                        ) { Icon(Icons.Outlined.Close, appText("关闭", english)) }
                    }
                    Box(
                        Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 12.dp).drawBehind {
                            val top = 80.dp.toPx()
                            val bottom = 120.dp.toPx()
                            val stroke = 1.dp.toPx()
                            drawLine(dividerColor, androidx.compose.ui.geometry.Offset(0f, top), androidx.compose.ui.geometry.Offset(size.width, top), stroke)
                            drawLine(dividerColor, androidx.compose.ui.geometry.Offset(0f, bottom), androidx.compose.ui.geometry.Offset(size.width, bottom), stroke)
                        }
                    ) {
                        Row(Modifier.fillMaxSize()) {
                            DateWheel(years, year, if (english) { value -> "$value" } else { value -> "${value}年" }, { year = it }, Modifier.weight(1.25f))
                            DateWheel((1..12).toList(), month, if (english) { value -> "$value" } else { value -> "${value}月" }, { month = it }, Modifier.weight(1f))
                            DateWheel((1..dayCount).toList(), day, if (english) { value -> "$value" } else { value -> "${value}日" }, { day = it }, Modifier.weight(1f))
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(62.dp).padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                        TextButton(
                            onClick = {
                                val selected = Calendar.getInstance().apply {
                                    set(year, month - 1, day, 0, 0, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                                dismissAnimated { onSelect(selected) }
                            },
                            modifier = Modifier.widthIn(min = 100.dp).height(48.dp)
                        ) { Text(appText("跳转", english), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateWheel(
    values: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = values.indexOf(selected).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val rowHeightPx = with(LocalDensity.current) { 40.dp.roundToPx() }
    LaunchedEffect(state, values) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .filter { scrolling -> !scrolling }
            .collect {
                val nearest = state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset >= rowHeightPx / 2) 1 else 0
                values.getOrNull(nearest)?.let(onSelected)
            }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 80.dp)
    ) {
        itemsIndexed(values, key = { _, value -> value }) { index, value ->
            Text(
                text = label(value),
                modifier = Modifier.fillMaxWidth().height(40.dp).clickable {
                    onSelected(value)
                    scope.launch { state.animateScrollToItem(index) }
                }.padding(top = 9.dp),
                textAlign = TextAlign.Center,
                color = if (value == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (value == selected) 16.sp else 14.sp
            )
        }
    }
}

@Composable
private fun VaultBottomSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val english = LocalAppEnglish.current
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val progress by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(220, easing = CubicBezierEasing(.22f, .8f, .28f, 1f)),
        label = "bottom-sheet"
    )
    val slideDistance = with(LocalDensity.current) { 80.dp.toPx() }
    var panelBounds by remember { mutableStateOf<Rect?>(null) }
    fun dismissAnimated() {
        if (closing) return
        closing = true
        shown = false
        scope.launch {
            delay(220)
            onDismiss()
        }
    }
    LaunchedEffect(Unit) { shown = true }
    Dialog(
        onDismissRequest = ::dismissAnimated,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .30f * progress))
                .pointerInput(panelBounds, closing) {
                    detectTapGestures { position ->
                        if (!closing && panelBounds?.contains(position) != true) dismissAnimated()
                    }
                }
                .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp, vertical = 28.dp),
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
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 20.dp
            ) {
                Column {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = ::dismissAnimated, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = appText("关闭", english))
                        }
                    }
                    content()
                }
            }
        }
    }
}
