package com.lutqiu.lumiread.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 6 维主题里的「UI 圆润度」：所有按钮 / 卡片 / 对话框 / 面板统一读取。
 */
val LocalCornerRadius = compositionLocalOf { 16.dp }

/**
 * 6 维主题里的「玻璃质感开关」：关闭后所有 LiquidGlassPanel 退化为纯色面板。
 */
val LocalGlassEnabled = compositionLocalOf { true }

/** 统一圆角工具：scale 用于胶囊/小控件微调。 */
@Composable
fun lumiShape(scale: Float = 1f): RoundedCornerShape {
    val base = LocalCornerRadius.current.value * scale
    return RoundedCornerShape(base.coerceIn(0f, 32f).dp)
}

/** 全局 App 主题的兜底入口（真正的 6 维配置由 LumiReadApp 从 DataStore 注入）。 */
@Composable
fun LumiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = appScheme(LumiColors.Canvas, LumiColors.Blue),
        content = content,
    )
}

/**
 * 阅读器独立主题作用域：只影响阅读页 / 控制台 / VN，不污染全局 App 主题。
 */
@Composable
fun LumiReaderTheme(
    background: Color,
    accent: Color,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = readerScheme(background, accent), content = content)
}
