@file:Suppress("UnsafeOptInUsageError")

package com.example.album.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Floating video window drawn by the app itself through the
 * "display over other apps" permission. Unlike picture-in-picture the system
 * does not wrap it in its own controls or gesture layer, so the window can
 * carry exactly the buttons the player needs.
 */
internal class OverlayMiniWindow(
    private val context: Context,
    private val player: ExoPlayer,
    private val onRestore: () -> Unit,
    private val onClose: () -> Unit,
    private val onTogglePlay: () -> Unit,
    private val onSeekBack: () -> Unit,
    private val onSeekForward: () -> Unit,
    private val isPlaying: () -> Boolean,
    private val onVisibilityChanged: (Boolean) -> Unit = {}
) {
    companion object {
        fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

        private const val EDGE_LEFT = 1
        private const val EDGE_TOP = 2
        private const val EDGE_RIGHT = 4
        private const val EDGE_BOTTOM = 8

        /** How far from a border the finger counts as "grab the border". */
        private const val BORDER_DP = 30f

        /** Controls hide themselves after this long, like the in-app player. */
        private const val CONTROLS_TIMEOUT_MS = 3_000L
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var playPauseButton: ImageButton? = null
    private var contentView: PlayerView? = null
    private var buttonViews: List<View> = emptyList()
    private val hideHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideControls = Runnable { setControlsVisible(false) }
    private var controlsVisible = true
    private val configurationCallback = object : android.content.ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
            // Rotating the device (which is what happens when the app goes to
            // the background in landscape) changes the screen bounds; without
            // re-clamping, the window ends up outside the visible area.
            clampToScreen()
        }

        override fun onLowMemory() = Unit
    }

    val isShowing: Boolean get() = root != null

    fun show() {
        if (root != null) return
        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val width = min((screenWidth * 0.62f).roundToInt(), (320 * density).roundToInt())
        val height = (width * 9f / 16f).roundToInt()

        val container = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = 10f * density
                setColor(Color.BLACK)
            }
            clipToOutline = true
        }
        val video = PlayerView(context).apply {
            useController = false
            this.player = this@OverlayMiniWindow.player
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(video)
        contentView = video

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                // The transport row sits a little below the vertical centre.
                topMargin = (28 * density).roundToInt()
            }
        }
        val rewind = overlayButton(com.example.album.R.drawable.ic_mw_rewind, density) { onSeekBack() }
        val playPause = overlayButton(com.example.album.R.drawable.ic_mw_pause, density) {
            onTogglePlay()
            playPauseButton?.setImageResource(
                if (isPlaying()) com.example.album.R.drawable.ic_mw_pause else com.example.album.R.drawable.ic_mw_play
            )
        }
        val forward = overlayButton(com.example.album.R.drawable.ic_mw_forward, density) { onSeekForward() }
        playPauseButton = playPause
        listOf(rewind, playPause, forward).forEach { controls.addView(it) }
        container.addView(controls)
        // Back to full screen sits in the top-left corner, close in the
        // top-right; the transport controls stay in the middle.
        val restore = overlayButton(
            com.example.album.R.drawable.ic_mw_fullscreen,
            density,
            gravity = Gravity.TOP or Gravity.START
        ) {
            dismiss()
            onRestore()
        }
        val close = overlayButton(
            com.example.album.R.drawable.ic_mw_close,
            density,
            gravity = Gravity.TOP or Gravity.END
        ) {
            dismiss()
            onClose()
        }
        container.addView(restore)
        container.addView(close)
        buttonViews = listOf(restore, close, controls)

        container.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            private var startWindowX = 0
            private var startWindowY = 0
            private var startWidth = 0
            private var startHeight = 0
            private var edges = 0
            private var moved = false
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val params = layoutParams ?: return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        moved = false
                        startWindowX = params.x
                        startWindowY = params.y
                        startWidth = params.width
                        startHeight = params.height
                        // Grabbing a border resizes, grabbing the middle moves.
                        edges = touchedEdges(event.rawX, event.rawY, params, density)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - startX)
                        val dy = (event.rawY - startY)
                        if (abs(dx) > 8f * density || abs(dy) > 8f * density) moved = true
                        if (edges != 0) {
                            applyResize(
                                params = params,
                                edges = edges,
                                dx = dx,
                                dy = dy,
                                density = density,
                                startRawX = startX,
                                startRawY = startY,
                                startWindowX = startWindowX,
                                startWindowY = startWindowY,
                                startWidth = startWidth,
                                startHeight = startHeight
                            )
                        } else {
                            val screenHeight = context.resources.displayMetrics.heightPixels
                            params.x = (startWindowX + dx).roundToInt()
                                .coerceIn(-params.width / 3, screenWidth - params.width / 3)
                            params.y = (startWindowY + dy).roundToInt()
                                .coerceIn(0, (screenHeight - params.height / 3).coerceAtLeast(0))
                        }
                        runCatching { windowManager.updateViewLayout(view, params) }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        edges = 0
                        // A tap on the empty area toggles the controls, the
                        // same way it does inside the app's player.
                        if (!moved) setControlsVisible(!controlsVisible) else touchControls()
                        // Overlay windows are not reachable by the usual
                        // accessibility path, so report the gesture as a click
                        // to keep the touch handling observable.
                        view.performClick()
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        edges = 0
                        return true
                    }
                }
                return false
            }
        })

        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - width - 16 * density).roundToInt().coerceAtLeast(0)
            y = (context.resources.displayMetrics.heightPixels * 0.25f).roundToInt()
        }
        layoutParams = params
        runCatching { windowManager.addView(container, params) }
        root = container
        runCatching { context.registerComponentCallbacks(configurationCallback) }
        setControlsVisible(true)
        onVisibilityChanged(true)
    }

    fun dismiss() {
        val view = root ?: return
        hideHandler.removeCallbacks(hideControls)
        runCatching { context.unregisterComponentCallbacks(configurationCallback) }
        runCatching { windowManager.removeView(view) }
        contentView?.player = null
        root = null
        contentView = null
        playPauseButton = null
        buttonViews = emptyList()
        onVisibilityChanged(false)
    }

    /**
     * Shows or hides the controls, exactly like the in-app player: a tap on the
     * video toggles them and they fade away after a few seconds of no use.
     */
    private fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        buttonViews.forEach { it.visibility = if (visible) View.VISIBLE else View.GONE }
        hideHandler.removeCallbacks(hideControls)
        if (visible) hideHandler.postDelayed(hideControls, CONTROLS_TIMEOUT_MS)
    }

    /** Keeps the controls on screen while the user is interacting with them. */
    private fun touchControls() {
        if (controlsVisible) setControlsVisible(true)
    }

    fun syncPlayState() {
        playPauseButton?.setImageResource(
            if (isPlaying()) com.example.album.R.drawable.ic_mw_pause else com.example.album.R.drawable.ic_mw_play
        )
    }

    /**
     * A translucent icon button with no filled background: the video stays
     * visible behind the controls.
     */
    private fun overlayButton(
        iconRes: Int,
        density: Float,
        gravity: Int = 0,
        onClick: () -> Unit
    ): ImageButton = ImageButton(context).apply {
        setImageResource(iconRes)
        setBackgroundColor(Color.TRANSPARENT)
        setColorFilter(Color.WHITE)
        imageAlpha = 255
        // Every button looks the same: same size, same white, no background and
        // no state-list elevation.
        val size = (46 * density).roundToInt()
        stateListAnimator = null
        elevation = 0f
        isFocusable = false
        setPadding(0, 0, 0, 0)
        layoutParams = if (gravity == 0) {
            LinearLayout.LayoutParams(size, size).apply {
                marginStart = (6 * density).roundToInt()
                marginEnd = (6 * density).roundToInt()
            }
        } else {
            FrameLayout.LayoutParams(size, size, gravity).apply {
                val margin = (2 * density).roundToInt()
                if (gravity and Gravity.START != 0) marginStart = margin
                if (gravity and Gravity.END != 0) marginEnd = margin
                topMargin = margin
            }
        }
        setOnClickListener {
            touchControls()
            onClick()
        }
    }

    /** Which borders the finger grabbed, as a bitmask (0 = the middle). */
    private fun touchedEdges(
        rawX: Float,
        rawY: Float,
        params: WindowManager.LayoutParams,
        density: Float
    ): Int {
        val band = BORDER_DP * density
        var edges = 0
        if (rawX <= params.x + band) edges = edges or EDGE_LEFT
        else if (rawX >= params.x + params.width - band) edges = edges or EDGE_RIGHT
        if (rawY <= params.y + band) edges = edges or EDGE_TOP
        else if (rawY >= params.y + params.height - band) edges = edges or EDGE_BOTTOM
        return edges
    }

    /**
     * Resizes the window while keeping the 16:9 aspect ratio and the dragged
     * edge pinned, so the window grows away from the finger.
     */
    private fun applyResize(
        params: WindowManager.LayoutParams,
        edges: Int,
        dx: Float,
        dy: Float,
        density: Float,
        startRawX: Float,
        startRawY: Float,
        startWindowX: Int,
        startWindowY: Int,
        startWidth: Int,
        startHeight: Int
    ) {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val minWidth = (160 * density).roundToInt()
        val maxWidth = (screenWidth - 16 * density).roundToInt().coerceAtLeast(minWidth)
        val centerX = startWindowX + startWidth / 2f
        val centerY = startWindowY + startHeight / 2f
        // Dragging away from the centre grows the window, dragging towards it
        // shrinks; that holds for every border and corner, which fixed the case
        // where both directions shrank the window.
        val horizontalGrowth = if (startRawX < centerX) -dx else dx
        val verticalGrowth = if (startRawY < centerY) -dy else dy
        val horizontalEdge = edges and (EDGE_LEFT or EDGE_RIGHT) != 0
        val verticalEdge = edges and (EDGE_TOP or EDGE_BOTTOM) != 0
        val growth = when {
            // Corners move on both axes: average the projections so the window
            // scales smoothly instead of snapping to one axis.
            horizontalEdge && verticalEdge -> (horizontalGrowth + verticalGrowth * 16f / 9f) / 2f
            horizontalEdge -> horizontalGrowth
            else -> verticalGrowth * 16f / 9f
        }
        val newWidth = (startWidth + growth).roundToInt().coerceIn(minWidth, maxWidth)
        val newHeight = (newWidth * 9f / 16f).roundToInt()
        // Keep the opposite side pinned, so the window grows away from the
        // finger instead of sliding around.
        if (startRawX < centerX) params.x = startWindowX + (startWidth - newWidth)
        if (startRawY < centerY) params.y = startWindowY + (startHeight - newHeight)
        params.width = newWidth
        params.height = newHeight
    }

    /** Keeps the window inside the current screen bounds. */
    private fun clampToScreen() {
        val view = root ?: return
        val params = layoutParams ?: return
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val maxWidth = (metrics.widthPixels - 16 * density).roundToInt()
            .coerceAtLeast((160 * density).roundToInt())
        if (params.width > maxWidth) {
            params.width = maxWidth
            params.height = (maxWidth * 9f / 16f).roundToInt()
        }
        params.x = params.x.coerceIn(0, (metrics.widthPixels - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (metrics.heightPixels - params.height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, params) }
    }
}
