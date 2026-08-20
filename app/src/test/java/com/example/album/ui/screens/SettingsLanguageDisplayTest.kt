package com.example.album.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLanguageDisplayTest {
    @Test
    fun languageNamesAlwaysUseTheirOwnLanguage() {
        listOf(false, true).forEach { englishUi ->
            assertEquals("简体中文", settingsDisplay("语言", "简体中文", englishUi))
            assertEquals("English", settingsDisplay("语言", "English", englishUi))
        }
    }

    @Test
    fun otherChoicesStillFollowTheCurrentUiLanguage() {
        assertEquals("自动", settingsDisplay("主题模式", "自动", english = false))
        assertEquals("System", settingsDisplay("主题模式", "自动", english = true))
        assertEquals("Dark", settingsDisplay("主题模式", "深色", english = true))
    }
}
