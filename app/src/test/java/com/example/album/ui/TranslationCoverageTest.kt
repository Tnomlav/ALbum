package com.example.album.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The UI keeps its text in one dictionary (see AppLanguage.kt) instead of
 * Android string resources, which means nothing prevents a new `appText("…")`
 * call site from shipping untranslated. This test scans the source for those
 * call sites and fails when one of them has no English text.
 */
class TranslationCoverageTest {
    @Test
    fun everyAppTextCallSiteHasEnglishText() {
        val sources = mainSourceFiles()
        assertTrue("No Kotlin sources found; the test is not looking in the right place", sources.isNotEmpty())

        val literals = sortedMapOf<String, String>()
        // Only direct literals are checked. A call whose first argument is an
        // expression (a variable, or an inline `if`) cannot be resolved here.
        val pattern = Regex("appText\\([ \\t]*\"([^\"\\n\\\\]*(?:\\\\.[^\"\\n\\\\]*)*)\"")
        sources.forEach { file ->
            pattern.findAll(withoutComments(file.readText())).forEach { match ->
                literals.putIfAbsent(match.groupValues[1], file.name)
            }
        }
        assertTrue("No appText() call sites found", literals.isNotEmpty())

        val untranslated = literals
            .filterKeys { appText(it, english = true) == it }
            .entries
            .joinToString("\n") { (chinese, file) -> "  $chinese  ($file)" }

        assertTrue(
            "These appText() literals have no English text:\n$untranslated",
            untranslated.isBlank()
        )
    }

    @Test
    fun settingsRowsAreCoveredByTheSameDictionary() {
        // Values that used to be translated by a second dictionary inside
        // SettingsScreen.kt must keep working after that dictionary was removed.
        listOf("主题模式", "语言", "缓存上限", "检查更新", "开源许可", "导出应用数据").forEach { key ->
            assertTrue("$key has no English text", appText(key, english = true) != key)
        }
    }

    private fun mainSourceFiles(): List<File> {
        // Unit tests run with the module directory as the working directory.
        val candidates = generateSequence(File("").absoluteFile) { it.parentFile }
            .take(4)
            .map { File(it, "src/main/java") }
            .filter { it.isDirectory }
        val root = candidates.firstOrNull() ?: return emptyList()
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * Removes block and line comments so text that only exists in a
     * commented-out block is not reported (and cannot hide a real gap).
     */
    private fun withoutComments(source: String): String = source
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("//[^\n]*"), " ")
}
