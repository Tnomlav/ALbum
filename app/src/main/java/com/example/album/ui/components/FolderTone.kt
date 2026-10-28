package com.example.album.ui.components

import androidx.compose.ui.graphics.Color
import java.util.Locale

internal data class EmptyFolderTone(val background: Color, val icon: Color)

private data class EmptyFolderPalette(
    val lightBackground: Color,
    val lightIcon: Color,
    val darkBackground: Color,
    val darkIcon: Color
)

private val emptyFolderPalettes = listOf(
    EmptyFolderPalette(Color(0xFFF8ECEF), Color(0xFFB86F7C), Color(0xFF302528), Color(0xFFD49AA5)),
    EmptyFolderPalette(Color(0xFFF8F0E8), Color(0xFFB7835B), Color(0xFF2F2822), Color(0xFFD2A57A)),
    EmptyFolderPalette(Color(0xFFF5F2E7), Color(0xFFA18C54), Color(0xFF2D2B23), Color(0xFFC2B27D)),
    EmptyFolderPalette(Color(0xFFEAF3ED), Color(0xFF5F8C73), Color(0xFF222D27), Color(0xFF90BA9E)),
    EmptyFolderPalette(Color(0xFFE8F2F3), Color(0xFF578A91), Color(0xFF222C2D), Color(0xFF86B7BD)),
    EmptyFolderPalette(Color(0xFFEAF0F8), Color(0xFF607DA8), Color(0xFF232832), Color(0xFF94ADD6)),
    EmptyFolderPalette(Color(0xFFEEEDF7), Color(0xFF7B70A7), Color(0xFF292635), Color(0xFFADA2D7)),
    EmptyFolderPalette(Color(0xFFF3EDF3), Color(0xFF966A91), Color(0xFF2D252E), Color(0xFFC69BC1))
)

/**
 * Folder thumbnails use a stable hue derived from their name. The palettes are
 * deliberately low-saturation and close in lightness, similar to the tinted
 * icon backgrounds on the settings home page, while still telling folders
 * apart at a glance.
 */
internal fun emptyFolderTone(name: String, darkTheme: Boolean): EmptyFolderTone {
    val index = (name.lowercase(Locale.ROOT).hashCode() and Int.MAX_VALUE) % emptyFolderPalettes.size
    val palette = emptyFolderPalettes[index]
    return if (darkTheme) {
        EmptyFolderTone(palette.darkBackground, palette.darkIcon)
    } else {
        EmptyFolderTone(palette.lightBackground, palette.lightIcon)
    }
}
