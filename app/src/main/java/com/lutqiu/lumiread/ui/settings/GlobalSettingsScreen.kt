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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lutqiu.lumiread.data.ReaderPagingMode
import com.lutqiu.lumiread.data.ReaderPrefs
import com.lutqiu.lumiread.data.ReaderSettings
import com.lutqiu.lumiread.data.ThemeConfig
import com.lutqiu.lumiread.data.ApiConfig
import com.lutqiu.lumiread.services.ApiConfigRepository
import com.lutqiu.lumiread.ui.components.ColorPickerDialog
import com.lutqiu.lumiread.ui.components.PageHeader
import com.lutqiu.lumiread.ui.theme.LumiColorTokens
import com.lutqiu.lumiread.ui.theme.lumiShape

private enum class ColorTarget(val title: String) {
    UI_BACKGROUND("UI 全局背景色"),
    UI_ACCENT("UI 重点色"),
    READER_BACKGROUND("阅读器背景色"),
    READER_ACCENT("阅读器重点色"),
}

/**
 * 全局设置（v0.4.2）。
 *
 * 6 维主题：圆润度 / 玻璃开关 / UI 背景 / UI 重点色 / 阅读背景 / 阅读重点色。
 * 每个维度独立持久化，互不覆盖；颜色支持 HEX 输入。
 */
@Composable
fun GlobalSettingsScreen(
    theme: ThemeConfig,
    onThemeChange: (ThemeConfig) -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    var readerSettings by remember { mutableStateOf(ReaderPrefs.load(context)) }
    fun updateReaderSettings(next: ReaderSettings) {
        readerSettings = next
        ReaderPrefs.save(context, next)
    }

    var picking by remember { mutableStateOf<ColorTarget?>(null) }
    var radiusDraft by remember { mutableFloatStateOf(theme.cornerRadiusDp.toFloat()) }
    LaunchedEffect(theme.cornerRadiusDp) { radiusDraft = theme.cornerRadiusDp.toFloat() }
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

            SectionTitle("外观 · 6 维主题")
            Spacer(Modifier.height(12.dp))

            // 1. UI 圆润度
            SettingCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "UI 圆润度",
                        style = TextStyle(fontSize = 13.sp, color = scheme.onSurface),
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Slider(
                        value = radiusDraft,
                        onValueChange = {
                            radiusDraft = it
                            onThemeChange(
                                theme.copy(
                                    cornerRadiusDp = it.toInt()
                                        .coerceIn(LumiColorTokens.MIN_RADIUS, LumiColorTokens.MAX_RADIUS),
                                ),
                            )
                        },
                        valueRange = LumiColorTokens.MIN_RADIUS.toFloat()..LumiColorTokens.MAX_RADIUS.toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${theme.cornerRadiusDp}dp",
                        style = TextStyle(fontSize = 11.sp, color = scheme.primary),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                // 2. 玻璃质感开关
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("玻璃质感", style = TextStyle(fontSize = 13.sp, color = scheme.onSurface))
                        Text(
                            "关闭后所有液态玻璃面板退化为纯色",
                            style = TextStyle(fontSize = 10.sp, color = scheme.onSurfaceVariant),
                        )
                    }
                    Switch(
                        checked = theme.glassEnabled,
                        onCheckedChange = { onThemeChange(theme.copy(glassEnabled = it)) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 3-6. 四色配置
            SettingCard {
                ColorRow(
                    label = ColorTarget.UI_BACKGROUND.title,
                    argb = theme.uiBackground,
                    onClick = { picking = ColorTarget.UI_BACKGROUND },
                )
                ColorRow(
                    label = ColorTarget.UI_ACCENT.title,
                    argb = theme.uiAccent,
                    onClick = { picking = ColorTarget.UI_ACCENT },
                )
                ColorRow(
                    label = ColorTarget.READER_BACKGROUND.title,
                    argb = theme.readerBackground,
                    onClick = { picking = ColorTarget.READER_BACKGROUND },
                )
                ColorRow(
                    label = ColorTarget.READER_ACCENT.title,
                    argb = theme.readerAccent,
                    onClick = { picking = ColorTarget.READER_ACCENT },
                )
            }

            Spacer(Modifier.height(22.dp))

            SectionTitle("阅读模式")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderPagingMode.entries.forEach { mode ->
                    ModeChip(
                        label = mode.label,
                        selected = readerSettings.pagingMode == mode,
                        onClick = { updateReaderSettings(readerSettings.copy(pagingMode = mode)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (readerSettings.pagingMode == ReaderPagingMode.SCROLL) {
                    "滚动模式：竖向上下滑动，实时更新进度。"
                } else {
                    "无动画模式：点屏幕左侧 40% 上一页、右侧 40% 下一页，瞬间切换；中间 20% 呼出控制台。"
                },
                style = TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant, lineHeight = 17.sp),
            )

            Spacer(Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = lumiShape(),
                color = scheme.surface,
                border = BorderStroke(1.dp, scheme.outlineVariant),
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(
                        text = "AI 视觉小说引擎",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "模型：${ApiConfig.ZHIPU_MODEL}",
                        style = TextStyle(fontSize = 12.sp, color = scheme.onSurfaceVariant),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (configured) "API Key：已内置配置" else "API Key：未填写（ApiConfig.kt）",
                        style = TextStyle(fontSize = 12.sp, color = if (configured) scheme.primary else scheme.error),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Key 与模型地址统一在 ApiConfig.kt 中维护，设置页不再提供输入。",
                style = TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant, lineHeight = 18.sp),
            )

            Spacer(Modifier.height(110.dp))
        }
    }

    // 颜色选择器
    picking?.let { target ->
        val initial = when (target) {
            ColorTarget.UI_BACKGROUND -> Color(theme.uiBackground)
            ColorTarget.UI_ACCENT -> Color(theme.uiAccent)
            ColorTarget.READER_BACKGROUND -> Color(theme.readerBackground)
            ColorTarget.READER_ACCENT -> Color(theme.readerAccent)
        }
        ColorPickerDialog(
            title = target.title,
            initial = initial,
            onDismiss = { picking = null },
            onConfirm = { color ->
                val argb = 0xFF000000L or (color.toArgb().toLong() and 0xFFFFFFFFL)
                val next = when (target) {
                    ColorTarget.UI_BACKGROUND -> theme.copy(uiBackground = argb)
                    ColorTarget.UI_ACCENT -> theme.copy(uiAccent = argb)
                    ColorTarget.READER_BACKGROUND -> theme.copy(readerBackground = argb)
                    ColorTarget.READER_ACCENT -> theme.copy(readerAccent = argb)
                }
                onThemeChange(next)
                picking = null
            },
        )
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = lumiShape(),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ColorRow(label: String, argb: Long, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(lumiShape(0.7f))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(1.dp, scheme.outline, CircleShape),
        )
        Text(
            text = label,
            style = TextStyle(fontSize = 13.sp, color = scheme.onSurface),
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        Text(
            text = String.format("#%06X", 0xFFFFFF and argb.toInt()),
            style = TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant),
        )
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
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(lumiShape())
            .background(if (selected) scheme.primary else scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
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
