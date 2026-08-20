package com.example.album.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.min

internal const val PrototypeDesignWidth = 412f
internal const val PrototypeDesignHeight = 900f

internal fun prototypeViewportScale(viewportWidthDp: Float, viewportHeightDp: Float): Float {
    if (!viewportWidthDp.isFinite() || !viewportHeightDp.isFinite()) return 1f
    return min(viewportWidthDp / PrototypeDesignWidth, viewportHeightDp / PrototypeDesignHeight)
        .coerceAtLeast(.01f)
}

/** Keeps layout, vectors, text and pointer input in the prototype's 412 x 900 coordinate space. */
@Composable
fun PrototypeViewport(
    useDeviceViewport: Boolean = false,
    content: @Composable () -> Unit
) {
    val deviceDensity = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        // The activity is portrait-locked, but the emulator can still provide
        // a landscape-sized window while the video canvas is rotated inside
        // it. In that case the fixed 412x900 design viewport would collapse
        // into a narrow centered strip. Use the real constraints for the
        // entire window so the player UI keeps the HTML proportions.
        val landscapeWindow = maxWidth > maxHeight
        val useFullWindow = useDeviceViewport || landscapeWindow
        val scale = if (useFullWindow) 1f else prototypeViewportScale(maxWidth.value, maxHeight.value)
        val designDensity = Density(
            density = deviceDensity.density * scale,
            fontScale = deviceDensity.fontScale
        )
        CompositionLocalProvider(LocalDensity provides designDensity) {
            Box(
                modifier = Modifier
                    .then(
                        if (useFullWindow) Modifier.fillMaxSize()
                        else Modifier.size(PrototypeDesignWidth.dp, PrototypeDesignHeight.dp)
                    )
                    .clipToBounds()
            ) {
                content()
            }
        }
    }
}
