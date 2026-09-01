package com.example.album.ui.screens

import android.content.SharedPreferences
import android.os.PowerManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.components.VaultTextInputSheet
import com.example.album.ui.components.VaultWheelChoiceSheet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api

private enum class WallpaperOrder { InOrder, TrueRandom, Shuffle }
private enum class WallpaperSound { Disabled, ForegroundOnly, BackgroundNoFocus, BackgroundWithFocus }
private enum class WallpaperSettingDialog { Span, Order, Frequency, Sound }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperSettingsSheet(
    preferences: SharedPreferences,
    initialIsVideo: Boolean,
    onDismiss: () -> Unit
) {
    val english = LocalAppEnglish.current
    val context = LocalContext.current
    val systemPowerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    fun text(zh: String, en: String) = if (english) en else zh
    var isVideo by remember { mutableStateOf(initialIsVideo) }
    var syncLock by remember { mutableStateOf(preferences.getBoolean("wallpaper_sync_lock", false)) }
    var allowBackground by remember { mutableStateOf(preferences.getBoolean("wallpaper_allow_background", false)) }
    var spanMode by remember { mutableStateOf(preferences.getString("wallpaper_span_mode", "single") ?: "single") }
    var autoAdjustImage by remember { mutableStateOf(preferences.getBoolean("wallpaper_static_auto_adjust", true)) }
    var returnSwitch by remember(isVideo) { mutableStateOf(preferences.getBoolean(if (isVideo) "wallpaper_video_switch_on_home" else "wallpaper_static_switch_on_home", false)) }
    var frequency by remember(isVideo) { mutableStateOf(preferences.getString("wallpaper_frequency", "5") ?: "5") }
    var customSeconds by remember { mutableStateOf(preferences.getString("wallpaper_custom_seconds", "10") ?: "10") }
    var order by remember(isVideo) {
        mutableStateOf(
            runCatching { WallpaperOrder.valueOf(preferences.getString(if (isVideo) "wallpaper_video_order" else "wallpaper_static_order", WallpaperOrder.InOrder.name)!!) }
                .getOrDefault(WallpaperOrder.InOrder)
        )
    }
    var dynamicBackground by remember { mutableStateOf(preferences.getBoolean("wallpaper_dynamic_background", false)) }
    var lowPower by remember {
        mutableStateOf(
            preferences.getBoolean("wallpaper_low_power", false) ||
                (context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true)
        )
    }
    var sound by remember {
        mutableStateOf(
            runCatching { WallpaperSound.valueOf(preferences.getString("wallpaper_sound", WallpaperSound.Disabled.name)!!) }
                .getOrDefault(WallpaperSound.Disabled)
        )
    }
    var wallpaperVolume by remember { mutableStateOf(preferences.getFloat("wallpaper_volume", 1f).coerceIn(0f, 1f)) }
    var dialog by remember { mutableStateOf<WallpaperSettingDialog?>(null) }

    fun save() {
        preferences.edit()
            .putBoolean("wallpaper_sync_lock", syncLock)
            .putBoolean("wallpaper_allow_background", allowBackground)
            .putString("wallpaper_span_mode", spanMode)
            .putBoolean("wallpaper_static_auto_adjust", autoAdjustImage)
            .putBoolean(if (isVideo) "wallpaper_video_switch_on_home" else "wallpaper_static_switch_on_home", returnSwitch)
            .putString("wallpaper_frequency", frequency)
            .putString("wallpaper_custom_seconds", customSeconds)
            .putString(if (isVideo) "wallpaper_video_order" else "wallpaper_static_order", order.name)
            .putBoolean("wallpaper_dynamic_background", dynamicBackground)
            .putBoolean("wallpaper_low_power", lowPower)
            .putString("wallpaper_sound", sound.name)
            .putFloat("wallpaper_volume", wallpaperVolume)
            .apply()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text("壁纸设置", "Wallpaper settings")) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    TypeChoice(text("静态", "Static"), !isVideo) { isVideo = false }
                    TypeChoice(text("动态", "Live"), isVideo) { isVideo = true }
                }
                SettingSwitch(text("同步到锁屏", "Sync to lock screen"), syncLock) { syncLock = it }
                SettingSwitch(text("允许后台运行", "Allow background operation"), allowBackground) { allowBackground = it }
                SettingChoiceRow(
                    text("壁纸范围", "Wallpaper span"),
                    if (spanMode == "single") text("单屏宽度", "Single screen") else text("跨屏宽度", "Across screens")
                ) { dialog = WallpaperSettingDialog.Span }
                SettingSwitch(text("回到桌面时切换下一张", "Switch on return to home"), returnSwitch) { returnSwitch = it }
                SettingChoiceRow(
                    text("轮播顺序", "Rotation order"),
                    when (order) {
                        WallpaperOrder.InOrder -> text("顺序播放", "In order")
                        WallpaperOrder.TrueRandom -> text("完全随机", "True random")
                        WallpaperOrder.Shuffle -> text("洗牌后播放", "Shuffle then play")
                    }
                ) { dialog = WallpaperSettingDialog.Order }
                if (!isVideo) {
                    SettingSwitch(text("自动适配图片占用", "Automatically fit image usage"), autoAdjustImage) { autoAdjustImage = it }
                    SettingChoiceRow(
                        text("轮播频率", "Rotation frequency"),
                        if (returnSwitch) text("回桌即切换", "On return home")
                        else frequencyLabel(frequency, customSeconds, english),
                        enabled = !returnSwitch
                    ) { dialog = WallpaperSettingDialog.Frequency }
                } else {
                    SettingSwitch(text("低功耗模式", "Low-power mode"), lowPower, enabled = !systemPowerSave) {
                        lowPower = it
                        if (it) {
                            dynamicBackground = false
                            allowBackground = false
                        }
                    }
                    SettingSwitch(text("后台播放与轮播", "Background playback and rotation"), dynamicBackground, enabled = !lowPower) {
                        dynamicBackground = it
                        if (it) allowBackground = true
                    }
                    SettingChoiceRow(text("声音播放", "Sound playback"), soundLabel(sound, english), enabled = dynamicBackground) { dialog = WallpaperSettingDialog.Sound }
                    Text(
                        text("动态壁纸音量：${(wallpaperVolume * 100).toInt()}%", "Live wallpaper volume: ${(wallpaperVolume * 100).toInt()}%"),
                        fontSize = 13.sp,
                        color = if (dynamicBackground && sound != WallpaperSound.Disabled) androidx.compose.material3.MaterialTheme.colorScheme.onSurface else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f)
                    )
                    val volumeActiveColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    val volumeInactiveColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                    val volumeEnabled = dynamicBackground && sound != WallpaperSound.Disabled
                    Slider(
                        value = wallpaperVolume,
                        onValueChange = { wallpaperVolume = it },
                        enabled = volumeEnabled,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        track = { sliderState ->
                            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                                val fraction = ((sliderState.value - sliderState.valueRange.start) /
                                    (sliderState.valueRange.endInclusive - sliderState.valueRange.start))
                                    .coerceIn(0f, 1f)
                                val centerY = size.height / 2f
                                val thumbCenter = size.width * fraction
                                drawLine(
                                    volumeActiveColor,
                                    androidx.compose.ui.geometry.Offset(0f, centerY),
                                    androidx.compose.ui.geometry.Offset(thumbCenter, centerY),
                                    strokeWidth = size.height,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                drawLine(
                                    volumeInactiveColor,
                                    androidx.compose.ui.geometry.Offset(thumbCenter, centerY),
                                    androidx.compose.ui.geometry.Offset(size.width, centerY),
                                    strokeWidth = size.height,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }
                        },
                        thumb = {
                            Box(
                                Modifier.size(12.dp)
                                    .clip(CircleShape)
                                    .background(volumeActiveColor, CircleShape)
                            )
                        },
                        colors = SliderDefaults.colors(
                            activeTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                            inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                            thumbColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledActiveTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledInactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledThumbColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { save(); onDismiss() }) { Text(text("应用", "Apply")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text("取消", "Cancel")) } }
    )

    when (dialog) {
        WallpaperSettingDialog.Span -> ChoiceDialog(
            text("壁纸范围", "Wallpaper span"),
            listOf(text("单屏宽度", "Single screen"), text("跨屏宽度", "Across screens")),
            if (spanMode == "single") 0 else 1,
            { spanMode = if (it == 0) "single" else "scrolling"; dialog = null },
            { dialog = null }
        )
        WallpaperSettingDialog.Order -> ChoiceDialog(
            text("轮播顺序", "Rotation order"),
            listOf(text("顺序播放", "In order"), text("完全随机", "True random"), text("洗牌后播放", "Shuffle then play")),
            order.ordinal,
            { order = WallpaperOrder.entries[it]; dialog = null },
            { dialog = null }
        )
        WallpaperSettingDialog.Frequency -> FrequencyDialog(
            english, frequency, customSeconds,
            { nextFrequency, nextSeconds -> frequency = nextFrequency; customSeconds = nextSeconds; dialog = null },
            { dialog = null }
        )
        WallpaperSettingDialog.Sound -> ChoiceDialog(
            text("声音播放", "Sound playback"),
            listOf(text("禁用声音", "Disabled"), text("仅前台播放", "Foreground only"), text("后台播放，不抢占音频", "Background, no audio focus"), text("后台播放，优先占用音频", "Background, audio focus")),
            sound.ordinal,
            { sound = WallpaperSound.entries[it]; dialog = null },
            { dialog = null }
        )
        null -> Unit
    }
}

@Composable
private fun RowScope.TypeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).heightIn(min = 40.dp).then(
            if (selected) Modifier.shadow(2.dp, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
            else Modifier
        ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.onSurface else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SettingChoiceRow(label: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = if (enabled) androidx.compose.material3.MaterialTheme.colorScheme.onSurface else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f)
        )
        Text(
            value,
            modifier = Modifier.padding(start = 12.dp),
            fontSize = 12.sp,
            color = if (enabled) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f),
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}

private fun frequencyLabel(frequency: String, customSeconds: String, english: Boolean): String = when (frequency) {
    "custom" -> if (english) "$customSeconds s" else "${customSeconds}秒"
    "60" -> if (english) "1 min" else "1分钟"
    else -> if (english) "$frequency s" else "${frequency}秒"
}

private fun soundLabel(sound: WallpaperSound, english: Boolean): String = when (sound) {
    WallpaperSound.Disabled -> if (english) "Disabled" else "禁用声音"
    WallpaperSound.ForegroundOnly -> if (english) "Foreground only" else "仅前台播放"
    WallpaperSound.BackgroundNoFocus -> if (english) "Background, no audio focus" else "后台播放，不抢占音频"
    WallpaperSound.BackgroundWithFocus -> if (english) "Background, audio focus" else "后台播放，优先占用音频"
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    VaultWheelChoiceSheet(
        title = title,
        options = options,
        selected = options.getOrNull(selectedIndex) ?: options.firstOrNull().orEmpty(),
        onDismiss = onDismiss,
        onApply = { option ->
            options.indexOf(option).takeIf { it >= 0 }?.let(onSelected)
        }
    )
}

@Composable
private fun FrequencyDialog(
    english: Boolean,
    frequency: String,
    customSeconds: String,
    onApply: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(frequency) }
    var seconds by remember { mutableStateOf(customSeconds) }
    var draftSeconds by remember { mutableStateOf(customSeconds) }
    var showCustomSeconds by remember { mutableStateOf(false) }
    val frequencies = listOf("1", "3", "5", "10", "30", "60", "custom")
    val text = { zh: String, en: String -> if (english) en else zh }
    val options = frequencies.map { value ->
        when (value) {
            "custom" -> text("自定义秒数", "Custom seconds")
            "60" -> text("1分钟", "1 min")
            else -> if (english) "$value s" else "${value}秒"
        }
    }
    if (!showCustomSeconds) {
        VaultWheelChoiceSheet(
            title = text("轮播频率", "Rotation frequency"),
            options = options,
            selected = options.getOrNull(frequencies.indexOf(selected)) ?: options.first(),
            onDismiss = onDismiss,
            onApply = { option ->
                val index = options.indexOf(option)
                selected = frequencies[index]
                if (selected == "custom") {
                    draftSeconds = seconds
                    showCustomSeconds = true
                } else {
                    onApply(selected, seconds)
                }
            }
        )
    }
    if (showCustomSeconds) {
        VaultTextInputSheet(
            title = text("自定义轮播时间", "Custom rotation time"),
            value = draftSeconds,
            onValueChange = { draftSeconds = it.filter(Char::isDigit).take(4) },
            label = text("秒数（1-3600）", "Seconds (1-3600)"),
            confirmLabel = text("应用", "Apply"),
            confirmEnabled = draftSeconds.toLongOrNull()?.let { it in 1L..3600L } == true,
            onDismiss = { showCustomSeconds = false },
            onConfirm = {
                seconds = draftSeconds
                onApply("custom", seconds)
            }
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp, color = if (enabled) androidx.compose.material3.MaterialTheme.colorScheme.onSurface else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
