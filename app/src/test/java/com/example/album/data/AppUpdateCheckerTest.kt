package com.example.album.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    @Test
    fun secureUpdateUrl_requires_https_host_without_userinfo_or_fragment() {
        assertTrue(AppUpdateChecker.isSecureHttpsUrl("https://example.com/releases.json"))
        assertFalse(AppUpdateChecker.isSecureHttpsUrl("http://example.com/releases.json"))
        assertFalse(AppUpdateChecker.isSecureHttpsUrl("https://user:pass@example.com/releases.json"))
        assertFalse(AppUpdateChecker.isSecureHttpsUrl("https://example.com/releases.json#latest"))
        assertFalse(AppUpdateChecker.isSecureHttpsUrl("javascript:alert(1)"))
    }

    @Test
    fun parsesThePublishedManifest() {
        val release = AppUpdateChecker.parse(
            JSONObject(
                """
                {
                  "versionCode": 171,
                  "versionName": "1.2.1",
                  "downloadUrl": "https://github.com/Tnomlav/ALbum/releases/download/v1.2.1/Album-v1.2.1.apk",
                  "notes": "换机不再丢收藏"
                }
                """.trimIndent()
            ),
            "https://github.com/Tnomlav/ALbum/releases/latest/download/album-update.json"
        )

        assertEquals(171L, release.versionCode)
        assertEquals("1.2.1", release.versionName)
        assertTrue(release.downloadUrl.endsWith("Album-v1.2.1.apk"))
        assertEquals("换机不再丢收藏", release.notes)
    }

    @Test
    fun parsesAGitHubReleaseResponseIncludingItsTagAndAssets() {
        val release = AppUpdateChecker.parse(
            JSONObject(
                """
                {
                  "tag_name": "v2.0.0",
                  "name": "Album 2.0.0",
                  "body": "Rewrite",
                  "html_url": "https://github.com/Tnomlav/ALbum/releases/tag/v2.0.0",
                  "assets": [
                    {"name": "album-update.json", "browser_download_url": "https://github.com/Tnomlav/ALbum/releases/download/v2.0.0/album-update.json"},
                    {"name": "Album-v2.0.0-arm64-v8a.apk", "browser_download_url": "https://github.com/Tnomlav/ALbum/releases/download/v2.0.0/Album-v2.0.0-arm64-v8a.apk"}
                  ]
                }
                """.trimIndent()
            ),
            "https://api.github.com/repos/Tnomlav/ALbum/releases/latest"
        )

        assertEquals(-1L, release.versionCode)
        assertEquals("2.0.0", release.versionName)
        assertTrue(release.downloadUrl.endsWith("Album-v2.0.0-arm64-v8a.apk"))
        assertEquals("Rewrite", release.notes)
    }

    @Test
    fun fallsBackToTheReleasePageWhenNoApkAssetExists() {
        val release = AppUpdateChecker.parse(
            JSONObject("""{"tag_name":"v1.3.0","html_url":"https://github.com/Tnomlav/ALbum/releases/tag/v1.3.0"}"""),
            "https://api.github.com/repos/Tnomlav/ALbum/releases/latest"
        )

        assertEquals("https://github.com/Tnomlav/ALbum/releases/tag/v1.3.0", release.downloadUrl)
    }

    @Test
    fun rejectsAnInsecureDownloadUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdateChecker.parse(
                JSONObject(
                    """{"versionCode":9,"versionName":"9.0","downloadUrl":"http://example.com/album.apk"}"""
                ),
                "https://example.com/album-update.json"
            )
        }
    }

    @Test
    fun rejectsAManifestWithoutAnyVersionInformation() {
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdateChecker.parse(JSONObject("""{"notes":"nothing here"}"""), "https://example.com/a.json")
        }
    }

    @Test
    fun versionCodeDecidesWhenBothSidesHaveOne() {
        val release = AppRelease(171L, "1.2.1", "", "")

        assertEquals(UpdateStatus.Available, AppUpdateChecker.status(release, "1.1.91", 170L))
        assertEquals(UpdateStatus.UpToDate, AppUpdateChecker.status(release, "1.2.1", 171L))
        assertEquals(UpdateStatus.SourceBehind, AppUpdateChecker.status(release, "1.3.0", 172L))
    }

    @Test
    fun versionNameDecidesWhenTheSourceHasNoVersionCode() {
        val release = AppRelease(-1L, "2.0.0", "", "")

        assertEquals(UpdateStatus.Available, AppUpdateChecker.status(release, "1.2.1", 171L))
        assertEquals(UpdateStatus.UpToDate, AppUpdateChecker.status(release, "2.0.0", 171L))
        assertEquals(UpdateStatus.SourceBehind, AppUpdateChecker.status(AppRelease(-1L, "1.9.0", "", ""), "2.0.0", 200L))
    }

    @Test
    fun versionNamesCompareNumericallyNotAsText() {
        assertTrue(AppUpdateChecker.compareVersionNames("1.2.1", "1.1.91") > 0)
        assertTrue(AppUpdateChecker.compareVersionNames("1.10.0", "1.9.9") > 0)
        assertTrue(AppUpdateChecker.compareVersionNames("1.2", "1.2.1") < 0)
        assertEquals(0, AppUpdateChecker.compareVersionNames("v1.2.1", "1.2.1"))
    }
}
