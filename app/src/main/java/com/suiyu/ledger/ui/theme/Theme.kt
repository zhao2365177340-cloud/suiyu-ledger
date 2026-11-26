package com.suiyu.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalSyColors = staticCompositionLocalOf { LightXmColors }

private val LightScheme = lightColorScheme(
    primary = Pine,
    onPrimary = PaperElevated,
    primaryContainer = PineSoft,
    onPrimaryContainer = Pine,
    secondary = Gold,
    onSecondary = PaperElevated,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = Color(0xFF4A3617),
    tertiary = Jade,
    onTertiary = PaperElevated,
    tertiaryContainer = JadeSoft,
    onTertiaryContainer = Jade,
    background = Paper,
    onBackground = Ink,
    surface = PaperElevated,
    onSurface = Ink,
    surfaceVariant = PaperSunken,
    onSurfaceVariant = InkSecondary,
    surfaceTint = Pine,
    outline = Line,
    outlineVariant = Line,
    error = Danger,
    onError = PaperElevated,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    scrim = Color(0x99000000),
)

private val DarkScheme = darkColorScheme(
    primary = DarkPine,
    onPrimary = Color(0xFF0E231C),
    primaryContainer = DarkPineSoft,
    onPrimaryContainer = DarkPine,
    secondary = DarkGold,
    onSecondary = Color(0xFF2A2112),
    secondaryContainer = DarkGoldSoft,
    onSecondaryContainer = DarkGold,
    tertiary = DarkJade,
    onTertiary = Color(0xFF0E231C),
    tertiaryContainer = DarkJadeSoft,
    onTertiaryContainer = DarkJade,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkSecondary,
    surfaceTint = DarkPine,
    outline = DarkLine,
    outlineVariant = DarkLine,
    error = DarkCinnabar,
    onError = Color(0xFF2A120D),
    inverseSurface = DarkInk,
    inverseOnSurface = DarkBg,
    scrim = Color(0xCC000000),
)

val SyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** 全局设计常量：间距节奏（4 的倍数）与版心宽度 */
object SyDim {
    val gutter = 20.dp
    val gapXs = 4.dp
    val gapS = 8.dp
    val gapM = 12.dp
    val gapL = 16.dp
    val gapXl = 24.dp
    val gap2Xl = 32.dp
    val cardRadius = 20.dp
    val cardBorder = 1.dp
    val contentMaxWidth = 560.dp
    val bottomBarHeight = 64.dp
}

@Composable
fun SuiyuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val xmColors = if (darkTheme) DarkXmColors else LightXmColors
    androidx.compose.runtime.CompositionLocalProvider(LocalSyColors provides xmColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = SyTypography,
            shapes = SyShapes,
            content = content,
        )
    }
}

/** 语义色入口：`SyTheme.colors.expense` */
object SyTheme {
    val colors: SyExtraColors
        @Composable @ReadOnlyComposable get() = LocalSyColors.current
}
