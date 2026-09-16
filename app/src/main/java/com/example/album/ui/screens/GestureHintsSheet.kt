package com.example.album.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText
import com.example.album.ui.components.VaultBottomSheet
import com.example.album.ui.components.VaultSheetApplyButton

/**
 * One-time list of the gestures that are not visible in the UI. Most of what
 * the app can do is hidden behind a long press or a two-finger gesture, so it
 * is spelled out once instead of leaving users to find it.
 */
@Composable
fun GestureHintsSheet(onDismiss: () -> Unit) {
    val english = LocalAppEnglish.current
    val hints = listOf(
        "长按图片或文件夹进入多选；选好后继续拖动可批量选择" to
            "Long-press a photo or folder to start selecting; keep dragging to add more",
        "长按底栏图标或工具箱条目可以拖动排序（可在设置里关闭）" to
            "Long-press a bottom-bar icon or a Tools entry to reorder it (can be turned off in Settings)",
        "浏览图片时单击切换全屏，双指可缩放" to
            "In the viewer a single tap toggles full screen, and two fingers zoom",
        "播放视频时上下滑动调亮度（左）/音量（右），左右滑动快进快退" to
            "In the player, drag up/down on the left for brightness and on the right for volume; drag sideways to seek",
        "点当前页的底栏图标回到顶部；已经在顶部时再点一次刷新" to
            "Tap the current page's bottom-bar icon to jump to the top; tap again while at the top to refresh"
    )
    VaultBottomSheet(title = appText("使用提示", english), onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            hints.forEach { (zh, en) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("·", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                    Text(
                        if (english) en else zh,
                        modifier = Modifier.width(300.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        VaultSheetApplyButton(label = appText("知道了", english)) { onDismiss() }
    }
}
