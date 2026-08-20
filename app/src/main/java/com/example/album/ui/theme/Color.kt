package com.example.album.ui.theme

import androidx.compose.ui.graphics.Color

val VaultGreen = Color(0xFF00E673)
val VaultGreenDark = Color(0xFF19E67A)
val VaultBlue = Color(0xFF3D8BFF)
val VaultTeal = Color(0xFF00C7BE)
val VaultOrange = Color(0xFFFF9F0A)
val VaultCoral = Color(0xFFFF453A)
val VaultViolet = Color(0xFFAF52DE)
val VaultBackground = Color(0xFFFFFFFF)
val VaultSurface = Color(0xFFFFFFFF)
val VaultInk = Color(0xFF202624)
val VaultMuted = Color(0xFF717975)
val VaultDarkBackground = Color(0xFF121715)
val VaultDarkSurface = Color(0xFF1B211E)

enum class ThemeAccent(val label: String, val storedValue: String, val color: Color) {
    Green("荧光绿", "#00E673", VaultGreen),
    Blue("明亮蓝", "#3D8BFF", VaultBlue),
    Teal("青绿色", "#00C7BE", VaultTeal),
    Orange("活力橙", "#FF9F0A", VaultOrange),
    Coral("珊瑚红", "#FF453A", VaultCoral),
    Violet("紫罗兰", "#AF52DE", VaultViolet);

    companion object {
        fun fromStored(value: String?): ThemeAccent = entries.firstOrNull {
            it.storedValue.equals(value, ignoreCase = true)
        } ?: Green
    }
}
