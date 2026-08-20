package com.example.album.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color

private fun darkScheme(accent: Color, background: Color, surface: Color, onSurface: Color, onSurfaceVariant: Color) = darkColorScheme(
    primary = accent,
    background = background,
    surface = surface,
    onBackground = onSurface,
    onSurface = onSurface,
    onSurfaceVariant = onSurfaceVariant
)

private fun lightScheme(accent: Color, background: Color, surface: Color, onSurface: Color, onSurfaceVariant: Color) = lightColorScheme(
    primary = accent,
    background = background,
    surface = surface,
    onBackground = onSurface,
    onSurface = onSurface,
    onSurfaceVariant = onSurfaceVariant,
    surfaceVariant = Color(0xFFF4F5F4),
    surfaceContainer = VaultSurface,
    surfaceContainerLow = VaultSurface,
    surfaceContainerHigh = VaultSurface,
    surfaceContainerHighest = Color(0xFFF4F5F4),
    outline = Color(0xFFDDE1DE)
)

@Composable
fun AlbumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color = VaultGreen,
    content: @Composable () -> Unit
) {
    val duration = 200
    val animatedAccent = animateColorAsState(accent, tween(duration), label = "theme-accent").value
    val background = animateColorAsState(if (darkTheme) VaultDarkBackground else VaultBackground, tween(duration), label = "theme-background").value
    val surface = animateColorAsState(if (darkTheme) VaultDarkSurface else VaultSurface, tween(duration), label = "theme-surface").value
    val onSurface = animateColorAsState(if (darkTheme) Color(0xFFF2F6F3) else VaultInk, tween(duration), label = "theme-on-surface").value
    val onSurfaceVariant = animateColorAsState(if (darkTheme) Color(0xFFADB7B1) else VaultMuted, tween(duration), label = "theme-muted").value
    MaterialTheme(
        colorScheme = if (darkTheme) {
            darkScheme(animatedAccent, background, surface, onSurface, onSurfaceVariant)
        } else {
            lightScheme(animatedAccent, background, surface, onSurface, onSurfaceVariant)
        },
        typography = Typography
    ) {
        CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)
    }
}
