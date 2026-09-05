@file:Suppress("UnsafeOptInUsageError")

package com.example.album.ui

import android.app.WallpaperManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.widget.Toast
import androidx.media3.common.MimeTypes
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.album.data.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/** Progress exposed to the wallpaper manager so a batch can be cancelled safely. */
sealed class WallpaperImportState {
    data object Idle : WallpaperImportState()
    data class Running(val kind: Kind, val completed: Int, val total: Int) : WallpaperImportState()
    data class Finished(val kind: Kind, val imported: Int, val failed: Int) : WallpaperImportState()
    data class Failed(val kind: Kind) : WallpaperImportState()

    enum class Kind { STATIC, DYNAMIC }
}

internal data class ImportResult<T>(val value: T?, val imported: Int, val failed: Int)

/** Serializes imports and gives the UI one cancellation point for all wallpaper batches. */
object WallpaperImportCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Any()
    private var activeJob: Job? = null
    private val stateFlow = MutableStateFlow<WallpaperImportState>(WallpaperImportState.Idle)

    val state: StateFlow<WallpaperImportState> = stateFlow.asStateFlow()

    fun isRunning(): Boolean = synchronized(lock) { activeJob?.isActive == true }

    fun cancel(): Boolean = synchronized(lock) {
        activeJob?.let { if (it.isActive) { it.cancel(); true } else false } ?: false
    }

    internal fun <T> start(
        context: Context,
        kind: WallpaperImportState.Kind,
        total: Int,
        english: Boolean,
        work: suspend (suspend (Int) -> Unit) -> ImportResult<T>,
        onSuccess: (T) -> Unit,
        failureMessage: String
    ): Boolean {
        synchronized(lock) {
            if (activeJob?.isActive == true) {
                Toast.makeText(context, if (english) "A wallpaper import is already running" else "壁纸导入正在进行中", Toast.LENGTH_SHORT).show()
                return false
            }
            activeJob = scope.launch {
                stateFlow.value = WallpaperImportState.Running(kind, 0, total)
                try {
                    val reportStep = (total / 100).coerceAtLeast(1)
                    val result = withContext(Dispatchers.IO) {
                        work { completed ->
                            if (completed == total || completed % reportStep == 0) {
                                stateFlow.value = WallpaperImportState.Running(kind, completed, total)
                            }
                        }
                    }
                    stateFlow.value = WallpaperImportState.Finished(kind, result.imported, result.failed)
                    if (result.value == null) {
                        Toast.makeText(context, failureMessage, Toast.LENGTH_LONG).show()
                    } else {
                        val message = if (result.failed == 0) {
                            if (english) "Imported ${result.imported} item(s)" else "已导入 ${result.imported} 项"
                        } else {
                            if (english) "Imported ${result.imported}; skipped ${result.failed}" else "已导入 ${result.imported} 项，跳过 ${result.failed} 项"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        onSuccess(result.value)
                    }
                } catch (_: CancellationException) {
                    stateFlow.value = WallpaperImportState.Idle
                    Toast.makeText(context, if (english) "Wallpaper import cancelled" else "已中止壁纸导入", Toast.LENGTH_SHORT).show()
                } catch (_: Throwable) {
                    stateFlow.value = WallpaperImportState.Failed(kind)
                    Toast.makeText(context, failureMessage, Toast.LENGTH_LONG).show()
                } finally {
                    synchronized(lock) {
                        if (activeJob === kotlin.coroutines.coroutineContext[Job]) activeJob = null
                    }
                }
            }
            return true
        }
    }
}

/** Starts the system wallpaper flow while preserving access to SAF and MediaStore URIs. */
fun setWallpaper(context: Context, item: MediaItem, english: Boolean) {
    if (item.isVideo) setDynamicWallpaper(context, listOf(item), english)
    else setStaticWallpaper(context, listOf(item), english)
}

/** Opens Android's app resolver for wallpaper-capable handlers. */
fun launchWallpaperAppChooser(context: Context, item: MediaItem, english: Boolean) {
    val uri = item.uri
    val intent = Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(uri, if (item.isVideo) "video/*" else "image/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply { clipData = ClipData.newRawUri("wallpaper", uri) }
    val hasHandler = context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
    if (!hasHandler) {
        Toast.makeText(context, if (english) "No wallpaper app is available" else "未找到可用的壁纸应用", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, if (english) "Unable to open wallpaper apps" else "无法打开壁纸应用选择器", Toast.LENGTH_SHORT).show()
    }
}

fun setStaticWallpaper(context: Context, items: List<MediaItem>, english: Boolean) {
    val imageItems = items.filterNot { it.isVideo }.distinctBy { it.uri.toString() }
    if (imageItems.isEmpty()) return
    WallpaperImportCoordinator.start(
        context = context,
        kind = WallpaperImportState.Kind.STATIC,
        total = imageItems.size,
        english = english,
        failureMessage = if (english) "Unable to import images" else "无法导入图片",
        work = { update -> importStaticQueue(context, imageItems, update) },
        onSuccess = { openStaticWallpaperSettings(context, english) }
    )
}

fun setStaticWallpaperBitmap(context: Context, bitmap: Bitmap, english: Boolean) {
    val accepted = WallpaperImportCoordinator.start(
        context = context,
        kind = WallpaperImportState.Kind.STATIC,
        total = 1,
        english = english,
        failureMessage = if (english) "Unable to save wallpaper" else "无法保存壁纸",
        work = { update ->
            try {
                cleanupUnusedWallpaperArtifacts(context)
                ensureAvailableSpace(context, bitmap.byteCount.toLong())
                val staging = File(context.filesDir, "static_wallpaper_queue_staging_${System.nanoTime()}").apply { mkdirs() }
                try {
                    val part = File(staging, "image_00000.jpg.part")
                    val target = File(staging, "image_00000.jpg")
                    part.outputStream().use { output ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) error("Unable to encode image")
                    }
                    check(part.renameTo(target)) { "Unable to finalize image" }
                    currentCoroutineContext().ensureActive()
                    val finalDirectory = publishDirectory(context, staging, "static_wallpaper_queue")
                    currentCoroutineContext().ensureActive()
                    commitStaticQueue(context, finalDirectory, listOf(target.name))
                    update(1)
                    ImportResult(finalDirectory, 1, 0)
                } catch (error: Throwable) {
                    staging.deleteRecursively()
                    throw error
                }
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        },
        onSuccess = { openStaticWallpaperSettings(context, english) }
    )
    // The coordinator owns the bitmap only after accepting the job. If an
    // import is already running, release this rejected request immediately.
    if (!accepted && !bitmap.isRecycled) bitmap.recycle()
}

fun setDynamicWallpaper(context: Context, items: List<MediaItem>, english: Boolean) {
    val videoItems = items.filter { it.isVideo }.distinctBy { it.uri.toString() }
    if (videoItems.isEmpty()) return
    WallpaperImportCoordinator.start(
        context = context,
        kind = WallpaperImportState.Kind.DYNAMIC,
        total = videoItems.size,
        english = english,
        failureMessage = if (english) "Unable to import videos" else "无法导入视频",
        work = { update -> importDynamicQueue(context, videoItems, update) },
        onSuccess = { openDynamicWallpaperSettings(context, english) }
    )
}

private suspend fun importStaticQueue(context: Context, items: List<MediaItem>, update: suspend (Int) -> Unit): ImportResult<File> {
    cleanupUnusedWallpaperArtifacts(context)
    ensureAvailableSpace(context, items.sumOf { it.size.coerceAtLeast(0L) })
    val staging = File(context.filesDir, "static_wallpaper_queue_staging_${System.nanoTime()}").apply { mkdirs() }
    return try {
        val names = mutableListOf<String>()
        var failed = 0
        items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            val extension = item.name.substringAfterLast('.', "jpg").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "jpg"
            val target = File(staging, "image_${index.toString().padStart(5, '0')}.$extension")
            val part = File("${target.absolutePath}.part")
            try {
                copyUriToFile(context, item.uri, part)
                check(part.renameTo(target)) { "Unable to finalize image" }
                names += target.name
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                part.delete()
                target.delete()
                failed++
            }
            update(index + 1)
        }
        if (names.isEmpty()) error("Unable to read image")
        currentCoroutineContext().ensureActive()
        val finalDirectory = publishDirectory(context, staging, "static_wallpaper_queue")
        currentCoroutineContext().ensureActive()
        commitStaticQueue(context, finalDirectory, names)
        ImportResult(finalDirectory, names.size, failed)
    } catch (error: Throwable) {
        staging.deleteRecursively()
        throw error
    }
}

private suspend fun importDynamicQueue(context: Context, items: List<MediaItem>, update: suspend (Int) -> Unit): ImportResult<File> {
    cleanupUnusedWallpaperArtifacts(context)
    ensureAvailableSpace(context, items.sumOf { it.size.coerceAtLeast(0L) })
    val lowPower = context.getSharedPreferences("album_preferences", Context.MODE_PRIVATE).getBoolean("wallpaper_low_power", false) ||
        (context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true)
    val staging = File(context.filesDir, "live_wallpaper_queue_staging_${System.nanoTime()}").apply { mkdirs() }
    return try {
        val names = mutableListOf<String>()
        var failed = 0
        items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            val target = File(staging, "video_${index.toString().padStart(5, '0')}.mp4")
            val part = File("${target.absolutePath}.part")
            try {
                val success = if (lowPower) transcodeLowPowerVideo(context, item, part) else {
                    copyUriToFile(context, item.uri, part)
                    true
                }
                check(success && part.isFile && part.length() > 0L && isPlayableVideo(part)) { "Invalid video" }
                check(part.renameTo(target)) { "Unable to finalize video" }
                names += target.name
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                part.delete()
                target.delete()
                failed++
            }
            update(index + 1)
        }
        if (names.isEmpty()) error("Unable to read video")
        currentCoroutineContext().ensureActive()
        val finalDirectory = publishDirectory(context, staging, "live_wallpaper_queue")
        currentCoroutineContext().ensureActive()
        commitDynamicQueue(context, finalDirectory, names, lowPower)
        ImportResult(finalDirectory, names.size, failed)
    } catch (error: Throwable) {
        staging.deleteRecursively()
        throw error
    }
}

private suspend fun copyUriToFile(context: Context, source: android.net.Uri, target: File) = withContext(Dispatchers.IO) {
    target.parentFile?.mkdirs()
    val input = context.contentResolver.openInputStream(source) ?: throw IOException("Unable to read source")
    input.use { sourceStream ->
        target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = sourceStream.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.flush()
        }
    }
}

private fun ensureAvailableSpace(context: Context, inputBytes: Long) {
    val data = StatFs(context.filesDir.absolutePath)
    val reserve = 64L * 1024L * 1024L
    val safeInput = inputBytes.coerceAtLeast(0L).coerceAtMost(Long.MAX_VALUE - reserve)
    check(data.availableBytes >= safeInput + reserve) { "Insufficient storage" }
}

private fun isPlayableVideo(file: File): Boolean {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0L
    } catch (_: Exception) {
        false
    } finally {
        retriever.release()
    }
}

private fun publishDirectory(context: Context, staging: File, prefix: String): File {
    val finalDirectory = File(context.filesDir, "${prefix}_${System.nanoTime()}")
    check(staging.renameTo(finalDirectory)) { "Unable to prepare wallpaper queue" }
    return finalDirectory
}

private fun commitStaticQueue(context: Context, directory: File, names: List<String>) {
    val preferences = context.getSharedPreferences("album_preferences", Context.MODE_PRIVATE)
    val editor = preferences.edit()
        .putString("static_wallpaper_queue_dir", directory.name)
        .putString("static_wallpaper_queue", JSONArray(names).toString())
        .putInt("static_wallpaper_index", 0)
        .remove("static_wallpaper_shuffle")
    if (preferences.getString("wallpaper_static_order", "InOrder") == "Shuffle") {
        val shuffle = names.indices.shuffled()
        editor.putString("static_wallpaper_shuffle", JSONArray(shuffle).toString())
            .putInt("static_wallpaper_index", shuffle.first())
    }
    if (!editor.commit()) {
        directory.deleteRecursively()
        error("Unable to save wallpaper queue")
    }
    cleanupUnusedWallpaperArtifacts(context)
}

private fun commitDynamicQueue(context: Context, directory: File, names: List<String>, lowPower: Boolean) {
    val preferences = context.getSharedPreferences("album_preferences", Context.MODE_PRIVATE)
    val editor = preferences.edit()
        .putBoolean("wallpaper_low_power", lowPower)
        .putString("live_wallpaper_queue_dir", directory.name)
        .putString("live_wallpaper_queue", JSONArray(names).toString())
        .putInt("live_wallpaper_index", 0)
        .remove("live_wallpaper_single_video")
        .remove("live_wallpaper_shuffle")
    if (preferences.getString("wallpaper_video_order", "InOrder") == "Shuffle") {
        val shuffle = names.indices.shuffled()
        editor.putString("live_wallpaper_shuffle", JSONArray(shuffle).toString())
            .putInt("live_wallpaper_index", shuffle.first())
    }
    if (!editor.commit()) {
        directory.deleteRecursively()
        error("Unable to save wallpaper queue")
    }
    cleanupUnusedWallpaperArtifacts(context)
}

private fun cleanupUnusedWallpaperArtifacts(context: Context) {
    val root = context.filesDir
    val preferences = context.getSharedPreferences("album_preferences", Context.MODE_PRIVATE)
    val keepStatic = preferences.getString("static_wallpaper_queue_dir", "static_wallpaper_queue")
    val keepDynamic = preferences.getString("live_wallpaper_queue_dir", "live_wallpaper_queue")
    val keepSingle = preferences.getString("live_wallpaper_single_video", null)
    root.listFiles()?.forEach { file ->
        val name = file.name
        val isWallpaperArtifact = name.startsWith("static_wallpaper_queue") || name.startsWith("live_wallpaper_queue") || name.startsWith("live_wallpaper_video_")
        val keepLegacySingle = name == "live_wallpaper_video" &&
            preferences.getString("live_wallpaper_queue_dir", null) == null &&
            preferences.getString("live_wallpaper_single_video", null) == null
        val keep = name == keepStatic || name == keepDynamic || name == keepSingle || keepLegacySingle
        if (isWallpaperArtifact && !keep) file.deleteRecursively()
    }
}

private fun openStaticWallpaperSettings(context: Context, english: Boolean) {
    val component = ComponentName(context, com.example.album.wallpaper.ImageWallpaperService::class.java)
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, if (english) "Unable to open wallpaper settings" else "无法打开壁纸设置", Toast.LENGTH_SHORT).show()
    }
}

private fun openDynamicWallpaperSettings(context: Context, english: Boolean) {
    val component = ComponentName(context, com.example.album.wallpaper.VideoWallpaperService::class.java)
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, if (english) "Unable to open live wallpaper settings" else "无法打开动态壁纸设置", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun transcodeLowPowerVideo(context: Context, item: MediaItem, target: File): Boolean {
    val retriever = MediaMetadataRetriever()
    val (width, height, frameRate) = runCatching {
        retriever.setDataSource(context, item.uri)
        Triple(
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: item.width,
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: item.height,
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f
        )
    }.getOrElse { Triple(item.width, item.height, 30f) }
    retriever.release()
    val longest = maxOf(width, height).coerceAtLeast(1)
    val scale = minOf(1f, 1080f / longest.toFloat())
    val outputFrameRate = frameRate.coerceAtMost(30f).coerceAtLeast(24f).toInt()
    return suspendCancellableCoroutine { continuation ->
        val effect = ScaleAndRotateTransformation.Builder().setScale(scale, scale).build()
        val encoderSettings = VideoEncoderSettings.Builder().setBitrate(2_500_000).build()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(DefaultEncoderFactory.Builder(context).setRequestedVideoEncoderSettings(encoderSettings).build())
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(target.isFile && target.length() > 0L)
                }

                override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                    target.delete()
                    if (continuation.isActive) continuation.resume(false)
                }
            })
            .build()
        continuation.invokeOnCancellation { transformer.cancel(); target.delete() }
        transformer.start(
            EditedMediaItem.Builder(androidx.media3.common.MediaItem.fromUri(item.uri))
                .setFrameRate(outputFrameRate)
                .setEffects(Effects(emptyList(), listOf(effect)))
                .build(),
            target.absolutePath
        )
    }
}
