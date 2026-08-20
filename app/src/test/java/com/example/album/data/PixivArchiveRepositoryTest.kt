package com.example.album.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivArchiveRepositoryTest {
    @Test
    fun parsesStrictPixivExportName() {
        assertEquals("147958029" to 2, parsePixivFilename("illust_147958029_p2_20260805_120000.png"))
    }

    @Test
    fun parsesStrictPixivExportNameWithoutPage() {
        assertEquals("147958029" to 0, parsePixivFilename("illust_147958029_20260805_120000.jpg"))
    }

    @Test
    fun parsesPixivDownloadNameWithDuplicateExtensionDot() {
        assertEquals("148270175" to 0, parsePixivFilename("illust_148270175_20260814_080938..jpg"))
        assertEquals("148326896" to 0, parsePixivFilename("illust_148326896_20260813_232526..png"))
    }

    @Test
    fun rejectsBarePidToAvoidMisidentifyingOrdinaryFiles() {
        assertNull(parsePixivFilename("147958029.jpg"))
    }

    @Test
    fun rejectsFilenameWithoutPid() {
        assertNull(parsePixivFilename("holiday-photo.jpg"))
    }

    @Test
    fun parsesEveryFormatSupportedByTheHtmlArchive() {
        assertEquals("147958029" to 0, parsePixivFilename("illust_147958029_20260805_120000.webp"))
        assertEquals("147958029" to 3, parsePixivFilename("illust_147958029_p3_20260805_120000.gif"))
        assertEquals("147958029" to 0, parsePixivFilename("illust_147958029_20260805_120000.jpeg"))
    }

    @Test
    fun detectsOnlyNonEmptyPixivLoginSessionCookies() {
        assertTrue(hasPixivSessionCookie("device_token=abc; PHPSESSID=12345_token; privacy_policy_agreement=1"))
        assertFalse(hasPixivSessionCookie("device_token=abc; PHPSESSID=; privacy_policy_agreement=1"))
        assertFalse(hasPixivSessionCookie("device_token=abc"))
        assertFalse(hasPixivSessionCookie(null))
    }

    @Test
    fun embedsAllUnicodeTagsInPngKeywordsAndXmp() {
        val source = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).apply {
                write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
                writeInt(0)
                write("IEND".toByteArray(StandardCharsets.US_ASCII))
                writeInt(0xAE426082.toInt())
            }
        }.toByteArray()
        val output = ByteArrayOutputStream()

        embedPngTags(ByteArrayInputStream(source), output, listOf("風景", "A&B", "tag-3", "tag-4"))

        val embedded = String(output.toByteArray(), StandardCharsets.UTF_8)
        assertTrue(embedded.contains("Keywords"))
        assertTrue(embedded.contains("風景, A&B, tag-3, tag-4"))
        assertTrue(embedded.contains("dc:subject"))
        assertTrue(embedded.contains("A&amp;B"))
        assertFalse(embedded.contains(".pixiv.json"))
        assertEquals(
            listOf("風景", "A&B", "tag-3", "tag-4"),
            readPngTags(ByteArrayInputStream(output.toByteArray()))
        )
    }
}
