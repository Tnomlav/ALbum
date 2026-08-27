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
    val targetName: String? = null,
    val movedDirectly: Boolean = false
)

data class TransferRequest(
    val items: List<MediaItem>,
    val mode: TransferMode
)

data class TransferTargetName(
    val name: String,
    val skipped: Boolean = false
)

internal fun resolveTransferTargetName(
    originalName: String,
    existingNames: Set<String>,
    conflictPolicy: ConflictPolicy
): TransferTargetName {
    if (originalName !in existingNames) return TransferTargetName(originalName)
    return when (conflictPolicy) {
        ConflictPolicy.Skip -> TransferTargetName(originalName, skipped = true)
        ConflictPolicy.Overwrite -> TransferTargetName(originalName)
        ConflictPolicy.KeepBoth -> {
            val dot = originalName.lastIndexOf('.')
            val base = if (dot > 0) originalName.substring(0, dot) else originalName
            val extension = if (dot > 0) originalName.substring(dot) else ""
            var index = 1
            var candidate: String
            do {
                candidate = "$base ($index)$extension"
                index++
            } while (candidate in existingNames)
            TransferTargetName(candidate)
        }
    }
}
