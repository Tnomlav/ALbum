package com.example.album.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.graphics.RectF
import androidx.media3.ui.PlayerView

/** Lets Compose controls above the video receive taps instead of the native view. */
class VideoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : PlayerView(context, attrs) {
    companion object {
        const val HIT_BACK = 1
        const val HIT_FAVORITE = 2
        const val HIT_SPEED = 3
        const val HIT_SHARE = 4
        const val HIT_PREVIOUS = 5
        const val HIT_PLAY_PAUSE = 6
        const val HIT_NEXT = 7
        const val HIT_ORIENTATION = 8
    }

    var controlsHitEnabled: Boolean = false
    var onOverlayBack: (() -> Unit)? = null
    var onOverlayFavorite: (() -> Unit)? = null
    var onOverlaySpeed: (() -> Unit)? = null
    var onOverlayShare: (() -> Unit)? = null
    var onOverlayPrevious: (() -> Unit)? = null
    var onOverlayNext: (() -> Unit)? = null
    var onOverlayPlayPause: (() -> Unit)? = null
    var onOverlayOrientation: (() -> Unit)? = null
    private val overlayRegions = mutableMapOf<Int, RectF>()
    private var activeHit: Int = 0

    fun setOverlayRegion(action: Int, boundsInWindow: RectF) {
        overlayRegions[action] = RectF(boundsInWindow)
    }

    fun clearOverlayRegions() = overlayRegions.clear()

    private fun hitAt(event: MotionEvent): Int {
        // Compose reports bounds in window coordinates, while MotionEvent x/y
        // are local to this View. Convert the local pointer to the same window
        // space at hit time so insets and orientation changes cannot offset it.
        val location = IntArray(2)
        getLocationInWindow(location)
        val x = event.x + location[0]
        val y = event.y + location[1]
        return overlayRegions.entries.firstOrNull { it.value.contains(x, y) }?.key ?: 0
    }

    private fun invokeHit(action: Int) {
        when (action) {
            HIT_BACK -> onOverlayBack?.invoke()
            HIT_FAVORITE -> onOverlayFavorite?.invoke()
            HIT_SPEED -> onOverlaySpeed?.invoke()
            HIT_SHARE -> onOverlayShare?.invoke()
            HIT_PREVIOUS -> onOverlayPrevious?.invoke()
            HIT_PLAY_PAUSE -> onOverlayPlayPause?.invoke()
            HIT_NEXT -> onOverlayNext?.invoke()
            HIT_ORIENTATION -> onOverlayOrientation?.invoke()
        }
    }

    init {
        // The video surface is render-only. If PlayerView dispatches into its
        // TextureView child, that native child wins the gesture before the
        // Compose controls above it can receive the event.
        isClickable = false
        isFocusable = false
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = super.dispatchTouchEvent(event)

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!controlsHitEnabled) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) activeHit = hitAt(event)
        return activeHit != 0
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!controlsHitEnabled || activeHit == 0) return false
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            invokeHit(activeHit)
            activeHit = 0
        } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            activeHit = 0
        }
        return true
    }
}
