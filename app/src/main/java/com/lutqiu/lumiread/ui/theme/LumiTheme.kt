package com.lutqiu.lumiread.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 全局主色：Lumi 蓝。Slider / Switch / Tab 等 Material3 组件的默认紫色全部被它替换。 */
val LumiBlue = Color(0xFF3B82F6)

private val LumiColorScheme = lightColorScheme(
    primary = LumiBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF0B2A5B),
    secondary = LumiBlue,
    onSecondary = Color.White,
    tertiary = LumiBlue,
    onTertiary = Color.White,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1A1B1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1B1E),
)

@Composable
fun LumiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LumiColorScheme, content = content)
}
