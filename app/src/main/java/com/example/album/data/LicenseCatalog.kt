package com.example.album.data

import android.content.Context

/**
 * Third-party components that ship inside the APK.
 *
 * Anything listed here must also appear in `THIRD_PARTY_NOTICES.md` and, for
 * components under the LGPL, must keep its license text reachable from inside
 * the app so users can relink or replace the library.
 */
data class LicenseComponent(
    val name: String,
    val version: String,
    val licenseName: String,
    val licenseAsset: String
)

/** A license text or notice shipped in `assets/licenses/`. */
data class LicenseDocument(
    val title: String,
    val assetPath: String
)

object LicenseCatalog {
    private const val APACHE = "licenses/apache-2.0.txt"
    private const val LGPL = "licenses/lgpl-2.1.txt"
    private const val FONTS = "licenses/fonts.txt"

    val components: List<LicenseComponent> = listOf(
        LicenseComponent("LibVLC (libvlc-all)", "3.7.5", "LGPL-2.1", LGPL),
        LicenseComponent(
            "AndroidX Media3 (ExoPlayer, UI, Transformer, Effect)",
            "1.6.1",
            "Apache-2.0",
            APACHE
        ),
        LicenseComponent(
            "AndroidX Compose (UI, Material 3, Material Icons)",
            "BOM 2026.02.01",
            "Apache-2.0",
            APACHE
        ),
        LicenseComponent(
            "AndroidX Core, Lifecycle, Activity, DocumentFile, ExifInterface",
            "1.10.1 / 2.6.1 / 1.8.0 / 1.0.1 / 1.3.6",
            "Apache-2.0",
            APACHE
        ),
        LicenseComponent("Kotlin standard library and coroutines", "2.2.10", "Apache-2.0", APACHE),
        LicenseComponent("Bundled editor fonts (10 families)", "see notice", "OFL-1.1", FONTS)
    )

    val documents: List<LicenseDocument> = listOf(
        LicenseDocument("GNU Lesser General Public License 2.1", LGPL),
        LicenseDocument("Apache License 2.0", APACHE),
        LicenseDocument("SIL Open Font License 1.1", "licenses/ofl-1.1.txt"),
        LicenseDocument("Bundled fonts / 内置字体", FONTS)
    )

    /**
     * Reads a license asset. A missing or unreadable asset must not break the
     * screen that is supposed to disclose it, so the failure is reported as
     * text instead of thrown.
     */
    fun read(context: Context, assetPath: String): String = runCatching {
        context.assets.open(assetPath).bufferedReader().use { it.readText() }
    }.getOrElse { error ->
        "Unable to read $assetPath (${error.message ?: "unknown error"})"
    }
}
