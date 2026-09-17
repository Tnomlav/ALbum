package com.example.album.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.example.album.data.MediaItem

internal val LocalMediaSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
internal val LocalMediaAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
internal val LocalActiveSharedMediaKey = staticCompositionLocalOf<String?> { null }

@Composable
internal fun Modifier.mediaSharedElement(item: MediaItem): Modifier {
    val sharedScope = LocalMediaSharedTransitionScope.current ?: return this
    val visibilityScope = LocalMediaAnimatedVisibilityScope.current
    val key = "media:${item.uri}"
    val activeKey = LocalActiveSharedMediaKey.current
    // Only the item the viewer is opening or closing takes part in a shared
    // transition. Sharing every tile made the album list and the folder grid
    // animate into each other, which kept the previous page composed (and
    // visible) while the new one appeared.
    if (activeKey != key) return this
    return with(sharedScope) {
        val state = rememberSharedContentState(key)
        val transform = BoundsTransform { _: Rect, _: Rect ->
            tween<Rect>(360, easing = CubicBezierEasing(.22f, .78f, .24f, 1f))
        }
        if (visibilityScope != null) {
            sharedElement(state, visibilityScope, boundsTransform = transform)
        } else {
            sharedElementWithCallerManagedVisibility(
                sharedContentState = state,
                visible = activeKey != key,
                boundsTransform = transform
            )
        }
    }
}
