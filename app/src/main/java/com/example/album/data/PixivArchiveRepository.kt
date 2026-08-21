package com.example.album.data

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.CRC32
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import org.json.JSONObject

data class PixivMetadata(
    val title: String,
    val artist: String,
    val artistId: String,
    val tags: List<String>
)

enum class PixivArchiveStatus { Ready, Warning, Archived, Failed }

data class PixivArchiveRecord(
    val uri: Uri,
    val filename: String,
    val mimeType: String,
    val pid: String?,
    val page: Int,
    val metadata: PixivMetadata?,
    val status: PixivArchiveStatus,
    val message: String = "",
    val dateModified: Long = 0L
) {
    val canArchive: Boolean get() = pid != null && metadata != null && status != PixivArchiveStatus.Archived
}

enum class PixivArchivePhase { Discover, Metadata, Ready, Folders, Tags, Move, Complete, Error }

data class PixivArchiveProgress(
    val phase: PixivArchivePhase,
    val completed: Int,
    val total: Int,
    val failed: Int,
    val currentFile: String = "",
    val currentArtist: String = "",
    val message: String,
    val log: String = "",
    val itemProgress: Float = 0f
)

data class PixivArchiveResult(
    val records: List<PixivArchiveRecord>,
    val completed: Int,
    val failed: Int
)

data class PixivLibrarySnapshot(
    val items: List<MediaItem>,
    val tagsByUri: Map<String, List<String>>,
    val folderNames: Set<String>,
    val sourceFolderName: String,
    val sourceConfigured: Boolean,
    val targetConfigured: Boolean
)

class PixivArchiveRepository(private val context: Context) {
    private val metadataCache = mutableMapOf<String, PixivMetadata>()
    private val metadataCacheLock = Mutex()
    private val metadataPreferences = context.getSharedPreferences("pixiv_metadata_cache", Context.MODE_PRIVATE)
    // WebView creation and JS callbacks are main-thread bound on real devices.
    // Keep fallback lookups single-file to avoid starving the UI thread.
    private val webViewFallbackSemaphore = Semaphore(1)

    fun hasAuthenticatedSession(): Boolean = context.getSharedPreferences("pixiv_archive", Context.MODE_PRIVATE)
        .getBoolean("session_verified", false) || hasPixivSessionCookie(pixivCookies())

    suspend fun verifyAuthenticatedSession(): Boolean {
        if (context.getSharedPreferences("pixiv_archive", Context.MODE_PRIVATE)
                .getBoolean("session_verified", false)
        ) return true
        // WebView's CookieManager is backed by the UI process on real devices.
        // Read it on the main thread before doing the network request off-thread.
        val cookies = withContext(Dispatchers.Main.immediate) { pixivCookies() }
            ?.takeIf(::hasPixivSessionCookie)
            ?: return false
        return withContext(Dispatchers.IO) { runCatching {
            val connection = (URL("https://www.pixiv.net/ajax/user/self?lang=zh").openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Album/1.0")
                setRequestProperty("Referer", "https://www.pixiv.net/")
                setRequestProperty("Origin", "https://www.pixiv.net")
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                setRequestProperty("Cookie", cookies)
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching false
                val root = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                !root.optBoolean("error", true) && (
                    root.optJSONObject("userData")?.optString("id")?.isNotBlank() == true ||
                        root.optJSONObject("body")?.optString("userId")?.isNotBlank() == true
                    )
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false) }
    }

    fun clearAuthenticatedSession() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        context.getSharedPreferences("pixiv_archive", Context.MODE_PRIVATE)
            .edit().putBoolean("session_verified", false).apply()
    }

    private fun pixivCookies(): String? = listOf(
        CookieManager.getInstance().getCookie("https://www.pixiv.net/"),
        CookieManager.getInstance().getCookie("https://accounts.pixiv.net/")
    ).filterNot { it.isNullOrBlank() }.joinToString(";") { it.orEmpty() }.ifBlank { null }

    suspend fun readTags(item: MediaItem): List<String> = withContext(Dispatchers.IO) {
        readEmbeddedTags(item.uri, item.mimeType).ifEmpty {
            findArchivedFile(item.uri)?.let { (file, parent) ->
                parent.findFile("${file.name}.pixiv.json")?.let(::readSidecarTags).orEmpty()
            }.orEmpty()
        }
    }

    suspend fun updateTags(item: MediaItem, tags: List<String>): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            when {
                item.mimeType == "image/png" || item.name.endsWith(".png", ignoreCase = true) -> writePngTags(item.uri, tags)
                item.mimeType == "image/jpeg" && item.uri.scheme == "file" && item.uri.path != null -> ExifInterface(item.uri.path!!).apply {
                    setAttribute(ExifInterface.TAG_USER_COMMENT, tags.joinToString(", "))
                    saveAttributes()
                }
                item.mimeType == "image/jpeg" -> context.contentResolver.openFileDescriptor(item.uri, "rw")?.use { descriptor ->
                    ExifInterface(descriptor.fileDescriptor).apply {
                        setAttribute(ExifInterface.TAG_USER_COMMENT, tags.joinToString(", "))
                        saveAttributes()
                    }
                } ?: error("无法写入 JPEG 信息")
                else -> {
                    val (file, parent) = findArchivedFile(item.uri) ?: error("无法定位归档文件")
                    val sidecarName = "${file.name}.pixiv.json"
                    val sidecar = parent.findFile(sidecarName) ?: parent.createFile("application/json", sidecarName)
                        ?: error("无法创建 Tag 信息文件")
                    val json = JSONObject().apply { put("tags", org.json.JSONArray(tags)) }
                    openMediaOutputStream(context, sidecar.uri, "wt")?.bufferedWriter()?.use { writer ->
                        writer.write(json.toString(2))
                    } ?: error("无法写入 Tag 信息")
                }
            }
            true
        }.getOrDefault(false)
    }

    suspend fun loadLibrary(fallbackDefaultItems: List<MediaItem>): PixivLibrarySnapshot = withContext(Dispatchers.IO) {
        val preferences = context.getSharedPreferences("pixiv_archive", Context.MODE_PRIVATE)
        val sourceUri = preferences.getString("source_uri", null)?.let(Uri::parse)
        val targetUri = preferences.getString("target_uri", null)?.let(Uri::parse)
        val tagsByUri = mutableMapOf<String, List<String>>()
        val sourceFolderName = sourceUri
            ?.let { treeDocumentFile(it)?.name?.takeIf(String::isNotBlank) }
            ?: "Pixiv"
        val defaultItems = sourceUri?.let { uri ->
                treeDocumentFile(uri)?.let { root ->
                buildList { collectLibraryImages(root, sourceFolderName, this, tagsByUri) }
            }
        } ?: fallbackDefaultItems.map { item ->
            readEmbeddedTags(item.uri, item.mimeType).takeIf { it.isNotEmpty() }
                ?.let { tagsByUri[item.uri.toString()] = it }
            item.copy(folder = sourceFolderName)
        }
        val archivedItems = targetUri?.let { uri ->
            treeDocumentFile(uri)?.let { root ->
                buildList {
                    runCatching { root.listFiles() }.getOrDefault(emptyArray())
                        .filter { it.isDirectory }
                        .forEach { artistFolder ->
                            val artistName = artistFolder.name?.takeIf { it.isNotBlank() } ?: return@forEach
                            collectLibraryImages(artistFolder, artistName, this, tagsByUri)
                        }
                }
            }
        }.orEmpty()
        val archivedFolderNames = targetUri?.let { uri ->
            treeDocumentFile(uri)?.listFiles().orEmpty()
                .filter { it.isDirectory }
                .mapNotNull { it.name?.takeIf(String::isNotBlank) }
                .toSet()
        }.orEmpty()
        val archivedUris = archivedItems.mapTo(hashSetOf()) { it.uri.toString() }
        PixivLibrarySnapshot(
            items = (defaultItems.filterNot { it.uri.toString() in archivedUris } + archivedItems)
                .distinctBy { it.uri.toString() },
            tagsByUri = tagsByUri,
            folderNames = setOf(sourceFolderName) + archivedFolderNames,
            sourceFolderName = sourceFolderName,
            sourceConfigured = sourceUri != null,
            targetConfigured = targetUri != null
        )
    }

    suspend fun scan(
        sourceTree: Uri,
        maxItems: Int = Int.MAX_VALUE,
        onProgress: suspend (PixivArchiveProgress) -> Unit = {},
        onRecord: suspend (PixivArchiveRecord) -> Unit = {}
    ): List<PixivArchiveRecord> = withContext(Dispatchers.IO) {
        onProgress(PixivArchiveProgress(PixivArchivePhase.Discover, 0, 0, 0, message = "正在读取来源目录"))
        val root = treeDocumentFile(sourceTree)
            ?: throw IllegalStateException("无法读取 Pixiv 来源目录")
        val files = mutableListOf<DocumentFile>()
        runCatching { collectImages(root, files) }
            .getOrElse { throw IllegalStateException("来源目录读取失败，请检查访问权限", it) }
        files.sortBy { it.name.orEmpty().lowercase(Locale.ROOT) }
        val filesToScan = files.take(maxItems.coerceAtLeast(1))
        onProgress(PixivArchiveProgress(PixivArchivePhase.Metadata, 0, filesToScan.size, 0, message = "找到 ${filesToScan.size} 张图片，正在查询作品信息", log = "已读取 ${filesToScan.size} 个文件"))
        val parsed = filesToScan.map { file -> file to parsePixivFilename(file.name.orEmpty()) }
        val hasUncachedPid = parsed.any { (file, details) ->
            details?.first?.let { pid -> readPersistedMetadata(pid) == null } == true
        }
        if (hasUncachedPid && !verifyAuthenticatedSession()) {
            throw IllegalStateException("Pixiv 登录已失效，请先登录 Pixiv 账号")
        }
        // Keep the request rate modest; Pixiv may throttle bursts even for a
        // fully authenticated account, especially on real-device networks.
        val semaphore = Semaphore(2)
        var scanned = 0
        var warnings = 0
        var lastProgressAt = 0L
        val records = coroutineScope {
            val metadataRequests = parsed.mapNotNull { it.second?.first }.distinct().associateWith { pid ->
                async { semaphore.withPermit { resolveMetadata(pid) } }
            }
            parsed.map { (file, details) ->
                val pid = details?.first
                onProgress(PixivArchiveProgress(
                    phase = PixivArchivePhase.Metadata,
                    completed = scanned - warnings,
                    total = filesToScan.size,
                    failed = warnings,
                    currentFile = file.name.orEmpty(),
                    message = "正在查询 Pixiv 信息"
                ))
                val info = pid?.let { metadataRequests[it]?.await() }
                val record = PixivArchiveRecord(
                    uri = file.uri,
                    filename = file.name.orEmpty(),
                    mimeType = file.type ?: context.contentResolver.getType(file.uri) ?: "image/*",
                    pid = pid,
                    page = details?.second ?: 0,
                    metadata = info,
                    status = if (pid != null && info != null) PixivArchiveStatus.Ready else PixivArchiveStatus.Warning,
                    message = when {
                        pid == null -> "文件名不符合 Pixiv 规则，需手动确认"
                        info == null -> "无法读取公开作品信息，需手动确认"
                        else -> "等待归档"
                    },
                    dateModified = file.lastModified().takeIf { it > 0L } ?: 0L
                )
                scanned++
                if (record.status == PixivArchiveStatus.Warning) warnings++
                val now = SystemClock.elapsedRealtime()
                if (now - lastProgressAt >= PROGRESS_UPDATE_INTERVAL_MS || scanned == filesToScan.size) {
                    lastProgressAt = now
                    onProgress(PixivArchiveProgress(
                        phase = PixivArchivePhase.Metadata,
                        completed = scanned - warnings,
                        total = filesToScan.size,
                        failed = warnings,
                        currentFile = record.filename,
                        currentArtist = info?.artist.orEmpty(),
                        message = if (info != null) "已识别 PID $pid" else "无法识别作品信息",
                        log = if (info != null) "${record.filename} -> ${info.artist}" else "${record.filename} · 需手动确认",
                        itemProgress = 1f
                    ))
                }
                onRecord(record)
                record
            }
        }
        onProgress(PixivArchiveProgress(PixivArchivePhase.Ready, records.size - warnings, records.size, warnings, message = "${records.size - warnings} 张可归档，$warnings 张需确认", log = "扫描完成"))
        records
    }

    suspend fun rescan(
        records: List<PixivArchiveRecord>,
        maxItems: Int = Int.MAX_VALUE,
        onProgress: suspend (PixivArchiveProgress) -> Unit = {},
        onRecord: suspend (PixivArchiveRecord) -> Unit = {}
    ): List<PixivArchiveRecord> = withContext(Dispatchers.IO) {
        val pending = records
            .filter { it.pid != null }
            .take(maxItems.coerceAtLeast(1))
        val hasUncachedPid = pending.any { it.pid?.let { pid -> readPersistedMetadata(pid) == null } == true }
        if (hasUncachedPid && !verifyAuthenticatedSession()) {
            throw IllegalStateException("Pixiv 登录已失效，请先登录 Pixiv 账号")
        }
        val files = pending.mapNotNull { record ->
            DocumentFile.fromSingleUri(context, record.uri)?.takeIf { it.exists() }?.let { file ->
                file to record
            }
        }
        onProgress(PixivArchiveProgress(PixivArchivePhase.Metadata, 0, files.size, 0, message = "正在重新查询 ${files.size} 张图片"))
        val semaphore = Semaphore(2)
        val requests = files.mapNotNull { (_, record) -> record.pid }
            .distinct()
            .associateWith { pid -> async { semaphore.withPermit { resolveMetadata(pid) } } }
        var completed = 0
        var failed = 0
        var lastProgressAt = 0L
        val updated = files.map { (file, old) ->
            val info = old.pid?.let { requests[it]?.await() }
            completed++
            if (info == null) failed++
            val result = old.copy(
                filename = file.name ?: old.filename,
                mimeType = file.type ?: old.mimeType,
                metadata = info,
                status = if (old.pid != null && info != null) PixivArchiveStatus.Ready else PixivArchiveStatus.Warning,
                message = when {
                    old.pid == null -> "文件名不符合 Pixiv 规则，需手动确认"
                    info == null -> "无法读取公开作品信息，需手动确认"
                    else -> "等待归档"
                },
                dateModified = file.lastModified().takeIf { it > 0L } ?: old.dateModified
            )
            val now = SystemClock.elapsedRealtime()
            if (now - lastProgressAt >= PROGRESS_UPDATE_INTERVAL_MS || completed == files.size) {
                lastProgressAt = now
                onProgress(PixivArchiveProgress(
                    phase = PixivArchivePhase.Metadata,
                    completed = completed - failed,
                    total = files.size,
                    failed = failed,
                    currentFile = result.filename,
                    currentArtist = info?.artist.orEmpty(),
                    message = if (info != null) "已识别 PID ${result.pid}" else "无法识别作品信息"
                ))
            }
            onRecord(result)
            result
        }
        updated
    }

    suspend fun archive(
        records: List<PixivArchiveRecord>,
        targetTree: Uri,
        keepOriginalFilename: Boolean,
        writeTags: Boolean,
        copyInsteadOfMove: Boolean,
        onProgress: suspend (PixivArchiveProgress) -> Unit
    ): PixivArchiveResult = withContext(Dispatchers.IO) {
        val total = records.count { it.canArchive }
        val root = treeDocumentFile(targetTree, createMissing = true)
        if (root == null) {
            onProgress(PixivArchiveProgress(PixivArchivePhase.Error, 0, total, total, message = "无法读取归档目标目录", log = "目标目录读取失败，可重新选择后重试"))
            return@withContext PixivArchiveResult(
                records.map { if (it.canArchive) it.copy(status = PixivArchiveStatus.Failed, message = "目标目录不可用，来源文件已保留") else it },
                0,
                total
            )
        }
        var completed = 0
        var failed = 0
        onProgress(PixivArchiveProgress(PixivArchivePhase.Folders, 0, total, 0, message = "正在准备画师目录"))
        val updated = records.map { record ->
            if (!record.canArchive) return@map record
            val metadata = requireNotNull(record.metadata)
            onProgress(PixivArchiveProgress(PixivArchivePhase.Folders, completed, total, failed, record.filename, metadata.artist, "正在创建画师目录", "创建目录 ${metadata.artist} [${metadata.artistId}]", itemProgress = 0.05f))
            val folderName = sanitize("${metadata.artist} [${metadata.artistId}]")
            val folder = root.findFile(folderName)?.takeIf { it.isDirectory } ?: root.createDirectory(folderName)
            val targetName = if (keepOriginalFilename) record.filename else canonicalName(record)
            val target = folder?.let { createUniqueFile(it, record.mimeType, targetName) }
            val copied = target != null && copy(record.uri, target.uri) { copyProgress ->
                onProgress(PixivArchiveProgress(
                    PixivArchivePhase.Move,
                    completed,
                    total,
                    failed,
                    record.filename,
                    metadata.artist,
                    if (copyInsteadOfMove) "正在复制到画师目录" else "正在移动到画师目录",
                    itemProgress = (0.1f + copyProgress * 0.65f).coerceIn(0.1f, 0.75f)
                ))
            }
            if (copied && writeTags) onProgress(PixivArchiveProgress(PixivArchivePhase.Tags, completed, total, failed, record.filename, metadata.artist, "正在写入 Pixiv tags", itemProgress = 0.85f))
            if (copied) onProgress(
                PixivArchiveProgress(
                    PixivArchivePhase.Move,
                    completed,
                    total,
                    failed,
                    record.filename,
                    metadata.artist,
                    if (copyInsteadOfMove) "正在复制到画师目录" else "正在移动到画师目录",
                    itemProgress = 0.9f
                )
            )
            var sidecar: DocumentFile? = null
            val moved = copied && runCatching {
                if (writeTags) sidecar = writeMetadata(target, folder, metadata)
                copyInsteadOfMove || deleteSource(record.uri)
            }.getOrDefault(false)
            val next = if (moved) {
                completed++
                clearMetadataCache(record.pid)
                record.copy(status = PixivArchiveStatus.Archived, message = "已归档至 $folderName")
            } else {
                if (copied) {
                    target?.delete()
                    sidecar?.delete()
                }
                failed++
                record.copy(status = PixivArchiveStatus.Failed, message = "归档失败，来源文件已保留")
            }
            onProgress(PixivArchiveProgress(
                phase = PixivArchivePhase.Move,
                completed = completed,
                total = total,
                failed = failed,
                currentFile = record.filename,
                currentArtist = metadata.artist,
                message = if (moved) {
                    if (copyInsteadOfMove) "文件复制完成" else "文件移动完成"
                } else "归档失败，来源文件已保留",
                log = "${record.filename} · ${if (moved) "完成" else "失败，可重试"}",
                itemProgress = 1f
            ))
            next
        }
        onProgress(PixivArchiveProgress(if (failed == 0) PixivArchivePhase.Complete else PixivArchivePhase.Error, completed, total, failed, message = if (failed == 0) "已完成 $completed 张图片" else "归档已暂停，可检查后重试", log = if (failed == 0) "归档完成" else "操作已停止，可检查后重试"))
        PixivArchiveResult(updated, completed, failed)
    }

    private suspend fun clearMetadataCache(pid: String?) {
        if (pid.isNullOrBlank()) return
        metadataCacheLock.withLock { metadataCache.remove(pid) }
        metadataPreferences.edit().remove(pid).apply()
    }

    private fun collectImages(file: DocumentFile, output: MutableList<DocumentFile>) {
        // Android's media provider can expose trashed entries in a tree. They
        // are not user-visible source files and may retain another image's name.
        if (file.name.orEmpty().startsWith(".trashed", ignoreCase = true)) return
        if (file.isDirectory) {
            runCatching { file.listFiles() }.getOrDefault(emptyArray()).forEach { collectImages(it, output) }
            return
        }
        val mime = file.type ?: context.contentResolver.getType(file.uri).orEmpty()
        val extension = file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        // Some emulator document providers return an empty or generic MIME type.
        // Use the filename as a fallback so valid Pixiv exports are not skipped.
        val imageByExtension = extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")
        if (mime.startsWith("image/") || imageByExtension) output += file
    }

    private fun collectLibraryImages(
        file: DocumentFile,
        folderName: String,
        output: MutableList<MediaItem>,
        tagsByUri: MutableMap<String, List<String>>,
        siblingSidecars: Map<String, DocumentFile> = emptyMap()
    ) {
        if (file.isDirectory) {
            val children = runCatching { file.listFiles() }.getOrDefault(emptyArray())
            val sidecars = children.filter { it.isFile && it.name.orEmpty().endsWith(".pixiv.json", ignoreCase = true) }
                .associateBy { it.name.orEmpty() }
            children.filterNot { it.name.orEmpty().endsWith(".pixiv.json", ignoreCase = true) }
                .forEach { child -> collectLibraryImages(child, folderName, output, tagsByUri, sidecars) }
            return
        }
        val mime = file.type ?: context.contentResolver.getType(file.uri).orEmpty()
        if (!mime.startsWith("image/")) return
        val modified = file.lastModified().takeIf { it > 0 } ?: 0L
        val item = MediaItem(
            id = file.uri.toString().hashCode().toLong() and 0xffffffffL,
            uri = file.uri,
            name = file.name ?: "未命名图片",
            folder = folderName,
            dateTaken = modified,
            mimeType = mime.ifBlank { "image/*" },
            size = file.length(),
            dateModified = modified / 1000L,
            isDocument = true
        )
        output += item
        val sidecar = siblingSidecars["${file.name}.pixiv.json"]
        val tags = readEmbeddedTags(file.uri, mime).ifEmpty { sidecar?.let(::readSidecarTags).orEmpty() }
        if (tags.isNotEmpty()) tagsByUri[item.uri.toString()] = tags
    }

    private fun readEmbeddedTags(uri: Uri, mimeType: String): List<String> = runCatching {
        when {
            mimeType == "image/jpeg" -> if (uri.scheme == "file" && uri.path != null) {
                ExifInterface(uri.path!!).getAttribute(ExifInterface.TAG_USER_COMMENT)
                    ?.split(',')?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
            } else context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                ExifInterface(descriptor.fileDescriptor).getAttribute(ExifInterface.TAG_USER_COMMENT)
                    ?.split(',')?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
            }.orEmpty()
            mimeType == "image/png" -> openMediaInputStream(context, uri)?.use(::readPngTags).orEmpty()
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    private fun readSidecarTags(sidecar: DocumentFile): List<String> = runCatching {
        openMediaInputStream(context, sidecar.uri)?.bufferedReader()?.use { reader ->
            val tags = JSONObject(reader.readText()).optJSONArray("tags") ?: return@use emptyList()
            buildList {
                repeat(tags.length()) { index -> tags.optString(index).takeIf(String::isNotBlank)?.let(::add) }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun findArchivedFile(uri: Uri): Pair<DocumentFile, DocumentFile>? {
        val targetUri = context.getSharedPreferences("pixiv_archive", Context.MODE_PRIVATE)
            .getString("target_uri", null)?.let(Uri::parse) ?: return null
        val root = treeDocumentFile(targetUri) ?: return null
        fun find(directory: DocumentFile): Pair<DocumentFile, DocumentFile>? {
            runCatching { directory.listFiles() }.getOrDefault(emptyArray()).forEach { child ->
                if (child.uri == uri) return child to directory
                if (child.isDirectory) find(child)?.let { return it }
            }
            return null
        }
        return find(root)
    }

    private fun treeDocumentFile(uri: Uri, createMissing: Boolean = false): DocumentFile? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            val relativePath = documentId?.substringAfter("primary:", "")?.trim('/')
            if (!relativePath.isNullOrBlank()) {
                val directory = File(Environment.getExternalStorageDirectory(), relativePath)
                if (directory.exists() || (createMissing && directory.mkdirs())) return DocumentFile.fromFile(directory)
            }
        }
        return DocumentFile.fromTreeUri(context, uri)
    }

    private fun deleteSource(uri: Uri): Boolean = when (uri.scheme) {
        "file" -> uri.path?.let(::File)?.delete() == true
        else -> DocumentFile.fromSingleUri(context, uri)?.delete() == true
    }

    private suspend fun resolveMetadata(pid: String): PixivMetadata? {
        metadataCacheLock.withLock { metadataCache[pid] }?.let { return it }
        readPersistedMetadata(pid)?.let { cached ->
            metadataCacheLock.withLock { metadataCache[pid] = cached }
            return cached
        }
        // CookieManager is UI-thread backed on real devices. Reading it from
        // the IO dispatcher can return an empty/stale cookie intermittently.
        val cookies = withContext(Dispatchers.Main.immediate) {
            CookieManager.getInstance().getCookie("https://www.pixiv.net/")
                ?.takeIf { it.isNotBlank() }
        }
        // Try the authenticated endpoint, then the public endpoint. A work can
        // be viewable publicly even when the signed-in request is rejected.
        val resolved = cookies?.let { requestMetadataWithRetry(pid, it) }
            ?: requestMetadataWithRetry(pid, null)
            ?: requestMetadataFromWebView(pid)
        if (resolved != null) {
            metadataCacheLock.withLock { metadataCache[pid] = resolved }
            writePersistedMetadata(pid, resolved)
        }
        return resolved
    }

    private fun readPersistedMetadata(pid: String): PixivMetadata? = runCatching {
        val raw = metadataPreferences.getString(pid, null) ?: return null
        val json = JSONObject(raw)
        val tagsJson = json.optJSONArray("tags")
        val tags = buildList {
            if (tagsJson != null) repeat(tagsJson.length()) {
                tagsJson.optString(it).takeIf(String::isNotBlank)?.let(::add)
            }
        }
        PixivMetadata(
            title = json.optString("title").ifBlank { "PID $pid" },
            artist = json.optString("artist").ifBlank { "未知画师" },
            artistId = json.optString("artistId").ifBlank { "unknown" },
            tags = tags
        )
    }.getOrNull()

    private fun writePersistedMetadata(pid: String, metadata: PixivMetadata) {
        runCatching {
            val tags = org.json.JSONArray().apply { metadata.tags.forEach(::put) }
            metadataPreferences.edit().putString(
                pid,
                JSONObject().apply {
                    put("title", metadata.title)
                    put("artist", metadata.artist)
                    put("artistId", metadata.artistId)
                    put("tags", tags)
                }.toString()
            ).apply()
        }
    }

    private suspend fun requestMetadataWithRetry(pid: String, cookies: String?): PixivMetadata? {
        repeat(2) { attempt ->
            requestMetadata(pid, cookies)?.let { return it }
            if (attempt == 0) delay(650L)
        }
        return null
    }

    private fun requestMetadata(pid: String, cookies: String?): PixivMetadata? = runCatching {
        val connection = (URL("https://www.pixiv.net/ajax/illust/$pid?lang=zh").openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 5_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Album/1.0")
            setRequestProperty("Referer", "https://www.pixiv.net/artworks/$pid")
            setRequestProperty("Origin", "https://www.pixiv.net")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            cookies?.let { setRequestProperty("Cookie", it) }
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            val root = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            if (root.optBoolean("error", true)) return@runCatching null
            parseArtworkMetadata(pid, root)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /**
     * Some real-device networks reject the app's native TLS/HTTP request while
     * the logged-in WebView can still fetch the same Pixiv endpoint. Keep the
     * WebView fallback isolated so normal scans do not create a WebView unless
     * the native request actually failed.
     */
    private suspend fun requestMetadataFromWebView(pid: String): PixivMetadata? =
        withContext(Dispatchers.Main.immediate) {
            webViewFallbackSemaphore.withPermit {
                withTimeoutOrNull(12_000L) {
                kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                    val webView = WebView(context)
                    var completed = false
                    fun finish(result: PixivMetadata?) {
                        if (completed) return
                        completed = true
                        webView.stopLoading()
                        webView.destroy()
                        if (continuation.isActive) continuation.resume(result)
                    }
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(webView, true)
                    }
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            val host = Uri.parse(url).host.orEmpty().lowercase(Locale.ROOT)
                            if (host != "www.pixiv.net" && host != "pixiv.net") return
                            view.evaluateJavascript(
                                """fetch('/ajax/illust/$pid?lang=zh',{credentials:'include',cache:'no-store'}).then(r=>r.text()).catch(()=> '')"""
                            ) { raw ->
                                val jsonText = runCatching {
                                    JSONObject("{\"value\":$raw}").optString("value")
                                }.getOrNull().orEmpty()
                                val metadata = runCatching { parseArtworkMetadata(pid, JSONObject(jsonText)) }.getOrNull()
                                finish(metadata)
                            }
                        }
                    }
                    continuation.invokeOnCancellation {
                        webView.post { webView.stopLoading(); webView.destroy() }
                    }
                    CookieManager.getInstance().flush()
                    webView.loadUrl("https://www.pixiv.net/artworks/$pid")
                }
                }
            }
        }

    private fun parseArtworkMetadata(pid: String, root: JSONObject): PixivMetadata? {
        if (root.optBoolean("error", true)) return null
        val body = root.optJSONObject("body") ?: return null
        val tagArray = body.optJSONObject("tags")?.optJSONArray("tags")
        val tags = buildList {
            if (tagArray != null) repeat(tagArray.length()) { index ->
                tagArray.optJSONObject(index)?.optString("tag")?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        return PixivMetadata(
            title = body.optString("illustTitle").ifBlank { "PID $pid" },
            artist = body.optString("userName").ifBlank { "未知画师" },
            artistId = body.optString("userId").ifBlank { "unknown" },
            tags = tags
        )
    }

    private suspend fun copy(source: Uri, target: Uri, onProgress: suspend (Float) -> Unit): Boolean {
        return try {
            val totalBytes = contentResolverSize(source)
            openMediaInputStream(context, source).use { input ->
                requireNotNull(input)
                openMediaOutputStream(context, target, "w").use { output ->
                    requireNotNull(output)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copiedBytes = 0L
                    var lastReportedBytes = 0L
                    var lastReportedAt = SystemClock.uptimeMillis()
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copiedBytes += count
                        val now = SystemClock.uptimeMillis()
                        if (totalBytes > 0L && (copiedBytes == totalBytes || copiedBytes - lastReportedBytes >= 256 * 1024 || now - lastReportedAt >= PROGRESS_UPDATE_INTERVAL_MS)) {
                            onProgress((copiedBytes.toFloat() / totalBytes).coerceIn(0f, 1f))
                            lastReportedBytes = copiedBytes
                            lastReportedAt = now
                        }
                    }
                    if (totalBytes <= 0L) onProgress(1f)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun contentResolverSize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    private fun writeMetadata(target: DocumentFile, folder: DocumentFile, metadata: PixivMetadata): DocumentFile? {
        if (target.type == "image/jpeg") {
            if (target.uri.scheme == "file" && target.uri.path != null) {
                ExifInterface(target.uri.path!!).apply {
                    setAttribute(ExifInterface.TAG_USER_COMMENT, metadata.tags.joinToString(", "))
                    saveAttributes()
                }
            } else {
                val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(target.uri, "rw")) {
                    "无法写入 JPEG 信息"
                }
                descriptor.use {
                    ExifInterface(descriptor.fileDescriptor).apply {
                        setAttribute(ExifInterface.TAG_USER_COMMENT, metadata.tags.joinToString(", "))
                        saveAttributes()
                    }
                }
            }
            return null
        }
        if (target.type == "image/png" || target.name.orEmpty().endsWith(".png", ignoreCase = true)) {
            writePngTags(target.uri, metadata.tags)
            return null
        }
        val sidecarName = "${target.name ?: "image"}.pixiv.json"
        val sidecar = requireNotNull(
            folder.findFile(sidecarName) ?: folder.createFile("application/json", sidecarName)
        ) { "无法创建 Tag 信息文件" }
        val json = JSONObject().apply {
            put("title", metadata.title)
            put("artist", metadata.artist)
            put("artistId", metadata.artistId)
            put("tags", org.json.JSONArray(metadata.tags))
        }
        requireNotNull(openMediaOutputStream(context, sidecar.uri, "wt")) {
            "无法写入 Tag 信息"
        }.bufferedWriter().use { it.write(json.toString(2)) }
        return sidecar
    }

    private fun writePngTags(target: Uri, tags: List<String>) {
        val temporary = File.createTempFile("pixiv-tags-", ".png", context.cacheDir)
        try {
            openMediaInputStream(context, target).use { input ->
                requireNotNull(input) { "无法读取 PNG 文件" }
                FileOutputStream(temporary).use { output -> embedPngTags(input, output, tags) }
            }
            FileInputStream(temporary).use { input ->
                openMediaOutputStream(context, target, "w").use { output ->
                    requireNotNull(output) { "无法写入 PNG 文件" }
                    input.copyTo(output)
                }
            }
        } finally {
            temporary.delete()
        }
    }

    private fun createUniqueFile(folder: DocumentFile, mimeType: String, requestedName: String): DocumentFile? {
        val dot = requestedName.lastIndexOf('.')
        val base = if (dot > 0) requestedName.substring(0, dot) else requestedName
        val extension = if (dot > 0) requestedName.substring(dot) else ""
        var candidate = requestedName
        var index = 1
        while (folder.findFile(candidate) != null) {
            candidate = "$base ($index)$extension"
            index++
        }
        return folder.createFile(mimeType, candidate)
    }

    private fun canonicalName(record: PixivArchiveRecord): String {
        val extension = record.filename.substringAfterLast('.', "jpg").lowercase(Locale.ROOT)
        return "${record.pid}_p${record.page}.$extension"
    }

    private fun sanitize(value: String): String = value.replace(Regex("[\\/:*?\"<>|]"), "_").trim().take(120)

}

private val STRICT_PIXIV_FILENAME = Regex("^illust_(\\d+)(?:_p(\\d+))?_\\d{8}_\\d{6}\\.+(?:jpe?g|png|webp|gif)$", RegexOption.IGNORE_CASE)
private val COMMON_PIXIV_FILENAME = Regex("^(?:illust_)?(\\d+)(?:_p(\\d+))?(?:_\\d{8}_\\d{6})?\\.(?:jpe?g|png|webp|gif)$", RegexOption.IGNORE_CASE)
private const val PROGRESS_UPDATE_INTERVAL_MS = 120L
internal fun parsePixivFilename(filename: String): Pair<String, Int>? {
    val strict = STRICT_PIXIV_FILENAME.matchEntire(filename)
    if (strict != null) return strict.groupValues[1] to (strict.groupValues[2].toIntOrNull() ?: 0)
    val common = COMMON_PIXIV_FILENAME.matchEntire(filename) ?: return null
    if (!filename.startsWith("illust_", ignoreCase = true) && !common.groupValues[2].isNotBlank()) return null
    return common.groupValues[1] to (common.groupValues[2].toIntOrNull() ?: 0)
}

internal fun hasPixivSessionCookie(cookies: String?): Boolean = cookies.orEmpty().split(';').any { cookie ->
    cookie.substringBefore('=').trim().equals("PHPSESSID", ignoreCase = true) &&
        cookie.substringAfter('=', "").isNotBlank()
}

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private const val PNG_TEXT_LIMIT = 16 * 1024 * 1024
private const val XMP_KEYWORD = "XML:com.adobe.xmp"
private const val TAGS_KEYWORD = "Keywords"

internal fun embedPngTags(input: InputStream, output: OutputStream, tags: List<String>) {
    val source = DataInputStream(BufferedInputStream(input))
    val target = DataOutputStream(BufferedOutputStream(output))
    val signature = ByteArray(PNG_SIGNATURE.size)
    source.readFully(signature)
    require(signature.contentEquals(PNG_SIGNATURE)) { "无效的 PNG 文件" }
    target.write(signature)
    var foundEnd = false
    while (!foundEnd) {
        val length = try {
            source.readInt()
        } catch (_: EOFException) {
            throw IllegalArgumentException("PNG 文件缺少 IEND 数据块")
        }
        require(length >= 0) { "PNG 数据块长度无效" }
        val type = ByteArray(4).also(source::readFully)
        val typeName = String(type, StandardCharsets.US_ASCII)
        val inspectText = (typeName == "iTXt" || typeName == "tEXt") && length <= PNG_TEXT_LIMIT
        if (inspectText) {
            val data = ByteArray(length).also(source::readFully)
            val crc = source.readInt()
            if (pngTextKeyword(data) != TAGS_KEYWORD && pngTextKeyword(data) != XMP_KEYWORD) {
                target.writeInt(length)
                target.write(type)
                target.write(data)
                target.writeInt(crc)
            }
            continue
        }
        if (typeName == "IEND") {
            writePngInternationalText(target, TAGS_KEYWORD, tags.joinToString(", "))
            writePngInternationalText(target, XMP_KEYWORD, buildTagsXmp(tags))
            foundEnd = true
        }
        target.writeInt(length)
        target.write(type)
        copyExactly(source, target, length)
        target.writeInt(source.readInt())
    }
    target.flush()
}

private fun pngTextKeyword(data: ByteArray): String {
    val separator = data.indexOf(0)
    if (separator <= 0) return ""
    return String(data, 0, separator, StandardCharsets.ISO_8859_1)
}

private fun writePngInternationalText(output: DataOutputStream, keyword: String, text: String) {
    val data = ByteArrayOutputStream().apply {
        write(keyword.toByteArray(StandardCharsets.ISO_8859_1))
        write(0)
        write(0)
        write(0)
        write(0)
        write(0)
        write(text.toByteArray(StandardCharsets.UTF_8))
    }.toByteArray()
    val type = "iTXt".toByteArray(StandardCharsets.US_ASCII)
    val crc = CRC32().apply {
        update(type)
        update(data)
    }
    output.writeInt(data.size)
    output.write(type)
    output.write(data)
    output.writeInt(crc.value.toInt())
}

private fun copyExactly(input: InputStream, output: OutputStream, length: Int) {
    var remaining = length
    val buffer = ByteArray(32 * 1024)
    while (remaining > 0) {
        val count = input.read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) throw EOFException("PNG 数据块不完整")
        output.write(buffer, 0, count)
        remaining -= count
    }
}

private fun buildTagsXmp(tags: List<String>): String {
    val xmpNamespace = "adobe:ns:meta/"
    val rdfNamespace = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    val dcNamespace = "http://purl.org/dc/elements/1.1/"
    val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().newDocument()
    val root = document.createElementNS(xmpNamespace, "x:xmpmeta").also(document::appendChild)
    val rdf = document.createElementNS(rdfNamespace, "rdf:RDF").also(root::appendChild)
    val description = document.createElementNS(rdfNamespace, "rdf:Description").also(rdf::appendChild)
    val subject = document.createElementNS(dcNamespace, "dc:subject").also(description::appendChild)
    val bag = document.createElementNS(rdfNamespace, "rdf:Bag").also(subject::appendChild)
    tags.forEach { tag ->
        document.createElementNS(rdfNamespace, "rdf:li").apply { textContent = tag }.also(bag::appendChild)
    }
    val output = ByteArrayOutputStream()
    TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
    }.transform(DOMSource(document), StreamResult(output))
    return output.toString(StandardCharsets.UTF_8.name())
}

private data class PngTextEntry(val keyword: String, val text: String)

internal fun readPngTags(input: InputStream): List<String> {
    val source = DataInputStream(BufferedInputStream(input))
    val signature = ByteArray(PNG_SIGNATURE.size).also(source::readFully)
    require(signature.contentEquals(PNG_SIGNATURE)) { "无效的 PNG 文件" }
    val entries = mutableListOf<PngTextEntry>()
    while (true) {
        val length = source.readInt()
        require(length >= 0) { "PNG 数据块长度无效" }
        val type = ByteArray(4).also(source::readFully)
        val typeName = String(type, StandardCharsets.US_ASCII)
        if ((typeName == "iTXt" || typeName == "tEXt") && length <= PNG_TEXT_LIMIT) {
            val data = ByteArray(length).also(source::readFully)
            source.readInt()
            parsePngTextEntry(typeName, data)?.let(entries::add)
        } else {
            skipExactly(source, length)
            source.readInt()
        }
        if (typeName == "IEND") break
    }
    entries.firstOrNull { it.keyword == XMP_KEYWORD }?.text?.let(::parseXmpTags)
        ?.takeIf { it.isNotEmpty() }?.let { return it }
    return entries.firstOrNull { it.keyword == TAGS_KEYWORD }?.text
        ?.split(Regex("\\s*,\\s*"))?.filter(String::isNotBlank).orEmpty()
}

private fun parsePngTextEntry(type: String, data: ByteArray): PngTextEntry? {
    val separator = data.indexOf(0)
    if (separator <= 0) return null
    val keyword = String(data, 0, separator, StandardCharsets.ISO_8859_1)
    if (type == "tEXt") {
        return PngTextEntry(keyword, String(data, separator + 1, data.size - separator - 1, StandardCharsets.ISO_8859_1))
    }
    var position = separator + 1
    if (position + 2 > data.size) return null
    val compressed = data[position].toInt() != 0
    position += 2
    val languageEnd = data.indexOfZero(position)
    if (languageEnd < 0) return null
    position = languageEnd + 1
    val translatedEnd = data.indexOfZero(position)
    if (translatedEnd < 0 || compressed) return null
    position = translatedEnd + 1
    return PngTextEntry(keyword, String(data, position, data.size - position, StandardCharsets.UTF_8))
}

private fun ByteArray.indexOfZero(startIndex: Int): Int {
    for (index in startIndex until size) if (this[index] == 0.toByte()) return index
    return -1
}

private fun parseXmpTags(xmp: String): List<String> = runCatching {
    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xmp.toByteArray(StandardCharsets.UTF_8)))
    val nodes = document.getElementsByTagNameNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "li")
    buildList {
        repeat(nodes.length) { index -> nodes.item(index).textContent?.takeIf(String::isNotBlank)?.let(::add) }
    }
}.getOrDefault(emptyList())

private fun skipExactly(input: InputStream, length: Int) {
    var remaining = length
    val buffer = ByteArray(32 * 1024)
    while (remaining > 0) {
        val count = input.read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) throw EOFException("PNG 数据块不完整")
        remaining -= count
    }
}
