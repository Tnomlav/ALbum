// The buttons are ordinary ImageButtons that keep their own performClick; the
// extra touch listener only forwards drags to the window's move/resize code.
@file:Suppress("UnsafeOptInUsageError", "ClickableViewAccessibility")

package com.example.album.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
        /** Size of the corner squares that resize the window. */
        private const val CORNER_DP = 44f

        /** Corner zones are this share of the window, on each side. */
        private const val CORNER_FRACTION = 0.4f

        /** Controls hide themselves after this long, like the in-app player. */
        private const val CONTROLS_TIMEOUT_MS = 3_000L
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var playPauseButton: ImageButton? = null
    private var contentView: PlayerView? = null
    private var buttonViews: List<View> = emptyList()
    /** The rewind / play / forward row, whose spacing follows the window size. */
    private var transportButtons: List<ImageButton> = emptyList()
    private var baseWindowWidth = 0
    // Gesture state, shared by the container and the buttons so a drag that
    // starts on a button still moves/resizes the window.
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var gestureStartWindowX = 0
    private var gestureStartWindowY = 0
    private var gestureStartWidth = 0
    private var gestureStartHeight = 0
    private var gestureEdges = 0
    private var gestureMoved = false
    /**
     * Set when a drag started on a button: the button must not also fire its
     * click on release, otherwise dragging the window by its top-right corner
     * closed the window instead of resizing it.
     */
    private var suppressNextClick = false
    private var buttonDragListener: View.OnTouchListener? = null
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
        val screenHeight = context.resources.displayMetrics.heightPixels
        // Base the starting size on the short side of the screen, so the window
        // is the same size whether it was opened in portrait or landscape.
        val width = min((min(screenWidth, screenHeight) * 0.62f).roundToInt(), (320 * density).roundToInt())
        val height = (width * 9f / 16f).roundToInt()

        val container = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = 10f * density
                setColor(Color.BLACK)
            }
            clipToOutline = true
        }
        val video = (
            android.view.LayoutInflater.from(context)
                .inflate(com.example.album.R.layout.mini_window_player, container, false) as PlayerView
            ).apply {
            useController = false
            this.player = this@OverlayMiniWindow.player
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // Belt and braces on top of the inflated texture view: the player
            // view clips itself to the window's rounded corners too.
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 10f * density)
                }
            }
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
        transportButtons = listOf(rewind, playPause, forward)
        baseWindowWidth = width
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

        // Dragging anywhere -- including from a button -- moves or resizes the
        // window. A tap that never moves still reaches the button's own click.
        fun beginGesture(source: View, event: MotionEvent): Boolean {
            val params = layoutParams ?: return false
            gestureStartX = event.rawX
            gestureStartY = event.rawY
            gestureMoved = false
            gestureStartWindowX = params.x
            gestureStartWindowY = params.y
            gestureStartWidth = params.width
            gestureStartHeight = params.height
            // Hit-testing uses coordinates inside the window. The window can be
            // positioned outside the app's own coordinate space (it is a
            // FLAG_LAYOUT_NO_LIMITS overlay), so screen coordinates and layout
            // params do not line up; `getLocationInWindow` plus the event's own
            // position is exact for whichever child the finger landed on.
            val location = IntArray(2)
            source.getLocationInWindow(location)
            // A shrunk window resizes from its corners like any other: dragging
            // its bottom-left corner has to pin the top-right there too. A tap
            // (no movement) still restores the size the window opened with.
            gestureEdges = touchedEdges(
                insideX = location[0] + event.x,
                insideY = location[1] + event.y,
                width = params.width,
                height = params.height,
                density = density
            )
            return true
        }

        fun continueGesture(view: View, event: MotionEvent): Boolean {
            val params = layoutParams ?: return false
            val dx = event.rawX - gestureStartX
            val dy = event.rawY - gestureStartY
            if (abs(dx) > 8f * density || abs(dy) > 8f * density) gestureMoved = true
            if (!gestureMoved) return true
            if (gestureEdges != 0) {
                applyResize(
                    params = params,
                    edges = gestureEdges,
                    dx = dx,
                    dy = dy,
                    density = density,
                    startWindowX = gestureStartWindowX,
                    startWindowY = gestureStartWindowY,
                    startWidth = gestureStartWidth,
                    startHeight = gestureStartHeight
                )
                updateTransportSpacing()
            } else {
                // Read the metrics here instead of reusing the ones captured when
                // the window was created: after a rotation they described the
                // other orientation and the window could be dragged off screen.
                val metrics = context.resources.displayMetrics
                val screenWidth = metrics.widthPixels
                val screenHeight = metrics.heightPixels
                params.x = (gestureStartWindowX + dx).roundToInt()
                    .coerceIn(-params.width / 3, screenWidth - params.width / 3)
                params.y = (gestureStartWindowY + dy).roundToInt()
                    .coerceIn(0, (screenHeight - params.height / 3).coerceAtLeast(0))
            }
            runCatching { windowManager.updateViewLayout(view, params) }
            return true
        }

        container.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> beginGesture(view, event)
                MotionEvent.ACTION_MOVE -> continueGesture(view, event)
                MotionEvent.ACTION_UP -> {
                    // Apply the finger's last position too: on a quick drag the
                    // UP event carries the final spot and the window otherwise
                    // stopped a few pixels short.
                    if (gestureEdges != 0 && gestureMoved) continueGesture(view, event)
                    val wasTap = !gestureMoved
                    gestureEdges = 0
                    // A tap on the empty area toggles the controls, the same way
                    // it does inside the app's player.
                    if (wasTap) setControlsVisible(!controlsVisible)
                    view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    gestureEdges = 0
                    true
                }
                else -> false
            }
        }
        // Buttons keep their click, but a drag that starts on one is handed to
        // the same gesture code (the zones used to overlap and the buttons ate
        // every drag).
        buttonDragListener = View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    suppressNextClick = false
                    beginGesture(view, event)
                    false
                }
                MotionEvent.ACTION_MOVE -> if (gestureMoved) continueGesture(view, event) else false
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (gestureEdges != 0 && gestureMoved) continueGesture(view, event)
                    gestureEdges = 0
                    if (gestureMoved) suppressNextClick = true
                    gestureMoved = false
                    false
                }
                else -> false
            }
        }
        buttonViews.filterIsInstance<ImageButton>().forEach { it.setOnTouchListener(buttonDragListener) }
        // The transport buttons live inside their row, so they are not part of
        // buttonViews; give them the same drag handling.
        listOf(rewind, playPause, forward).forEach { it.setOnTouchListener(buttonDragListener) }

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

    /**
     * Keeps the gaps between the transport buttons proportional to the window:
     * they were fixed at 6dp, so a resized window left them crowded together or
     * spread far apart.
     */
    private fun updateTransportSpacing() {
        val params = layoutParams ?: return
        if (baseWindowWidth <= 0 || transportButtons.isEmpty()) return
        val density = context.resources.displayMetrics.density
        val scale = (params.width.toFloat() / baseWindowWidth).coerceIn(0.6f, 3f)
        val margin = (6f * density * scale).roundToInt()
        transportButtons.forEach { button ->
            (button.layoutParams as? LinearLayout.LayoutParams)?.let { layout ->
                layout.marginStart = margin
                layout.marginEnd = margin
                button.layoutParams = layout
            }
        }
        root?.requestLayout()
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
            // A drag that started on this button is a window gesture, not a
            // click, even though the view's own click detection still fires.
            if (suppressNextClick) {
                suppressNextClick = false
                return@setOnClickListener
            }
            touchControls()
            onClick()
        }
        setOnTouchListener(buttonDragListener)
    }

    /** Which borders the finger grabbed, as a bitmask (0 = the middle). */
    private fun touchedEdges(
        insideX: Float,
        insideY: Float,
        width: Int,
        height: Int,
        density: Float
    ): Int {
        // Resizing and moving are completely separate gestures: only the four
        // corner squares resize, everything else (including the edges) moves the
        // window. Overlapping bands used to make a drag ambiguous.
        //
        // The zones are proportional (a little under half the window on each
        // side) because the *picture* does not fill the window: a portrait video
        // is pillarboxed, so its bottom-left corner can sit a third of the
        // window away from the window's own left edge. Touching there used to
        // count as a move, which is what made the window wander instead of
        // resizing.
        // Proportional zones with no upper cap: a cap made the corners shrink
        // relative to a window the user had enlarged, so a finger on the
        // picture's own bottom-left corner (about a third of the way in) was
        // read as "move" and the window wandered instead of resizing.
        val minimum = CORNER_DP * density
        val zoneX = (width * CORNER_FRACTION).coerceAtLeast(minimum)
        val zoneY = (height * CORNER_FRACTION).coerceAtLeast(minimum)
        val leftDistance = insideX
        val rightDistance = width - insideX
        val topDistance = insideY
        val bottomDistance = height - insideY
        // The nearest side wins, so a finger in the bottom-left corner reports
        // LEFT|BOTTOM even when the window is small enough that the corner bands
        // would otherwise overlap or miss.
        val horizontal = when {
            leftDistance <= zoneX && leftDistance <= rightDistance -> EDGE_LEFT
            rightDistance <= zoneX && rightDistance < leftDistance -> EDGE_RIGHT
            else -> 0
        }
        val vertical = when {
            topDistance <= zoneY && topDistance <= bottomDistance -> EDGE_TOP
            bottomDistance <= zoneY && bottomDistance < topDistance -> EDGE_BOTTOM
            else -> 0
        }
        return if (horizontal != 0 && vertical != 0) horizontal or vertical else 0
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
        startWindowX: Int,
        startWindowY: Int,
        startWidth: Int,
        startHeight: Int
    ) {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val minWidth = (160 * density).roundToInt()
        // The dragged corner decides which corner stays put: dragging the left
        // side pins the right edge, dragging the bottom side pins the top edge,
        // and so on. The pinned corner is resolved from the grabbed *edges*, not
        // from where the finger happened to be relative to the window's centre,
        // which is what made the bottom-left drag behave like the bottom-right.
        val pinRight = edges and EDGE_LEFT != 0
        val pinBottom = edges and EDGE_TOP != 0
        val pinnedX = startWindowX + startWidth
        val pinnedY = startWindowY + startHeight
        // Room between the pinned edge and the far side of the screen. Limiting
        // the size here is what keeps the pinned corner exactly in place.
        val horizontalRoom = if (pinRight) pinnedX - 8f * density else screenWidth - 8f * density - startWindowX
        val verticalRoom = if (pinBottom) pinnedY - 8f * density else screenHeight - 8f * density - startWindowY
        val maxWidth = minOf(
            (screenWidth - 16 * density).roundToInt(),
            horizontalRoom.roundToInt(),
            (verticalRoom * 16f / 9f).roundToInt()
        ).coerceAtLeast(minWidth)
        // Dragging away from the centre grows the window, dragging towards it
        // shrinks; that holds for every border and corner, which fixed the case
        // where both directions shrank the window.
        val horizontalGrowth = if (pinRight) -dx else dx
        val verticalGrowth = if (pinBottom) -dy else dy
        val horizontalEdge = edges and (EDGE_LEFT or EDGE_RIGHT) != 0
        val verticalEdge = edges and (EDGE_TOP or EDGE_BOTTOM) != 0
        val growth = when {
            // Corners move on both axes. The finger is projected onto the
            // window's own diagonal -- growing the width by 1 also moves the
            // dragged corner by 9/16 vertically -- so the corner follows the
            // finger 1:1 when dragged along that diagonal. The previous
            // `(horizontal + vertical * 16/9) / 2` weighted the vertical motion
            // almost twice as heavily as the horizontal one, so a sideways drag
            // crawled while every wobble shot the window away: that is the
            // "乱飘" the user saw on three corners.
            horizontalEdge && verticalEdge -> cornerResizeGrowth(horizontalGrowth, verticalGrowth)
            horizontalEdge -> horizontalGrowth
            else -> verticalGrowth * 16f / 9f
        }
        val newWidth = (startWidth + growth).roundToInt().coerceIn(minWidth, maxWidth)
        val newHeight = (newWidth * 9f / 16f).roundToInt()
        // Re-anchor on the pinned corner: its absolute position on screen is
        // preserved exactly, so dragging the bottom-left corner keeps the
        // top-right corner still (and the same for the other three).
        params.x = if (pinRight) pinnedX - newWidth else startWindowX
        params.y = if (pinBottom) pinnedY - newHeight else startWindowY
        params.width = newWidth
        params.height = newHeight
    }

    /** Keeps the window inside the current screen bounds. */
    private fun clampToScreen() {
        val view = root ?: return
        val params = layoutParams ?: return
        val density = context.resources.displayMetrics.density
        // Use the display bounds, not the configuration metrics: while the phone
        // shows the background/recents screen those metrics report a much
        // smaller area and the window used to shrink to its minimum.
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            val metrics = context.resources.displayMetrics
            android.graphics.Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val screenWidth = bounds.width()
        val screenHeight = bounds.height()
        val maxWidth = (screenWidth - 16 * density).roundToInt()
            .coerceAtLeast((160 * density).roundToInt())
        if (params.width > maxWidth) {
            params.width = maxWidth
            params.height = (maxWidth * 9f / 16f).roundToInt()
        }
        params.x = params.x.coerceIn(0, (screenWidth - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screenHeight - params.height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, params) }
    }
}

/** Height per unit of width for the floating window (16:9). */
internal const val MINI_WINDOW_HEIGHT_PER_WIDTH = 9f / 16f

/**
 * How much the window's width should change for a corner drag.
 *
 * Growing the width by one unit moves the dragged corner by
 * `(1, MINI_WINDOW_HEIGHT_PER_WIDTH)`, so the finger displacement is projected
 * onto that direction. Dragging exactly along the window's diagonal therefore
 * moves the corner exactly with the finger; sideways or vertical-only motion
 * moves it proportionally instead of being amplified into a jump.
 */
internal fun cornerResizeGrowth(horizontalGrowth: Float, verticalGrowth: Float): Float {
    val verticalPerWidth = MINI_WINDOW_HEIGHT_PER_WIDTH
    val denominator = 1f + verticalPerWidth * verticalPerWidth
    return (horizontalGrowth + verticalGrowth * verticalPerWidth) / denominator
}
