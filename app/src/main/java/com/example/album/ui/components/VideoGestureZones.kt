package com.example.album.ui.components

/**
 * Horizontal touch regions for the vertical brightness/volume drag gesture.
 * Returns 2 for brightness, 3 for volume and 0 for the neutral middle area.
 */
internal fun verticalGestureZone(fraction: Float, ratio: String): Int = when (ratio) {
    "1:1:1" -> when {
        fraction < 1f / 3f -> 2
        fraction > 2f / 3f -> 3
        else -> 0
    }
    "1:2:1" -> when {
        fraction < .25f -> 2
        fraction > .75f -> 3
        else -> 0
    }
    else -> if (fraction < .5f) 2 else 3
}

/**
 * Horizontal touch regions for double-tap seeking. Returns -1 for rewind,
 * 0 for pause and 1 for fast-forward.
 */
internal fun doubleTapZone(fraction: Float, ratio: String): Int = when (ratio) {
    "1:2:1" -> when {
        fraction < .25f -> -1
        fraction > .75f -> 1
        else -> 0
    }
    "1:0:1" -> if (fraction < .5f) -1 else 1
    else -> when {
        fraction < 1f / 3f -> -1
        fraction > 2f / 3f -> 1
        else -> 0
    }
}
