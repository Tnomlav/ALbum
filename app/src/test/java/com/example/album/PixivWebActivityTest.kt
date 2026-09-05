package com.example.album

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivWebActivityTest {
    @Test
    fun browserCompatibleUserAgent_removesEmbeddedWebViewMarkers() {
        val original = "Mozilla/5.0 (Linux; Android 15; Device Build/ABC; wv) " +
            "AppleWebKit/537.36 Version/4.0 Chrome/138.0.0.0 Mobile Safari/537.36"

        val result = browserCompatibleUserAgent(original)

        assertFalse(result.contains("; wv"))
        assertFalse(result.contains("Version/4.0"))
        assertEquals(
            "Mozilla/5.0 (Linux; Android 15; Device Build/ABC) " +
                "AppleWebKit/537.36 Chrome/138.0.0.0 Mobile Safari/537.36",
            result
        )
    }

    @Test
    fun pixivWebHost_allowlist_rejects_untrusted_hosts() {
        assertTrue(isAllowedPixivWebHost("www.pixiv.net"))
        assertTrue(isAllowedPixivWebHost("accounts.pixiv.net"))
        assertTrue(isAllowedPixivWebHost("OAUTH.SECURE.PIXIV.NET"))
        assertFalse(isAllowedPixivWebHost("example.com"))
        assertFalse(isAllowedPixivWebHost("pixiv.net.evil.example"))
        assertFalse(isAllowedPixivWebHost(null))
    }
}
