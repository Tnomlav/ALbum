package com.example.album.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** SVG symbols copied from the editor prototype. */
internal object EditorPrototypeIcons {
    val Close by lazy { strokeIcon("close", listOf("M6 6l12 12M18 6 6 18")) }
    val Undo by lazy { strokeIcon("editor-undo", listOf("m9 8-5 4 5 4M4 12h9a7 7 0 0 1 7 7")) }
    val Redo by lazy { strokeIcon("editor-redo", listOf("m15 8 5 4-5 4m5-4h-9a7 7 0 0 0-7 7")) }
    val Repeat by lazy { strokeIcon("repeat", listOf("m17 2 4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3")) }
    val Pencil by lazy { strokeIcon("pencil", listOf("m4 20 4.2-1 10.4-10.4a2.1 2.1 0 0 0-3-3L5.2 16 4 20zM14.5 6.5l3 3")) }
    val Eyedropper by lazy { strokeIcon("eyedropper", listOf("m14.5 5.5 4-4 4 4-4 4M17 8 7.2 17.8 4 20l2.2-3.2L16 7M5 19l-2 2")) }
    val Eraser by lazy { strokeIcon("eraser", listOf("m7 19-4-4 9-11a2 2 0 0 1 3-.2l5.2 5.2a2 2 0 0 1-.2 3L13 19H7zM10 8l7 7M13 19h8")) }
    val EditPhoto by lazy {
        strokeIcon("edit-photo", listOf(
            "M4 7h2M10 7h10M4 12h6M14 12h6M4 17h10M18 17h2",
            "M10 7A2 2 0 1 1 6 7A2 2 0 1 1 10 7",
            "M14 12A2 2 0 1 1 10 12A2 2 0 1 1 14 12",
            "M18 17A2 2 0 1 1 14 17A2 2 0 1 1 18 17"
        ))
    }
    val RotateLeft by lazy { strokeIcon("rotate-left", listOf("M4 8V3m0 5h5M5.7 7A8 8 0 1 1 4 14")) }
    val RotateRight by lazy { strokeIcon("rotate-right", listOf("M20 8V3m0 5h-5m3.3-1A8 8 0 1 0 20 14")) }
    val FlipHorizontal by lazy { strokeIcon("flip-horizontal", listOf("M12 3v18M9 6 3 12l6 6V6zm6 0 6 6-6 6V6z")) }
    val FlipVertical by lazy { strokeIcon("flip-vertical", listOf("M3 12h18M6 9l6-6 6 6H6zm0 6 6 6 6-6H6z")) }
    val Type by lazy { strokeIcon("type", listOf("M4 6V4h16v2M12 4v16M8 20h8")) }

    private fun strokeIcon(name: String, paths: List<String>): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { data ->
                addPath(
                    pathData = PathParser().parsePathString(data).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                )
            }
        }.build()

}
