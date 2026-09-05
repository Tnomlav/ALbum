package com.example.album.data

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI

data class AppRelease(
    val versionCode: Long,
    val versionName: String,
    val downloadUrl: String,
    val notes: String
)

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
            val json = JSONObject(body)
            val element = json.optJSONArray("elements")?.optJSONObject(0)
            val versionCode = json.optLong("versionCode", element?.optLong("versionCode", -1L) ?: -1L)
            require(versionCode >= 0L) { "Missing or invalid versionCode" }
            val versionName = json.optString("versionName")
                .ifBlank { element?.optString("versionName").orEmpty() }
                .ifBlank { versionCode.toString() }
            val downloadUrl = json.optString("downloadUrl")
                .ifBlank { json.optString("url") }
                .ifBlank {
                    element?.optString("outputFile")?.takeIf { it.isNotBlank() }?.let { outputFile ->
                        runCatching { URI(updateUrl).resolve(outputFile).toString() }.getOrNull().orEmpty()
                    }.orEmpty()
                }
            require(downloadUrl.isBlank() || isSecureHttpsUrl(downloadUrl)) {
                "Download URL must use HTTPS"
            }
            AppRelease(
                versionCode = versionCode,
                versionName = versionName,
                downloadUrl = downloadUrl,
                notes = json.optString("notes")
            )
        } finally {
            connection.disconnect()
        }
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
