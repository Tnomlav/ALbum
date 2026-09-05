package com.example.album.data

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
}
