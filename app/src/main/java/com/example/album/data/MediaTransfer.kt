package com.example.album.data

enum class TransferMode {
    Copy,
    Move
}

enum class ConflictPolicy(val label: String) {
    KeepBoth("保留两者"),
    Overwrite("覆盖"),
    Skip("跳过")
}

data class TransferResult(
    val item: MediaItem,
    val success: Boolean,
    val skipped: Boolean = false,
    val targetName: String? = null
)

data class TransferRequest(
    val items: List<MediaItem>,
    val mode: TransferMode
)
