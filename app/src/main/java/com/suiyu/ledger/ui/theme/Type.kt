package com.suiyu.ledger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字体策略：
 * - 数字与标题用衬线体（系统 Serif），带一点「账簿 / 报表」的气质；
 * - 正文与操作文案用无衬线，保证小字号下的可读性与效率。
 */
val SySerif = FontFamily.Serif
val SySans = FontFamily.SansSerif

val SyTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SySerif, fontWeight = FontWeight.Medium,
        fontSize = 46.sp, lineHeight = 54.sp, letterSpacing = (-0.8).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = SySerif, fontWeight = FontWeight.Medium,
        fontSize = 38.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = SySerif, fontWeight = FontWeight.Medium,
        fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SySerif, fontWeight = FontWeight.Medium,
        fontSize = 26.sp, lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SySerif, fontWeight = FontWeight.Medium,
        fontSize = 22.sp, lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SySerif, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SySans, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = SySans, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SySans, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SySans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SySans, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SySans, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SySans, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = SySans, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp,
    ),
)
