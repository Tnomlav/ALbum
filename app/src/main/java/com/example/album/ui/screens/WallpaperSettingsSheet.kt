package com.example.album.ui.screens

import android.content.SharedPreferences
import android.os.PowerManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.album.ui.LocalAppEnglish

private enum class WallpaperOrder { InOrder, TrueRandom, Shuffle }
private enum class WallpaperSound { Disabled, ForegroundOnly, BackgroundNoFocus, BackgroundWithFocus }

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
                Text(text("类型", "Type"), fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeChoice(text("静态", "Static"), !isVideo) { isVideo = false }
                    TypeChoice(text("动态", "Live"), isVideo) { isVideo = true }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SettingSwitch(text("同步锁屏", "Sync lock screen"), syncLock) { syncLock = it }
                SettingSwitch(text("后台运行", "Run in background"), allowBackground) { allowBackground = it }
                Text(text("显示范围", "Display span"), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                ChoiceRow(text("单屏", "Single screen"), spanMode == "single") { spanMode = "single" }
                ChoiceRow(text("跨屏", "Across screens"), spanMode == "scrolling") { spanMode = "scrolling" }
                Text(text("回桌切换", "Return-home switching"), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                SettingSwitch(text("回桌时切换下一项", "Switch on return home"), returnSwitch) { returnSwitch = it }
                Text(text("轮播顺序", "Rotation order"), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                ChoiceRow(text("顺序", "In order"), order == WallpaperOrder.InOrder) { order = WallpaperOrder.InOrder }
                ChoiceRow(text("真随机", "True random"), order == WallpaperOrder.TrueRandom) { order = WallpaperOrder.TrueRandom }
                ChoiceRow(text("洗牌式随机", "Shuffled random"), order == WallpaperOrder.Shuffle) { order = WallpaperOrder.Shuffle }
                if (!isVideo) {
                    SettingSwitch(text("自动调整图片占用", "Auto-adjust image usage"), autoAdjustImage) { autoAdjustImage = it }
                    Text(text("轮播频率", "Rotation frequency"), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    val frequencies = listOf("1", "3", "5", "10", "30", "60", "custom")
                    frequencies.forEach { value ->
                        val label = when (value) {
                            "custom" -> text("自定义", "Custom")
                            "60" -> "1 min"
                            else -> "$value s"
                        }
                        ChoiceRow(label, frequency == value, enabled = !returnSwitch) { frequency = value }
                    }
                    if (frequency == "custom") {
                        OutlinedTextField(
                            value = customSeconds,
                            onValueChange = { customSeconds = it.filter(Char::isDigit).take(5) },
                            enabled = !returnSwitch,
                            label = { Text(text("秒数", "Seconds")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        )
                    }
                } else {
                    SettingSwitch(text("低功耗模式", "Low-power mode"), lowPower, enabled = !systemPowerSave) {
                        lowPower = it
                        if (it) {
                            dynamicBackground = false
                            allowBackground = false
                        }
                    }
                    Text(text("后台播放", "Background playback"), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    SettingSwitch(text("后台播放与轮播", "Background playback and rotation"), dynamicBackground, enabled = !lowPower) {
                        dynamicBackground = it
                        if (it) allowBackground = true
                    }
                    Text(text("声音", "Sound"), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    ChoiceRow(text("禁用", "Disabled"), sound == WallpaperSound.Disabled) { sound = WallpaperSound.Disabled }
                    ChoiceRow(text("仅前台", "Foreground only"), sound == WallpaperSound.ForegroundOnly) { sound = WallpaperSound.ForegroundOnly }
                    ChoiceRow(text("后台播放（不抢音频）", "Background (no audio focus)"), sound == WallpaperSound.BackgroundNoFocus, enabled = dynamicBackground) { sound = WallpaperSound.BackgroundNoFocus }
                    ChoiceRow(text("后台播放（抢占音频）", "Background (audio focus)"), sound == WallpaperSound.BackgroundWithFocus, enabled = dynamicBackground) { sound = WallpaperSound.BackgroundWithFocus }
                }
            }
        },
        confirmButton = { TextButton(onClick = { save(); onDismiss() }) { Text(text("应用", "Apply")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text("取消", "Cancel")) } }
    )
}

@Composable
private fun TypeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 10.dp), color = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = if (enabled) onClick else null, enabled = enabled)
        Text(label, color = if (enabled) androidx.compose.material3.MaterialTheme.colorScheme.onSurface else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f), fontSize = 13.sp)
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp, color = if (enabled) androidx.compose.material3.MaterialTheme.colorScheme.onSurface else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
