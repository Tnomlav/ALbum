package com.example.album.ui

internal enum class MainMenuAction {
    Scan,
    AddLocalFolder,
    Columns,
    Layout,
    Sort,
    JumpToDate,
    Select,
    ExcludeFolder;

    companion object {
        fun fromLabel(label: String): MainMenuAction? = when (label) {
            "\u626b\u63cf\u5237\u65b0", "Scan" -> Scan
            "\u6dfb\u52a0\u672c\u5730\u6587\u4ef6\u5939", "Add local folder" -> AddLocalFolder
            "\u5217\u6570", "Columns" -> Columns
            "\u6392\u5e03\u65b9\u5f0f", "Layout" -> Layout
            "\u6392\u5e8f\u65b9\u5f0f", "Sort" -> Sort
            "\u8df3\u8f6c\u65e5\u671f", "Jump to date" -> JumpToDate
            "\u8fdb\u5165\u591a\u9009", "Select" -> Select
            "\u6392\u9664\u6587\u4ef6\u5939", "Exclude folder" -> ExcludeFolder
            else -> null
        }
    }
}
