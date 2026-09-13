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
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = (6 * density).roundToInt() }
        }
        val close = overlayButton(android.R.drawable.ic_menu_close_clear_cancel, density) {
            dismiss()
            onClose()
        }
        val rewind = overlayButton(android.R.drawable.ic_media_rew, density) { onSeekBack() }
        val playPause = overlayButton(android.R.drawable.ic_media_pause, density) {
            onTogglePlay()
            playPauseButton?.setImageResource(
                if (isPlaying()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
        }
        val forward = overlayButton(android.R.drawable.ic_media_ff, density) { onSeekForward() }
        val restore = overlayButton(android.R.drawable.ic_menu_crop, density) {
            dismiss()
            onRestore()
        }
        playPauseButton = playPause
        listOf(close, rewind, playPause, forward, restore).forEach { controls.addView(it) }
        container.addView(controls)

        container.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            private var startWindowX = 0
            private var startWindowY = 0
            private var resizing = false
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val params = layoutParams ?: return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        startWindowX = params.x
                        startWindowY = params.y
                        resizing = event.rawX > params.x + params.width - 60 * density &&
                            event.rawY > params.y + params.height - 60 * density
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - startX)
                        val dy = (event.rawY - startY)
                        if (resizing) {
                            val newWidth = (params.width + maxOf(dx, dy * 16f / 9f)).roundToInt()
                                .coerceIn((160 * density).roundToInt(), screenWidth - (24 * density).roundToInt())
                            params.width = newWidth
                            params.height = (newWidth * 9f / 16f).roundToInt()
                        } else {
                            params.x = startWindowX + dx.roundToInt()
                            params.y = startWindowY + dy.roundToInt()
                        }
                        runCatching { windowManager.updateViewLayout(view, params) }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        resizing = false
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

    private fun overlayButton(iconRes: Int, density: Float, onClick: () -> Unit): ImageButton =
        ImageButton(context).apply {
            setImageResource(iconRes)
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            setColorFilter(Color.WHITE)
            val size = (40 * density).roundToInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = (6 * density).roundToInt()
                marginEnd = (6 * density).roundToInt()
            }
            setOnClickListener { onClick() }
        }

    private fun maxOf(a: Float, b: Float): Float = if (abs(a) >= abs(b)) a else b
}
