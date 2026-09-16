package com.example.album.ui.screens

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.components.VaultBottomSheet
import com.example.album.ui.components.VaultSheetApplyButton

private enum class SlideshowSettingDialog { Interval, Animation }

/**
 * Slideshow options in the shared bottom-sheet shape: the same frame, rows and
 * apply pill as every other sheet in the app.
 */
@Composable
fun SlideshowSettingsSheet(
    preferences: SharedPreferences,
    onDismiss: () -> Unit
) {
    val english = LocalAppEnglish.current
    fun text(zh: String, en: String) = if (english) en else zh
    var interval by remember { mutableStateOf(preferences.getString("slideshow_interval", "3秒") ?: "3秒") }
    var animation by remember { mutableStateOf(preferences.getString("slideshow_animation", "自然") ?: "自然") }
    var random by remember { mutableStateOf(preferences.getBoolean("random_slideshow", false)) }
    var dialog by remember { mutableStateOf<SlideshowSettingDialog?>(null) }

    fun save() {
        preferences.edit()
            .putString("slideshow_interval", interval)
            .putString("slideshow_animation", animation)
            .putBoolean("random_slideshow", random)
            .apply()
    }

    VaultBottomSheet(title = text("幻灯片设置", "Slideshow settings"), onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SettingChoiceRow(
                text("幻灯片播放间隔", "Slideshow interval"),
                interval
            ) { dialog = SlideshowSettingDialog.Interval }
            SettingChoiceRow(
                text("幻灯片播放动画", "Slideshow animation"),
                text(animation, animation)
            ) { dialog = SlideshowSettingDialog.Animation }
            SettingSwitch(text("幻灯片随机播放", "Shuffle slideshow"), random) {
                random = it
                save()
            }
        }
        VaultSheetApplyButton {
            save()
            onDismiss()
        }
    }

    when (dialog) {
        SlideshowSettingDialog.Interval -> {
            val options = (1..10).map { "${it}秒" }
            ChoiceDialog(
                text("幻灯片播放间隔", "Slideshow interval"),
                options,
                options.indexOf(interval).coerceAtLeast(0),
                { interval = options[it]; save(); dialog = null },
                { dialog = null }
            )
        }
        SlideshowSettingDialog.Animation -> {
            val options = listOf("自然", "淡入淡出", "滑动")
            ChoiceDialog(
                text("幻灯片播放动画", "Slideshow animation"),
                options,
                options.indexOf(animation).coerceAtLeast(0),
                { animation = options[it]; save(); dialog = null },
                { dialog = null }
            )
        }
        null -> Unit
    }
}
