package com.lutqiu.lumiread.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lutqiu.lumiread.data.AppThemeMode
import com.lutqiu.lumiread.data.ReaderTheme
import com.lutqiu.lumiread.data.ThemeConfig
import com.lutqiu.lumiread.data.ApiConfig
import com.lutqiu.lumiread.services.ApiConfigRepository
import com.lutqiu.lumiread.ui.components.PageHeader

/**
 * 全局设置（v0.4.0）。
 *
 * - 外观：全局 App 主题（DataStore 持久化），颜色全部走 MaterialTheme；
 * - 阅读器主题：独立配置，互不干扰；
 * - AI：Key 已移入 [ApiConfig] 硬编码，只做只读状态展示。
 */
@Composable
fun GlobalSettingsScreen(
    theme: ThemeConfig,
    onAppThemeChange: (AppThemeMode) -> Unit,
    onReaderThemeChange: (ReaderTheme) -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val key = ApiConfigRepository.getZhipuKey(context)
    val configured = key.isNotBlank() && key != "请填入真实Key"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        PageHeader(title = "设置")

        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(12.dp))

            SectionTitle("外观 · 全局主题")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeMode.entries.forEach { mode ->
                    ThemeChip(
                        label = mode.label,
                        selected = theme.appTheme == mode,
                        onClick = { onAppThemeChange(mode) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "全局主题只影响书架 / 资料库 / 设置与底部导航，阅读器主题独立设置。",
                style = TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant, lineHeight = 17.sp),
            )

            Spacer(Modifier.height(20.dp))

            SectionTitle("阅读器主题 · 独立")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTheme.entries.forEach { readerTheme ->
                    ThemeChip(
                        label = readerTheme.label,
                        selected = theme.readerTheme == readerTheme,
                        onClick = { onReaderThemeChange(readerTheme) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "阅读页、控制台与 VN 剧场使用这套配色，与全局深浅色互不影响。",
                style = TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant, lineHeight = 17.sp),
            )

            Spacer(Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = scheme.surface,
                border = BorderStroke(1.dp, scheme.outlineVariant),
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(
                        text = "AI 视觉小说引擎",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface),
                    )
                    Spacer(Modifier.height(10.dp))
                    SettingLine(label = "模型", value = ApiConfig.ZHIPU_MODEL, valueColor = scheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    SettingLine(
                        label = "API Key",
                        value = if (configured) "已内置配置" else "未填写 · 请在 ApiConfig.kt 中填入",
                        valueColor = if (configured) scheme.primary else scheme.error,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Key 与模型地址统一在 ApiConfig.kt 中维护，设置页不再提供输入。",
                style = TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant, lineHeight = 18.sp),
            )

            // 给液态玻璃底部导航留出安全高度
            Spacer(Modifier.height(110.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) scheme.primary else scheme.surfaceVariant)
            .then(
                if (selected) Modifier.border(1.dp, scheme.primary, RoundedCornerShape(50)) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun SettingLine(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(text = value, style = TextStyle(fontSize = 12.sp, color = valueColor))
    }
}
