package com.example.album.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dialogs used to grow their own confirm/cancel buttons: some right-aligned,
 * some full width, some with "关闭" where others said "取消". The wording and
 * the layout now come from one place (VaultDialogLabels plus the shared
 * VaultSheet*Button components in VaultDialogs.kt). These two checks keep it
 * that way.
 */
class DialogButtonConsistencyTest {

    private val dialogsSource: String by lazy { file("components/VaultDialogs.kt") }

    @Test
    fun theSharedDialogLabelsAreDefinedExactlyOnce() {
        val source = withoutComments(dialogsSource)
        listOf("取消", "完成", "知道了", "应用", "关闭").forEach { label ->
            val occurrences = Regex("\"" + label + "\"").findAll(source).count()
            assertEquals(
                "the dialog label \"$label\" must live in VaultDialogLabels only (found $occurrences uses)",
                1,
                occurrences
            )
        }
    }

    @Test
    fun theSharedDialogsUseTheSharedButtons() {
        val shared = listOf(
            "fun VaultInfoSheet",
            "fun VaultConfirmationSheet",
            "fun VaultChoiceConfirmationSheet",
            "fun VaultTextInputSheet",
            "fun VaultTextInputDialog"
        )
        shared.forEach { marker ->
            val body = bodyOf(dialogsSource, marker)
            val usesSharedButtons = listOf(
                "VaultSheetConfirmButtons",
                "VaultSheetDismissButton",
                "VaultSheetPrimaryButton",
                "VaultSheetCancelButton"
            ).any { body.contains(it) }
            assertTrue("$marker must use the shared dialog buttons", usesSharedButtons)
        }
    }

    private fun bodyOf(source: String, marker: String): String {
        val start = source.indexOf(marker)
        assertTrue("marker not found: $marker", start >= 0)
        val next = source.indexOf("\n@Composable", start + marker.length)
        return if (next > start) source.substring(start, next) else source.substring(start)
    }

    private fun withoutComments(source: String): String =
        source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//[^\n]*"), "")

    private fun file(relative: String): String {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main/java").isDirectory }
            ?: File(".")
        return File(root, "app/src/main/java/com/example/album/ui/$relative").readText()
    }
}
