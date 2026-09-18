package com.example.album.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Pixiv "P" mark. It is used by the Pixiv bottom-bar tab, the archive entry
 * on the P page and the badge on the pinned Pixiv folder.
 */
internal val PixivPMark = ImageVector.Builder("pixiv-p", 24.dp, 24.dp, 120f, 120f).apply {
    addPath(
        pathData = PathParser().parsePathString(
            "M32 28C36 28 39 27 41 29C43 31 44 34 45 37C50 30 57 27 66 27C82 27 93 40 93 58C93 76 82 89 66 89C58 89 51 85 46 79V92C46 95 44 97 41 97H32ZM62 42C52 42 46 49 46 59C46 69 52 76 62 76C72 76 79 69 79 59C79 49 72 42 62 42Z"
        ).toNodes(),
        pathFillType = PathFillType.EvenOdd,
        fill = SolidColor(Color.Black)
    )
}.build()

/**
 * The same mark with the counter of the "P" filled in. The badge uses it so no
 * theme colour shows *inside* the letter: the mark reads as one translucent
 * white shape instead of a white outline around a tinted hole.
 */
private val PixivPMarkSolid = ImageVector.Builder("pixiv-p-solid", 24.dp, 24.dp, 120f, 120f).apply {
    addPath(
        pathData = PathParser().parsePathString(
            "M32 28C36 28 39 27 41 29C43 31 44 34 45 37C50 30 57 27 66 27C82 27 93 40 93 58C93 76 82 89 66 89C58 89 51 85 46 79V92C46 95 44 97 41 97H32ZM62 42C52 42 46 49 46 59C46 69 52 76 62 76C72 76 79 69 79 59C79 49 72 42 62 42Z"
        ).toNodes(),
        pathFillType = PathFillType.EvenOdd,
        fill = SolidColor(Color.Black)
    )
    // The counter of the letter, filled so the mark has no hole.
    addPath(
        pathData = PathParser().parsePathString(
            "M46.5 59a16 17 0 1 0 32 0a16 17 0 1 0 -32 0Z"
        ).toNodes(),
        pathFillType = PathFillType.NonZero,
        fill = SolidColor(Color.Black)
    )
}.build()

/**
 * The Pixiv tile: a theme-coloured square with the mark punched into it in
 * translucent white. Used for the pinned folder's corner badge, the archive
 * entry on the P page and the archive tool in the toolbox.
 */
@Composable
internal fun PixivMarkBadge(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(size * 0.24f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            PixivPMarkSolid,
            contentDescription = null,
            // Nearly opaque: a 70% white over the theme colour still read as a
            // tinted letter, and the mark has to look white with only a hint of
            // transparency.
            tint = Color.White.copy(alpha = .9f),
            modifier = Modifier.fillMaxSize(.74f).graphicsLayer { scaleX = 1.15f; scaleY = 1.15f }
        )
    }
}
