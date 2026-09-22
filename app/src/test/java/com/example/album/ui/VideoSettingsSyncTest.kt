package com.example.album.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The video settings exist twice: once on the settings page and once inside the
 * player's own menu. A setting added to only one of them silently does nothing
 * for the other users of the same app, which is exactly what happened to the
 * gesture hint overlay. This test pins both lists to each other.
 */
class VideoSettingsSyncTest {

    // The settings page goes through local helpers (`setBoolean("…")`, `value("…")`),
    // the player dialog writes the preferences directly (`putBoolean("…")`).
    private val preferenceCall = Regex(
        "(?:(?:get|put|set)(?:Boolean|String|Int|Long)|value)\\([ \\t]*\"([^\"]+)\""
    )

    @Test
    fun thePlayerMenuOffersTheSameVideoSettingsAsTheSettingsPage() {
        val settingsPage = keysIn(section(settingsScreen(), "if (settingsSection == \"视频\")"))
        val playerDialog = keysIn(functions(playerDialog(), "private fun VideoSettingsDialog"))

        assertTrue("the video section key scan found too few settings", settingsPage.size >= 10)
        assertTrue("the player dialog key scan found too few settings", playerDialog.size >= 10)
        assertEquals(
            "video settings exist on one screen but not the other",
            settingsPage.sorted(),
            playerDialog.sorted()
        )
    }

    private fun section(source: String, marker: String): String {
        val start = source.indexOf(marker)
        assertTrue("marker not found: $marker", start >= 0)
        val next = source.indexOf("if (settingsSection ==", start + marker.length)
        return if (next > start) source.substring(start, next) else source.substring(start)
    }

    private fun functions(source: String, marker: String): String {
        val start = source.indexOf(marker)
        assertTrue("marker not found: $marker", start >= 0)
        val next = source.indexOf("\n@Composable", start + marker.length)
        return if (next > start) source.substring(start, next) else source.substring(start)
    }

    private fun keysIn(source: String): Set<String> =
        preferenceCall.findAll(source).mapTo(sortedSetOf()) { it.groupValues[1] }

    private fun settingsScreen() = mainSource("screens/SettingsScreen.kt")

    private fun playerDialog() = mainSource("components/MediaViewer.kt")

    private fun mainSource(relative: String): String {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main/java").isDirectory }
            ?: File(".")
        return File(root, "app/src/main/java/com/example/album/ui/$relative").readText()
    }
}
