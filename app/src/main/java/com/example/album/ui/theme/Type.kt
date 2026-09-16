package com.example.album.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp
    )
)

/**
 * Semantic text styles.
 *
 * The UI used to spell `fontSize = 12.sp / 13.sp / 15.sp / 17.sp` out at every
 * call site (a dozen different sizes across the app). These are the roles those
 * sizes actually play, so a sheet title, a row label and a top-bar action look
 * the same wherever they appear.
 */
object VaultText {
    val SheetTitle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
    val TopBarTitle = TextStyle(fontSize = 16.sp, lineHeight = 21.sp)
    val TopBarSwitch = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
    val TopBarAction = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val Body = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val RowLabel = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val RowValue = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val Caption = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp)
    val Badge = TextStyle(fontSize = 10.sp, lineHeight = 13.sp)
    val Button = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
}
