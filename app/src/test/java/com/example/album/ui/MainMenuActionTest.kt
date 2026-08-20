package com.example.album.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainMenuActionTest {
    @Test
    fun everyMenuLabelMapsToAnAction() {
        val labels = mapOf(
            "\u626b\u63cf\u5237\u65b0" to MainMenuAction.Scan,
            "Scan" to MainMenuAction.Scan,
            "\u6dfb\u52a0\u672c\u5730\u6587\u4ef6\u5939" to MainMenuAction.AddLocalFolder,
            "Add local folder" to MainMenuAction.AddLocalFolder,
            "\u5217\u6570" to MainMenuAction.Columns,
            "Columns" to MainMenuAction.Columns,
            "\u6392\u5e03\u65b9\u5f0f" to MainMenuAction.Layout,
            "Layout" to MainMenuAction.Layout,
            "\u6392\u5e8f\u65b9\u5f0f" to MainMenuAction.Sort,
            "Sort" to MainMenuAction.Sort,
            "\u8df3\u8f6c\u65e5\u671f" to MainMenuAction.JumpToDate,
            "Jump to date" to MainMenuAction.JumpToDate,
            "\u8fdb\u5165\u591a\u9009" to MainMenuAction.Select,
            "Select" to MainMenuAction.Select,
            "\u6392\u9664\u6587\u4ef6\u5939" to MainMenuAction.ExcludeFolder,
            "Exclude folder" to MainMenuAction.ExcludeFolder
        )

        labels.forEach { (label, expected) ->
            assertEquals(label, expected, MainMenuAction.fromLabel(label))
        }
    }

    @Test
    fun unknownMenuLabelIsRejected() {
        assertNull(MainMenuAction.fromLabel("Unknown"))
    }
}
