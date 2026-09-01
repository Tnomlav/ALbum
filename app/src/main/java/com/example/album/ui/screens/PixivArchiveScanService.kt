package com.example.album.ui.screens

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.album.R
import com.example.album.data.PixivArchiveProgress
import com.example.album.data.PixivArchiveRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

/** Owns Pixiv discovery independently from the Compose screen lifecycle. */
class PixivArchiveScanService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanJob: kotlinx.coroutines.Job? = null
    private var scanGeneration = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            scanGeneration++
            scanJob?.cancel()
            scanJob = null
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_SCAN) return START_NOT_STICKY
        val source = intent.getStringExtra(EXTRA_SOURCE_URI)?.let(Uri::parse)
        if (source == null) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        // A cancelled job can still be unwinding while Android delivers the
        // next scan command to this same service instance. Cancel that stale
        // job and accept the new request instead of silently ignoring it.
        scanJob?.cancel()
        val generation = ++scanGeneration
        startAsForeground(buildNotification("正在准备 Pixiv 扫描"))
        scanJob = serviceScope.launch {
            val session = PixivArchiveSession(applicationContext)
            session.setScanState(ArchiveUiState.Scanning)
            session.persistScanProgress(PixivArchiveProgress(
                phase = com.example.album.data.PixivArchivePhase.Discover,
                completed = 0,
                total = 0,
                failed = 0,
                message = "正在读取来源目录"
            ))
            try {
                val repository = PixivArchiveRepository(applicationContext)
                val onProgress: suspend (PixivArchiveProgress) -> Unit = { update ->
                    ensureActive()
                    if (session.isScanCancellationRequested()) throw CancellationException("扫描已终止")
                    session.persistScanProgress(update)
                    updateNotification(update.message)
                }
                val onRecord: suspend (com.example.album.data.PixivArchiveRecord) -> Unit = { record ->
                    ensureActive()
                    if (session.isScanCancellationRequested()) throw CancellationException("扫描已终止")
                    session.upsertRecord(record)
                }
                // "重新扫描" must inspect the source tree again. Re-querying
                // only persisted records misses newly added files and leaves
                // records for files that no longer exist in the source tree.
                val scanned = repository.scan(
                    source,
                    intent.getIntExtra(EXTRA_MAX_BATCH, 200),
                    onProgress,
                    onRecord
                )
                if (session.isScanCancellationRequested()) throw CancellationException("扫描已中止")
                session.replaceRecords(scanned)
                session.setScanState(ArchiveUiState.Ready)
                session.persistScanProgress(PixivArchiveProgress(
                    phase = com.example.album.data.PixivArchivePhase.Ready,
                    completed = scanned.count { it.status.name == "Ready" },
                    total = scanned.size,
                    failed = scanned.count { it.status.name != "Ready" },
                    message = "扫描完成，共 ${scanned.size} 项结果",
                    log = "扫描完成"
                ))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                session.setScanState(ArchiveUiState.Error)
                session.persistScanProgress(PixivArchiveProgress(
                    phase = com.example.album.data.PixivArchivePhase.Error,
                    completed = session.completed.value,
                    total = session.activity.value.total,
                    failed = session.failed.value,
                    message = error.message ?: "无法读取来源目录",
                    log = error.message ?: "扫描失败"
                ))
            } finally {
                // An older cancelled job must not stop the foreground state
                // or the service instance that now owns a newer scan.
                if (generation == scanGeneration) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    scanJob = null
                    stopSelfResult(startId)
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(message: String) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                buildNotification(message)
            )
        }
    }

    private fun buildNotification(message: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Pixiv 归档扫描")
        .setContentText(message)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Pixiv 归档扫描", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SCAN = "com.example.album.action.PIXIV_ARCHIVE_SCAN"
        const val ACTION_CANCEL = "com.example.album.action.PIXIV_ARCHIVE_CANCEL"
        const val EXTRA_SOURCE_URI = "source_uri"
        const val EXTRA_MAX_BATCH = "max_batch"
        private const val CHANNEL_ID = "pixiv_archive_scan"
        private const val NOTIFICATION_ID = 42
    }
}
