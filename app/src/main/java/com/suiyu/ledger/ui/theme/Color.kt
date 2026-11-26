package com.suiyu.ledger.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ────────────────────────────────────────────────────────────────
// 岁余 · 色彩
// 取自「米纸 / 墨 / 黛青 / 稻金 / 朱砂 / 青玉」的中国色意象
// ────────────────────────────────────────────────────────────────

// 浅色
val Paper = Color(0xFFF7F3EC)          // 米纸（背景）
val PaperElevated = Color(0xFFFFFDF8)  // 卡片
val PaperSunken = Color(0xFFEFE8DC)    // 凹陷 / 轨道
val Ink = Color(0xFF211F1B)            // 墨
val InkSecondary = Color(0xFF6B675E)
val InkTertiary = Color(0xFF9A9487)
val Pine = Color(0xFF24483E)           // 黛青（主色）
val PineSoft = Color(0xFFDCE8E1)
val Gold = Color(0xFFB08A4A)           // 稻金
val GoldSoft = Color(0xFFF1E4C8)
val Cinnabar = Color(0xFFB4432E)       // 朱砂（支出）
val CinnabarSoft = Color(0xFFF6E0D9)
val Jade = Color(0xFF2C6B55)           // 青玉（收入）
val JadeSoft = Color(0xFFDCEBE2)
val Line = Color(0xFFE5DDCE)           // 细线
val Danger = Color(0xFFB03A2E)

// 深色
val DarkBg = Color(0xFF121614)
val DarkSurface = Color(0xFF1B211E)
val DarkSurfaceVariant = Color(0xFF262E2A)
val DarkInk = Color(0xFFEDE8DC)
val DarkInkSecondary = Color(0xFFA9A79D)
val DarkInkTertiary = Color(0xFF77766D)
val DarkPine = Color(0xFF9FC7B4)
val DarkPineSoft = Color(0xFF243A32)
val DarkGold = Color(0xFFD9BC82)
val DarkGoldSoft = Color(0xFF3A3020)
val DarkCinnabar = Color(0xFFE59B84)
val DarkCinnabarSoft = Color(0xFF3C2620)
val DarkJade = Color(0xFF86C6A9)
val DarkJadeSoft = Color(0xFF1E3229)
val DarkLine = Color(0xFF313A34)

/** 图表分类配色：低饱和、可并列阅读，兼顾色弱差异 */
val ChartPaletteLight = listOf(
    Color(0xFF24483E), // 黛青
    Color(0xFFB08A4A), // 稻金
    Color(0xFFB4432E), // 朱砂
    Color(0xFF3E6B8A), // 黛蓝
    Color(0xFF7E8C6B), // 苔绿
    Color(0xFFA8746A), // 陶红
    Color(0xFF6E5F8A), // 紫檀
    Color(0xFF4F8A86), // 青瓷
    Color(0xFFB99A5C), // 麦黄
    Color(0xFF8A7B6B), // 褐
)

val ChartPaletteDark = listOf(
    Color(0xFF9FC7B4),
    Color(0xFFD9BC82),
    Color(0xFFE59B84),
    Color(0xFF8FB3CC),
    Color(0xFFB4C2A0),
    Color(0xFFD3A79E),
    Color(0xFFB4A7CC),
    Color(0xFF93C7C2),
    Color(0xFFDDC28C),
    Color(0xFFC0B4A4),
)

/** 扩展语义色：Material3 ColorScheme 中没有、但财务界面必需的色 */
@Immutable
data class SyExtraColors(
    val expense: Color,
    val expenseSoft: Color,
    val income: Color,
    val incomeSoft: Color,
    val gold: Color,
    val goldSoft: Color,
    val paper: Color,
    val inkSoft: Color,
    val hairline: Color,
    val chartPalette: List<Color>,
    val positive: Color,
    val negative: Color,
    /** 首页总览卡的底色 / 文字色：浅色主题用黛青，深色主题用深墨绿，避免深色下整块发亮 */
    val heroSurface: Color,
    val heroContent: Color,
)

val LightXmColors = SyExtraColors(
    expense = Cinnabar,
    expenseSoft = CinnabarSoft,
    income = Jade,
    incomeSoft = JadeSoft,
    gold = Gold,
    goldSoft = GoldSoft,
    paper = Paper,
    inkSoft = InkSecondary,
    hairline = Line,
    chartPalette = ChartPaletteLight,
    positive = Jade,
    negative = Cinnabar,
    heroSurface = Pine,
    heroContent = Color(0xFFF7F3EC),
)

val DarkXmColors = SyExtraColors(
    expense = DarkCinnabar,
    expenseSoft = DarkCinnabarSoft,
    income = DarkJade,
    incomeSoft = DarkJadeSoft,
    gold = DarkGold,
    goldSoft = DarkGoldSoft,
    paper = DarkBg,
    inkSoft = DarkInkSecondary,
    hairline = DarkLine,
    chartPalette = ChartPaletteDark,
    positive = DarkJade,
    negative = DarkCinnabar,
    heroSurface = Color(0xFF1D2E26),
    heroContent = Color(0xFFEDE8DC),
)
