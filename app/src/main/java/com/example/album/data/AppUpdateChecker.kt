package com.example.album.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI

/**
 * A published release.
 *
 * [versionCode] is -1 when the source does not carry one (the GitHub Releases
 * API does not). In that case the version name is compared segment by segment.
 */
data class AppRelease(
    val versionCode: Long,
    val versionName: String,
    val downloadUrl: String,
    val notes: String
)

/** How a published release relates to the installed build. */
enum class UpdateStatus {
    /** The published release is newer than the installed build. */
    Available,

    /** The installed build is already the published one. */
    UpToDate,

    /**
     * The published release is *older* than the installed build, which means
     * the update source has not been refreshed for this version yet. Reporting
     * "already up to date" here would hide a broken release pipeline.
     */
    SourceBehind
}

object AppUpdateChecker {
    private const val MAX_METADATA_BYTES = 256 * 1024

    fun fetch(updateUrl: String): AppRelease {
        require(isSecureHttpsUrl(updateUrl)) { "Update URL must use HTTPS" }
        val connection = (URL(updateUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Album-Android")
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) error("Update server returned HTTP $status")
            require(isSecureHttpsUrl(connection.url.toString())) { "Update response must use HTTPS" }
            val body = connection.inputStream.use { input ->
                readLimited(input, MAX_METADATA_BYTES).toString(Charsets.UTF_8)
            }
            parse(JSONObject(body), updateUrl)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Reads both shapes the app may be pointed at:
     *
     * 1. the manifest published by `scripts/write-update-manifest.ps1`
     *    (`versionCode` / `versionName` / `downloadUrl` / `notes`), and
     * 2. a GitHub Releases API response (`tag_name`, `assets`, `body`),
     *    which is what a repository-level endpoint returns.
     */
    internal fun parse(json: JSONObject, sourceUrl: String): AppRelease {
        val tagName = json.optString("tag_name").trim()
        if (tagName.isNotEmpty()) {
            val versionName = normalizeVersionName(tagName)
            require(versionName.isNotBlank()) { "Missing or invalid release tag" }
            val assetUrl = firstApkAssetUrl(json.optJSONArray("assets"))
            val fallbackUrl = json.optString("html_url")
            val downloadUrl = assetUrl.ifBlank { fallbackUrl }
            require(downloadUrl.isBlank() || isSecureHttpsUrl(downloadUrl)) {
                "Download URL must use HTTPS"
            }
            return AppRelease(
                versionCode = json.optLong("versionCode", -1L),
                versionName = versionName,
                downloadUrl = downloadUrl,
                notes = json.optString("body").ifBlank { json.optString("name") }
            )
        }

        val element = json.optJSONArray("elements")?.optJSONObject(0)
        val versionCode = json.optLong("versionCode", element?.optLong("versionCode", -1L) ?: -1L)
        val versionName = json.optString("versionName")
            .ifBlank { element?.optString("versionName").orEmpty() }
            .ifBlank { if (versionCode >= 0L) versionCode.toString() else "" }
        require(versionCode >= 0L || versionName.isNotBlank()) {
            "Missing or invalid versionCode"
        }
        val downloadUrl = json.optString("downloadUrl")
            .ifBlank { json.optString("url") }
            .ifBlank {
                element?.optString("outputFile")?.takeIf { it.isNotBlank() }?.let { outputFile ->
                    runCatching { URI(sourceUrl).resolve(outputFile).toString() }.getOrNull().orEmpty()
                }.orEmpty()
            }
        require(downloadUrl.isBlank() || isSecureHttpsUrl(downloadUrl)) {
            "Download URL must use HTTPS"
        }
        return AppRelease(
            versionCode = versionCode,
            versionName = versionName,
            downloadUrl = downloadUrl,
            notes = json.optString("notes")
        )
    }

    /** Drops a leading `v`/`V` so `v1.2.1` and the APK `versionName` agree. */
    internal fun normalizeVersionName(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").trim()

    fun status(
        release: AppRelease,
        installedVersionName: String,
        installedVersionCode: Long
    ): UpdateStatus {
        if (release.versionCode >= 0L && installedVersionCode >= 0L) {
            return when {
                release.versionCode > installedVersionCode -> UpdateStatus.Available
                release.versionCode < installedVersionCode -> UpdateStatus.SourceBehind
                else -> UpdateStatus.UpToDate
            }
        }
        return when (compareVersionNames(release.versionName, installedVersionName)) {
            in 1..Int.MAX_VALUE -> UpdateStatus.Available
            in Int.MIN_VALUE..-1 -> UpdateStatus.SourceBehind
            else -> UpdateStatus.UpToDate
        }
    }

    /**
     * Compares dotted version names (`1.2.1` > `1.1.91`) so a source without a
     * numeric version code still updates correctly. Non-numeric segments are
     * ignored, which keeps tags such as `v1.2.1-beta` comparable.
     */
    internal fun compareVersionNames(left: String, right: String): Int {
        val leftParts = numericSegments(left)
        val rightParts = numericSegments(right)
        val size = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val a = leftParts.getOrElse(index) { 0L }
            val b = rightParts.getOrElse(index) { 0L }
            if (a != b) return if (a > b) 1 else -1
        }
        return 0
    }

    private fun numericSegments(value: String): List<Long> =
        normalizeVersionName(value)
            .split('.')
            .map { part -> part.takeWhile(Char::isDigit) }
            .filter { it.isNotEmpty() }
            .map(String::toLong)

    private fun firstApkAssetUrl(assets: JSONArray?): String {
        if (assets == null) return ""
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val url = asset.optString("browser_download_url")
            if (url.isNotBlank() && isSecureHttpsUrl(url)) return url
        }
        return ""
    }

    internal fun isSecureHttpsUrl(raw: String): Boolean = runCatching {
        val uri = URI(raw.trim())
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.fragment == null
    }.getOrDefault(false)

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "Update metadata is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
