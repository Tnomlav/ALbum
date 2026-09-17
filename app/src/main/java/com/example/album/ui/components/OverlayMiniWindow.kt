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
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var playPauseButton: ImageButton? = null
    private var contentView: PlayerView? = null

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
            )
        }
        val rewind = overlayButton(android.R.drawable.ic_media_rew, density) { onSeekBack() }
        val playPause = overlayButton(android.R.drawable.ic_media_pause, density) {
            onTogglePlay()
            playPauseButton?.setImageResource(
                if (isPlaying()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
        }
        val forward = overlayButton(android.R.drawable.ic_media_ff, density) { onSeekForward() }
        playPauseButton = playPause
        listOf(rewind, playPause, forward).forEach { controls.addView(it) }
        container.addView(controls)
        // Back to full screen sits in the top-left corner, close in the
        // top-right; the transport controls stay in the middle.
        container.addView(
            overlayButton(
                android.R.drawable.ic_menu_crop,
                density,
                gravity = Gravity.TOP or Gravity.START
            ) {
                dismiss()
                onRestore()
            }
        )
        container.addView(
            overlayButton(
                android.R.drawable.ic_menu_close_clear_cancel,
                density,
                gravity = Gravity.TOP or Gravity.END
            ) {
                dismiss()
                onClose()
            }
        )

        container.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            private var startWindowX = 0
            private var startWindowY = 0
            private var startWidth = 0
            private var startHeight = 0
            private var edges = 0
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val params = layoutParams ?: return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
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
                        if (edges != 0) {
                            applyResize(
                                params = params,
                                edges = edges,
                                dx = dx,
                                dy = dy,
                                density = density,
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
        onVisibilityChanged(true)
    }

    fun dismiss() {
        val view = root ?: return
        runCatching { windowManager.removeView(view) }
        contentView?.player = null
        root = null
        contentView = null
        playPauseButton = null
        onVisibilityChanged(false)
    }

    fun syncPlayState() {
        playPauseButton?.setImageResource(
            if (isPlaying()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
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
        val size = (40 * density).roundToInt()
        layoutParams = if (gravity == 0) {
            LinearLayout.LayoutParams(size, size).apply {
                marginStart = (6 * density).roundToInt()
                marginEnd = (6 * density).roundToInt()
            }
        } else {
            FrameLayout.LayoutParams(size, size, gravity).apply {
                val margin = (6 * density).roundToInt()
                if (gravity and Gravity.START != 0) marginStart = margin
                if (gravity and Gravity.END != 0) marginEnd = margin
                topMargin = margin
            }
        }
        setOnClickListener { onClick() }
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
        startWindowX: Int,
        startWindowY: Int,
        startWidth: Int,
        startHeight: Int
    ) {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val minWidth = (160 * density).roundToInt()
        val maxWidth = (screenWidth - 16 * density).roundToInt().coerceAtLeast(minWidth)
        val horizontal = edges and (EDGE_LEFT or EDGE_RIGHT) != 0
        val vertical = edges and (EDGE_TOP or EDGE_BOTTOM) != 0
        val widthDelta = when {
            horizontal && vertical -> maxOf(dx, dy * 16f / 9f)
            horizontal -> dx
            else -> dy * 16f / 9f
        }
        val newWidth = (
            if (edges and EDGE_LEFT != 0) startWidth - widthDelta else startWidth + widthDelta
            ).roundToInt().coerceIn(minWidth, maxWidth)
        val newHeight = (newWidth * 9f / 16f).roundToInt()
        if (edges and EDGE_LEFT != 0) params.x = startWindowX + (startWidth - newWidth)
        if (edges and EDGE_TOP != 0) params.y = startWindowY + (startHeight - newHeight)
        params.width = newWidth
        params.height = newHeight
    }
}
