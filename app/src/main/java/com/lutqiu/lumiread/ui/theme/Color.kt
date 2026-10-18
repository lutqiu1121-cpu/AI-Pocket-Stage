package com.lutqiu.lumiread.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * 全项目唯一允许出现颜色字面量的地方。
 *
 * 其他任何 Composable / Modifier 都不允许写 `Color(0x...)`、`Color.White`、`Color.Black`：
 * 要么走 `MaterialTheme.colorScheme.*`，要么引用本文件的语义 token。
 */
object LumiColors {
    // ── 品牌 ────────────────────────────────────────────────────────────
    val Blue = Color(0xFF3B82F6)
    val BlueDeep = Color(0xFF1E3A8A)
    val BlueSoft = Color(0xFFBFD3FF)
    val BlueTint = Color(0xFFDCE8FF)
    val Highlight = Blue.copy(alpha = 0.2f)

    // ── 中性 ────────────────────────────────────────────────────────────
    val Ink = Color(0xFF1A1B1E)
    val InkSoft = Color(0xFF6B7078)
    val InkFaint = Color(0xFF9AA0A6)
    val Hairline = Color(0xFFE6E8EC)
    val HairlineSoft = Color(0xFFEDEEF0)
    val SurfaceMuted = Color(0xFFF1F2F4)
    val Canvas = Color(0xFFF7F8FA)
    val CanvasWarm = Color(0xFFFDFDFC)
    val White = Color(0xFFFFFFFF)

    // ── 深色中性 ────────────────────────────────────────────────────────
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

    // ── 场景占位 ────────────────────────────────────────────────────────
    val PlaceholderTint = Color(0xFFE8ECF2)
    val PlaceholderTintDark = Color(0xFF26282D)

    // ── Mock 生图渐变（离线验收用）─────────────────────────────────────
    val MockGradientStart = Color(0xFF1B1C1F)
    val MockGradientEnd = Blue
}

/**
 * 6 维主题的默认值 / 边界值（ARGB Long）。
 *
 * 只有本文件能定义颜色字面量；DataStore 配置与 Color Picker 都以这些常量作为默认。
 */
object LumiColorTokens {
    const val UI_BACKGROUND = 0xFFF7F8FA
    const val UI_ACCENT = 0xFF3B82F6
    const val READER_BACKGROUND = 0xFFFFFFFF
    const val READER_ACCENT = 0xFF3B82F6
    const val MIN_RADIUS = 0
    const val MAX_RADIUS = 24
}

/** 前景色：根据底色亮度自动选深/浅文字。 */
fun onColor(background: Color): Color =
    if (background.luminance() > 0.55f) LumiColors.Ink else LumiColors.White

/**
 * 全局 App 配色：由「UI 背景色 + 重点色」两个独立维度推导。
 * 不使用系统深色模式开关，避免与自定义背景色互相覆盖。
 */
fun appScheme(background: Color, accent: Color): ColorScheme {
    val dark = background.luminance() < 0.4f
    val ink = if (dark) LumiColors.NightInk else LumiColors.Ink
    val surface = lerp(background, LumiColors.White, if (dark) 0.08f else 0.72f)
    val surfaceVariant = lerp(background, if (dark) LumiColors.White else LumiColors.Black, if (dark) 0.14f else 0.06f)
    val onAccent = onColor(accent)

    return if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(background, accent, 0.28f),
            onPrimaryContainer = onAccent,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = surfaceVariant,
            onSecondaryContainer = ink,
            tertiary = accent,
            onTertiary = onAccent,
            background = background,
            onBackground = ink,
            surface = surface,
            onSurface = ink,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = ink.copy(alpha = 0.68f),
            outline = ink.copy(alpha = 0.18f),
            outlineVariant = ink.copy(alpha = 0.10f),
            error = LumiColors.Warning,
            onError = LumiColors.White,
            scrim = LumiColors.Black,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(background, accent, 0.18f),
            onPrimaryContainer = onColor(lerp(background, accent, 0.18f)),
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = surfaceVariant,
            onSecondaryContainer = ink,
            tertiary = accent,
            onTertiary = onAccent,
            background = background,
            onBackground = ink,
            surface = surface,
            onSurface = ink,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = ink.copy(alpha = 0.64f),
            outline = ink.copy(alpha = 0.16f),
            outlineVariant = ink.copy(alpha = 0.10f),
            error = LumiColors.Warning,
            onError = LumiColors.White,
            scrim = LumiColors.Black,
        )
    }
}

/**
 * 阅读器独立配色：由「阅读背景色 + 阅读重点色」推导，与全局 App 配色互不干扰。
 */
fun readerScheme(background: Color, accent: Color): ColorScheme {
    val dark = background.luminance() < 0.4f
    val ink = if (dark) LumiColors.NightInk else LumiColors.Ink
    val onAccent = onColor(accent)
    val surfaceVariant = lerp(background, if (dark) LumiColors.White else LumiColors.Black, if (dark) 0.12f else 0.05f)

    return if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(background, accent, 0.28f),
            onPrimaryContainer = onAccent,
            secondary = accent,
            onSecondary = onAccent,
            background = background,
            onBackground = ink,
            surface = background,
            onSurface = ink,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = ink.copy(alpha = 0.66f),
            outline = ink.copy(alpha = 0.18f),
            outlineVariant = ink.copy(alpha = 0.10f),
            error = LumiColors.Warning,
            onError = LumiColors.White,
            scrim = LumiColors.Black,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(background, accent, 0.16f),
            onPrimaryContainer = onColor(lerp(background, accent, 0.16f)),
            secondary = accent,
            onSecondary = onAccent,
            background = background,
            onBackground = ink,
            surface = background,
            onSurface = ink,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = ink.copy(alpha = 0.62f),
            outline = ink.copy(alpha = 0.16f),
            outlineVariant = ink.copy(alpha = 0.10f),
            error = LumiColors.Warning,
            onError = LumiColors.White,
            scrim = LumiColors.Black,
        )
    }
}
