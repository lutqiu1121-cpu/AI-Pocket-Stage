package com.lutqiu.lumiread.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.lutqiu.lumiread.data.ReaderTheme

/**
 * 全局 App 主题入口（颜色全部来自 [AppLightScheme] / [AppDarkScheme]）。
 *
 * 这里只做「跟随系统」的兜底；真正的用户选择由 [LumiReadApp] 读取 DataStore 后重新 provide。
 */
@Composable
fun LumiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AppDarkScheme else AppLightScheme,
        content = content,
    )
}

/**
 * 阅读器独立主题作用域：只影响阅读页 / 控制台 / VN，不污染全局 App 主题。
 */
@Composable
fun LumiReaderTheme(
    theme: ReaderTheme,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = readerScheme(theme), content = content)
}
