package com.example.album.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(
    val versionCode: Long,
    val versionName: String,
    val downloadUrl: String,
    val notes: String
)

object AppUpdateChecker {
    fun fetch(updateUrl: String): AppRelease {
        require(updateUrl.startsWith("https://") || updateUrl.startsWith("http://")) {
            "Update URL must use HTTP or HTTPS"
        }
        val connection = (URL(updateUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Album-Android")
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) error("Update server returned HTTP $status")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
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
                        updateUrl.substringBeforeLast('/') + "/" + outputFile
                    }.orEmpty()
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
}
