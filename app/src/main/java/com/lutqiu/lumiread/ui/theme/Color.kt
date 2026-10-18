package com.lutqiu.lumiread.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.lutqiu.lumiread.data.ReaderTheme

/**
 * 全项目唯一允许出现颜色字面量的地方。
 *
 * 其他任何 Composable / Modifier 都不允许写 `Color(0x...)`、`Color.White`、`Color.Black`：
 * 要么走 `MaterialTheme.colorScheme.*`（跟随双层主题自动切换），要么引用本文件的语义 token。
 */
object LumiColors {
    // ── 品牌 ────────────────────────────────────────────────────────────
    val Blue = Color(0xFF3B82F6)
    val BlueDeep = Color(0xFF1E3A8A)
    val BlueSoft = Color(0xFFBFD3FF)
    val BlueTint = Color(0xFFDCE8FF)
    val Highlight = Blue.copy(alpha = 0.2f)

    // ── 中性（浅色）────────────────────────────────────────────────────
    val Ink = Color(0xFF1A1B1E)
    val InkSoft = Color(0xFF6B7078)
    val InkFaint = Color(0xFF9AA0A6)
    val Hairline = Color(0xFFE6E8EC)
    val HairlineSoft = Color(0xFFEDEEF0)
    val SurfaceMuted = Color(0xFFF1F2F4)
    val Canvas = Color(0xFFF7F8FA)
    val CanvasWarm = Color(0xFFFDFDFC)
    val White = Color(0xFFFFFFFF)

    // ── 中性（深色）────────────────────────────────────────────────────
    val NightCanvas = Color(0xFF101114)
    val NightSurface = Color(0xFF1B1C1F)
    val NightSurfaceHigh = Color(0xFF24262B)
    val NightInk = Color(0xFFE7E9EE)
    val NightInkSoft = Color(0xFFA6ABB5)
    val NightHairline = Color(0xFF32353B)

    // ── 语义 ────────────────────────────────────────────────────────────
    val Success = Color(0xFF16A34A)
    val Warning = Color(0xFFB45309)
    val Black = Color(0xFF000000)
    val Transparent = Color(0x00000000)
    val Scrim = Black.copy(alpha = 0.4f)

    // ── 玻璃（高光描边 + 极低透明度底色）────────────────────────────────
    val GlassTint = White.copy(alpha = 0.42f)
    val GlassTintStrong = White.copy(alpha = 0.58f)
    val GlassBorder = White.copy(alpha = 0.72f)
    val GlassBorderSoft = White.copy(alpha = 0.28f)
    val GlassTintDark = NightSurface.copy(alpha = 0.46f)
    val GlassTintDarkStrong = NightSurfaceHigh.copy(alpha = 0.60f)
    val GlassBorderDark = White.copy(alpha = 0.22f)
    val GlassBorderDarkSoft = White.copy(alpha = 0.10f)
    val GlassEdgeShadow = Black.copy(alpha = 0.12f)
    val GlassSpecular = White.copy(alpha = 0.55f)

    // ── 阅读器纸张 ──────────────────────────────────────────────────────
    val Paper = White
    val PaperInk = Color(0xFF1F2024)
    val PaperHairline = Color(0xFFEEEEEE)
    val Sepia = Color(0xFFF5EFE6)
    val SepiaInk = Color(0xFF3A3226)
    val SepiaHairline = Color(0xFFE3D8C6)
    val EyeGreen = Color(0xFFE8F3E8)
    val EyeGreenInk = Color(0xFF2A3A2A)
    val EyeGreenHairline = Color(0xFFCFE3CF)
    val GrayPaper = Color(0xFF2B2B2B)
    val GrayPaperInk = Color(0xFFD9D9D9)
    val GrayPaperHairline = Color(0xFF3C3C3C)
    val PureBlack = Black
    val PureBlackInk = Color(0xFFBFBFBF)
    val PureBlackHairline = Color(0xFF2A2A2A)

    // ── 场景占位（浅色玻璃，不允许黑块）──────────────────────────────────
    val PlaceholderTint = Color(0xFFE8ECF2)
    val PlaceholderTintDark = Color(0xFF26282D)
}

// ── 全局 App 主题 ─────────────────────────────────────────────────────────

val AppLightScheme: ColorScheme = lightColorScheme(
    primary = LumiColors.Blue,
    onPrimary = LumiColors.White,
    primaryContainer = LumiColors.BlueTint,
    onPrimaryContainer = LumiColors.BlueDeep,
    secondary = LumiColors.BlueDeep,
    onSecondary = LumiColors.White,
    secondaryContainer = LumiColors.BlueSoft,
    onSecondaryContainer = LumiColors.BlueDeep,
    tertiary = LumiColors.Blue,
    onTertiary = LumiColors.White,
    background = LumiColors.Canvas,
    onBackground = LumiColors.Ink,
    surface = LumiColors.White,
    onSurface = LumiColors.Ink,
    surfaceVariant = LumiColors.SurfaceMuted,
    onSurfaceVariant = LumiColors.InkSoft,
    outline = LumiColors.Hairline,
    outlineVariant = LumiColors.HairlineSoft,
    error = LumiColors.Warning,
    onError = LumiColors.White,
    scrim = LumiColors.Black,
)

val AppDarkScheme: ColorScheme = darkColorScheme(
    primary = LumiColors.Blue,
    onPrimary = LumiColors.White,
    primaryContainer = LumiColors.BlueDeep,
    onPrimaryContainer = LumiColors.BlueSoft,
    secondary = LumiColors.BlueSoft,
    onSecondary = LumiColors.NightCanvas,
    secondaryContainer = LumiColors.BlueDeep,
    onSecondaryContainer = LumiColors.BlueSoft,
    tertiary = LumiColors.Blue,
    onTertiary = LumiColors.White,
    background = LumiColors.NightCanvas,
    onBackground = LumiColors.NightInk,
    surface = LumiColors.NightSurface,
    onSurface = LumiColors.NightInk,
    surfaceVariant = LumiColors.NightSurfaceHigh,
    onSurfaceVariant = LumiColors.NightInkSoft,
    outline = LumiColors.NightHairline,
    outlineVariant = LumiColors.NightHairline,
    error = LumiColors.Warning,
    onError = LumiColors.White,
    scrim = LumiColors.Black,
)

// ── 阅读器独立主题 ────────────────────────────────────────────────────────

fun readerScheme(theme: ReaderTheme): ColorScheme = when (theme) {
    ReaderTheme.PAPER -> lightColorScheme(
        primary = LumiColors.Blue,
        onPrimary = LumiColors.White,
        background = LumiColors.Paper,
        onBackground = LumiColors.PaperInk,
        surface = LumiColors.Paper,
        onSurface = LumiColors.PaperInk,
        surfaceVariant = LumiColors.SurfaceMuted,
        onSurfaceVariant = LumiColors.InkSoft,
        outline = LumiColors.PaperHairline,
        outlineVariant = LumiColors.PaperHairline,
        scrim = LumiColors.Black,
    )

    ReaderTheme.SEPIA -> lightColorScheme(
        primary = LumiColors.Blue,
        onPrimary = LumiColors.White,
        background = LumiColors.Sepia,
        onBackground = LumiColors.SepiaInk,
        surface = LumiColors.Sepia,
        onSurface = LumiColors.SepiaInk,
        surfaceVariant = LumiColors.SepiaHairline,
        onSurfaceVariant = LumiColors.SepiaInk.copy(alpha = 0.7f),
        outline = LumiColors.SepiaHairline,
        outlineVariant = LumiColors.SepiaHairline,
        scrim = LumiColors.Black,
    )

    ReaderTheme.EYE_GREEN -> lightColorScheme(
        primary = LumiColors.Blue,
        onPrimary = LumiColors.White,
        background = LumiColors.EyeGreen,
        onBackground = LumiColors.EyeGreenInk,
        surface = LumiColors.EyeGreen,
        onSurface = LumiColors.EyeGreenInk,
        surfaceVariant = LumiColors.EyeGreenHairline,
        onSurfaceVariant = LumiColors.EyeGreenInk.copy(alpha = 0.72f),
        outline = LumiColors.EyeGreenHairline,
        outlineVariant = LumiColors.EyeGreenHairline,
        scrim = LumiColors.Black,
    )

    ReaderTheme.DARK_GRAY -> darkColorScheme(
        primary = LumiColors.Blue,
        onPrimary = LumiColors.White,
        background = LumiColors.GrayPaper,
        onBackground = LumiColors.GrayPaperInk,
        surface = LumiColors.GrayPaper,
        onSurface = LumiColors.GrayPaperInk,
        surfaceVariant = LumiColors.GrayPaperHairline,
        onSurfaceVariant = LumiColors.GrayPaperInk.copy(alpha = 0.72f),
        outline = LumiColors.GrayPaperHairline,
        outlineVariant = LumiColors.GrayPaperHairline,
        scrim = LumiColors.Black,
    )

    ReaderTheme.PURE_BLACK -> darkColorScheme(
        primary = LumiColors.Blue,
        onPrimary = LumiColors.White,
        background = LumiColors.PureBlack,
        onBackground = LumiColors.PureBlackInk,
        surface = LumiColors.PureBlack,
        onSurface = LumiColors.PureBlackInk,
        surfaceVariant = LumiColors.PureBlackHairline,
        onSurfaceVariant = LumiColors.PureBlackInk.copy(alpha = 0.72f),
        outline = LumiColors.PureBlackHairline,
        outlineVariant = LumiColors.PureBlackHairline,
        scrim = LumiColors.Black,
    )
}
