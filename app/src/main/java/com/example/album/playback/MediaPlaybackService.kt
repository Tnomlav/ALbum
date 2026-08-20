package com.example.album.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.album.MainActivity
import com.example.album.R

class MediaPlaybackService : Service() {
    private var player: ExoPlayer? = null
    private var mediaUri: Uri? = null
    private var mediaName: String = "视频"
    private val handler = Handler(Looper.getMainLooper())
    private val saveProgress = object : Runnable {
        override fun run() {
            persistProgress()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> player?.let { if (it.isPlaying) it.pause() else it.play() }
            ACTION_STOP -> stopSelf()
            ACTION_OPEN_PLAYER -> openPlayer()
            ACTION_START -> startPlayback(intent)
        }
        updateNotification()
        // Playback is user initiated and must not be resurrected after the
        // emulator/device process is stopped or restored from a snapshot.
        return START_NOT_STICKY
    }

    private fun startPlayback(intent: Intent) {
        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse) ?: return
        mediaUri = uri
        mediaName = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "视频" }
        val position = intent.getLongExtra(EXTRA_POSITION, 0L)
        player?.release()
        player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
            prepare()
            seekTo(position)
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = updateNotification()
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) stopSelf()
                }
            })
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.removeCallbacks(saveProgress)
        handler.post(saveProgress)
    }

    private fun updateNotification() {
        if (player == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MediaPlaybackService::class.java).setAction(ACTION_OPEN_PLAYER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaPlaybackService::class.java).setAction(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MediaPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playing = player?.isPlaying == true
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(mediaName)
            .setContentText(if (playing) "正在后台播放" else "播放已暂停")
            .setContentIntent(openIntent)
            // Keep the foreground playback notification persistent even while
            // ExoPlayer is buffering and has not reported isPlaying yet.
            .setOngoing(player != null)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, if (playing) "暂停" else "播放", toggleIntent)
            .addAction(0, "停止", stopIntent)
            .build()
    }

    private fun persistProgress() {
        val uri = mediaUri ?: return
        val position = player?.currentPosition ?: return
        getSharedPreferences("album_settings", MODE_PRIVATE).edit()
            .putLong("video_position_${uri.toString().hashCode()}", position)
            .apply()
    }

    private fun openPlayer() {
        val uri = mediaUri ?: return
        val activePlayer = player
        val position = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val shouldPlay = activePlayer?.playWhenReady == true
        getSharedPreferences("album_settings", MODE_PRIVATE).edit()
            .putLong("video_position_${uri.toString().hashCode()}", position)
            .apply()

        handler.removeCallbacks(saveProgress)
        activePlayer?.pause()
        activePlayer?.release()
        player = null
        stopForeground(STOP_FOREGROUND_REMOVE)

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_RESUME_PLAYER
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_URI, uri.toString())
                putExtra(EXTRA_POSITION, position)
                putExtra(EXTRA_PLAY_WHEN_READY, shouldPlay)
                putExtra(EXTRA_REQUEST_ID, System.nanoTime())
            }
        )
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "视频后台播放", NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示相册视频的后台播放控制"
            }
        )
    }

    override fun onDestroy() {
        handler.removeCallbacks(saveProgress)
        persistProgress()
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        handler.removeCallbacks(saveProgress)
        player?.release()
        player = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.album.playback.START"
        const val ACTION_PLAY_PAUSE = "com.example.album.playback.PLAY_PAUSE"
        const val ACTION_STOP = "com.example.album.playback.STOP"
        const val ACTION_OPEN_PLAYER = "com.example.album.playback.OPEN_PLAYER"
        const val ACTION_RESUME_PLAYER = "com.example.album.playback.RESUME_PLAYER"
        const val EXTRA_URI = "uri"
        const val EXTRA_NAME = "name"
        const val EXTRA_POSITION = "position"
        const val EXTRA_PLAY_WHEN_READY = "play_when_ready"
        const val EXTRA_REQUEST_ID = "request_id"
        private const val CHANNEL_ID = "album_video_playback"
        private const val NOTIFICATION_ID = 4102
    }
}
