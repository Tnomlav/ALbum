// The buttons are ordinary ImageButtons that keep their own performClick; the
// extra touch listener only forwards drags to the window's move/resize code.
@file:Suppress("UnsafeOptInUsageError", "ClickableViewAccessibility")

package com.example.album.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.core.content.ContextCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.util.Locale
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

        /**
         * How often the window checks whether the system task switcher is up.
         * The check has to be quick enough that the window is already small
         * while the switcher's opening animation runs.
         */
        private const val TASK_MANAGER_POLL_MS = 200L

        /** Where the platform puts the dismiss reason on the system dialog broadcast. */
        private const val SYSTEM_DIALOG_REASON_KEY = "reason"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var playPauseButton: ImageButton? = null
    private var contentView: PlayerView? = null
    private var buttonViews: List<View> = emptyList()
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
    private var buttonDragListener: View.OnTouchListener? = null
    private var compact = false
    private var savedWidth = 0
    private var savedHeight = 0
    // The size the window was created with. Coming back from the shrunk state
    // returns to this, not to whatever size the user dragged it to.
    private var defaultWidth = 0
    private var defaultHeight = 0
    // The task switcher is watched while the window is on screen: the system
    // gives a background app no callback for it. Only a *visible* switcher
    // counts, because the launcher keeps the switcher entry in the task history
    // after it is closed -- treating that leftover as "the switcher is open"
    // made a freshly created window shrink on its own and then latched, so it
    // never reacted to a real visit again.
    private var switcherShown = false
    private val hideHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideControls = Runnable { setControlsVisible(false) }
    private var controlsVisible = true
    private val taskManagerProbe = object : Runnable {
        override fun run() {
            // The next check is always queued, even if this one throws: a single
            // failure used to stop the window from ever reacting again.
            try {
                val showing = isTaskManagerShowing()
                if (showing && !switcherShown) {
                    switcherShown = true
                    if (!compact) setCompact(true)
                } else if (!showing) {
                    // Arm again for the next visit.
                    switcherShown = false
                }
            } catch (_: Throwable) {
                // Never let the probe die; the window just tries again.
            } finally {
                if (root != null) {
                    hideHandler.postDelayed(this, TASK_MANAGER_POLL_MS)
                }
            }
        }
    }
    private val closeSystemDialogsReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            val reason = intent?.getStringExtra(SYSTEM_DIALOG_REASON_KEY).orEmpty()
            // "recentapps" is the task switcher; the home button reports
            // "homekey" and must leave the window alone.
            if (reason.contains("recent", ignoreCase = true) && root != null) {
                switcherShown = true
                setCompact(true)
            }
        }
    }
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
        defaultWidth = width
        defaultHeight = height

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

        // Dragging anywhere -- including from a button -- moves or resizes the
        // window. A tap that never moves still reaches the button's own click.
        fun beginGesture(event: MotionEvent): Boolean {
            val params = layoutParams ?: return false
            gestureStartX = event.rawX
            gestureStartY = event.rawY
            gestureMoved = false
            gestureStartWindowX = params.x
            gestureStartWindowY = params.y
            gestureStartWidth = params.width
            gestureStartHeight = params.height
            // A shrunk window is only moved: its corners are too close together
            // to resize, and a tap on it restores the previous size.
            //
            // Hit-testing uses coordinates inside the window: the layout params
            // are relative to the display frame, which sits below the status bar,
            // so comparing them with raw screen coordinates shifted the corner
            // zones vertically.
            val origin = IntArray(2)
            root?.getLocationOnScreen(origin)
            gestureEdges = if (compact) {
                0
            } else {
                touchedEdges(
                    insideX = event.rawX - origin[0],
                    insideY = event.rawY - origin[1],
                    width = params.width,
                    height = params.height,
                    density = density
                )
            }
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
                MotionEvent.ACTION_DOWN -> beginGesture(event)
                MotionEvent.ACTION_MOVE -> continueGesture(view, event)
                MotionEvent.ACTION_UP -> {
                    val wasTap = !gestureMoved
                    gestureEdges = 0
                    if (wasTap) {
                        // While the window is shrunk (the phone is showing its
                        // task switcher) a tap brings the player back to the
                        // size the user had chosen.
                        if (compact) {
                            setCompact(false)
                            setControlsVisible(true)
                        } else {
                            // A tap on the empty area toggles the controls, the
                            // same way it does inside the app's player.
                            setControlsVisible(!controlsVisible)
                        }
                    }
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
                    beginGesture(event)
                    false
                }
                MotionEvent.ACTION_MOVE -> if (gestureMoved) continueGesture(view, event) else false
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    gestureEdges = 0
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
        runCatching {
            ContextCompat.registerReceiver(
                context,
                closeSystemDialogsReceiver,
                IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        hideHandler.postDelayed(taskManagerProbe, TASK_MANAGER_POLL_MS)
        setControlsVisible(true)
        onVisibilityChanged(true)
    }

    fun dismiss() {
        val view = root ?: return
        hideHandler.removeCallbacks(hideControls)
        hideHandler.removeCallbacks(taskManagerProbe)
        runCatching { context.unregisterReceiver(closeSystemDialogsReceiver) }
        runCatching { context.unregisterComponentCallbacks(configurationCallback) }
        runCatching { windowManager.removeView(view) }
        contentView?.player = null
        root = null
        contentView = null
        playPauseButton = null
        buttonViews = emptyList()
        compact = false
        switcherShown = false
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
        if (controlsVisible && !compact) setControlsVisible(true)
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
        val minimum = CORNER_DP * density
        val maximum = 96f * density
        val zoneX = (width * CORNER_FRACTION).coerceIn(minimum, maximum)
        val zoneY = (height * CORNER_FRACTION).coerceIn(minimum, maximum)
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
            // Corners move on both axes: average the projections so the window
            // scales smoothly instead of snapping to one axis.
            horizontalEdge && verticalEdge -> (horizontalGrowth + verticalGrowth * 16f / 9f) / 2f
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
    /**
     * Shrinks the window while the phone shows its task switcher, where a full
     * size window would cover the task cards. A tap on the shrunk window puts it
     * back to the size the user had.
     */
    fun setCompact(compact: Boolean) {
        val view = root ?: return
        val params = layoutParams ?: return
        if (compact == this.compact) return
        this.compact = compact
        if (compact) {
            savedWidth = params.width
            savedHeight = params.height
            val metrics = context.resources.displayMetrics
            val target = (min(metrics.widthPixels, metrics.heightPixels) * 0.42f).roundToInt()
                .coerceAtMost((220 * metrics.density).roundToInt())
                .coerceAtLeast((120 * metrics.density).roundToInt())
            params.width = target
            params.height = (target * 9f / 16f).roundToInt()
        } else {
            // Back to the size the window opened with, not the size the user had
            // dragged it to before the task switcher appeared.
            val restoreWidth = if (defaultWidth > 0) defaultWidth else savedWidth
            val restoreHeight = if (defaultHeight > 0) defaultHeight else savedHeight
            if (restoreWidth > 0) {
                params.width = restoreWidth
                params.height = restoreHeight
            }
        }
        // The buttons are laid out for the full size window, so they are hidden
        // while it is shrunk; a tap on the picture restores the window instead.
        setControlsVisible(!compact)
        clampToScreen()
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    /**
     * Whether the system's task switcher is on screen right now. Android sends
     * a backgrounded app no callback for it, so this asks the task list the
     * platform still exposes (which, without the system-only permission,
     * contains only our own tasks and the launcher's).
     *
     * The test is "the launcher is visible but not showing its home screen":
     * the switcher is exactly that on every phone seen so far, and unlike
     * matching a class name it does not depend on how the vendor named the
     * switcher's activity. A task that is only left over in the history is
     * skipped via isVisible(), so it cannot latch the window shut.
     */
    @Suppress("DEPRECATION")
    private fun isTaskManagerShowing(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            ?: return false
        val tasks = runCatching { manager.getRecentTasks(6, 0) }.getOrNull() ?: return false
        val home = homeComponent()
        return tasks.any { info ->
            // TaskInfo.isVisible() only exists from API 32; older releases fall
            // back to the top-activity comparison alone.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2 && !info.isVisible()) return@any false
            val component = info.topActivity ?: info.baseActivity ?: return@any false
            if (component.packageName == context.packageName) return@any false
            val name = "${component.packageName}.${component.className}".lowercase(Locale.ROOT)
            name.contains("recents") || name.contains("overview") || name.contains("taskmanager") ||
                (home != null && component != home)
        }
    }

    /** The launcher's home activity, so "not the home screen" can be detected. */
    private fun homeComponent(): android.content.ComponentName? = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val resolved = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        resolved?.activityInfo?.let { android.content.ComponentName(it.packageName, it.name) }
    }.getOrNull()

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
