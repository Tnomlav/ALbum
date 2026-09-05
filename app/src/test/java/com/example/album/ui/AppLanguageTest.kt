package com.example.album.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun keyUiActionsHaveEnglishLabels() {
        assertEquals("Confirm", appText("确认", english = true))
        assertEquals("Search", appText("搜索", english = true))
        assertEquals("No matching folders", appText("没有找到相关文件夹", english = true))
        assertEquals("Fast-forward", appText("快进", english = true))
        assertEquals("Videos permission required", appText("需要视频访问权限", english = true))
    }

    @Test
    fun chineseUiRemainsUnchanged() {
        assertEquals("确认", appText("确认", english = false))
    }

    @Test
    fun seekHudUsesLocaleSpecificUnit() {
        assertEquals("快退 10秒", appSeekText("快退", 10_000L, english = false))
        assertEquals("Rewind 10s", appSeekText("快退", 10_000L, english = true))
        assertEquals("+10秒", appSeekDeltaText(10L, english = false))
        assertEquals("-10s", appSeekDeltaText(-10L, english = true))
    }
}
