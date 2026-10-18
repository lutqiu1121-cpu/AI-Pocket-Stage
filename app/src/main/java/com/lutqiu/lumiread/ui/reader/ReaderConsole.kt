package com.lutqiu.lumiread.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt
import com.lutqiu.lumiread.data.Chapter
import com.lutqiu.lumiread.data.ReaderSettings
import com.lutqiu.lumiread.data.ReaderPagingMode
import com.lutqiu.lumiread.data.ApiConfig
import com.lutqiu.lumiread.ui.components.LiquidGlassPanel
import com.lutqiu.lumiread.ui.theme.LumiColors
import com.lutqiu.lumiread.ui.theme.lumiShape

// ─────────────────────────────────────────────────────────────────────────────
// 沉浸式控制台（液态玻璃）
//   Home      ：进度条（上一章 / 下一章）+ 目录 / 界面 / 设置
//   目录      ：章节列表
//   界面      ：字重 / 缩进 / 边距 + 四个滑块 + 阅读器主题
//   设置      ：AI 开关（Key 已在 ApiConfig.kt 内置）
// ─────────────────────────────────────────────────────────────────────────────

private const val PAGE_HOME = 0
private const val PAGE_TOC = 1
private const val PAGE_INTERFACE = 2
private const val PAGE_SETTINGS = 3

@Composable
fun ReaderConsole(
    chapters: List<Chapter>,
    currentChapter: Int,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onJumpChapter: (Int) -> Unit,
) {
    var page by remember { mutableIntStateOf(PAGE_HOME) }
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 10.dp),
    ) {
        if (page != PAGE_HOME) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "‹ 返回",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { page = PAGE_HOME }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = TextStyle(color = scheme.onSurfaceVariant, fontSize = 13.sp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when (page) {
                        PAGE_TOC -> "目录"
                        PAGE_INTERFACE -> "界面"
                        else -> "设置"
                    },
                    style = TextStyle(
                        color = scheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }

        when (page) {
            PAGE_HOME -> ConsoleHome(
                totalChapters = chapters.size,
                currentChapter = currentChapter,
                onPrev = { if (currentChapter > 0) onJumpChapter(currentChapter - 1) },
                onNext = { if (currentChapter < chapters.lastIndex) onJumpChapter(currentChapter + 1) },
                onJumpChapter = onJumpChapter,
                onOpen = { page = it },
            )

            PAGE_TOC -> ConsoleToc(
                chapters = chapters,
                currentChapter = currentChapter,
                onSelect = {
                    onJumpChapter(it)
                    page = PAGE_HOME
                },
            )

            PAGE_INTERFACE -> ConsoleInterface(
                settings = settings,
                onSettingsChange = onSettingsChange,
            )

            PAGE_SETTINGS -> ConsoleSettings(settings = settings, onSettingsChange = onSettingsChange)
        }
    }
}

// ── Home ────────────────────────────────────────────────────────────────────

@Composable
private fun ConsoleHome(
    totalChapters: Int,
    currentChapter: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJumpChapter: (Int) -> Unit,
    onOpen: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val progress = if (totalChapters <= 1) 1f else (currentChapter + 1).toFloat() / totalChapters

    Column(Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(6.dp))

        var draft by remember(currentChapter, totalChapters) {
            mutableFloatStateOf(currentChapter.toFloat())
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "上一章",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = currentChapter > 0, onClick = onPrev)
                    .alpha(if (currentChapter > 0) 1f else 0.35f)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                style = TextStyle(color = scheme.onSurfaceVariant, fontSize = 12.sp),
            )
            if (totalChapters > 1) {
                Slider(
                    value = draft.coerceIn(0f, (totalChapters - 1).toFloat()),
                    onValueChange = { draft = it },
                    onValueChangeFinished = { onJumpChapter(draft.roundToInt()) },
                    valueRange = 0f..(totalChapters - 1).toFloat(),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = scheme.primary,
                        activeTrackColor = scheme.primary,
                        inactiveTrackColor = scheme.onSurfaceVariant.copy(alpha = 0.24f),
                    ),
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f).height(4.dp).padding(horizontal = 10.dp).clip(CircleShape),
                    color = scheme.primary,
                    trackColor = scheme.onSurfaceVariant.copy(alpha = 0.24f),
                )
            }
            Text(
                text = "下一章",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = currentChapter < totalChapters - 1, onClick = onNext)
                    .alpha(if (currentChapter < totalChapters - 1) 1f else 0.35f)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                style = TextStyle(color = scheme.onSurfaceVariant, fontSize = 12.sp),
            )
        }

        Spacer(Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ConsoleAction("☰", "目录") { onOpen(PAGE_TOC) }
            ConsoleAction("Aa", "界面") { onOpen(PAGE_INTERFACE) }
            ConsoleAction("⚙", "设置") { onOpen(PAGE_SETTINGS) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ConsoleAction(glyph: String, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(lumiShape())
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(text = glyph, style = TextStyle(color = scheme.onSurface, fontSize = 20.sp))
        Spacer(Modifier.height(6.dp))
        Text(text = label, style = TextStyle(color = scheme.onSurfaceVariant, fontSize = 12.sp))
    }
}

// ── 目录 ────────────────────────────────────────────────────────────────────

@Composable
private fun ConsoleToc(
    chapters: List<Chapter>,
    currentChapter: Int,
    onSelect: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "共 ${chapters.size} 章",
            style = TextStyle(color = scheme.onSurfaceVariant, fontSize = 12.sp),
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
            itemsIndexed(items = chapters, key = { index, _ -> index }) { index, chapter ->
                val current = index == currentChapter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(lumiShape(0.7f))
                        .clickable { onSelect(index) }
                        .background(if (current) scheme.primary.copy(alpha = 0.16f) else LumiColors.Transparent)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = if (current) scheme.primary else scheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.width(32.dp),
                    )
                    Text(
                        text = chapter.title,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (current) scheme.primary else scheme.onSurface,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ── 界面（高级排版）──────────────────────────────────────────────────────────

@Composable
private fun ConsoleInterface(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ConsoleChip(
                label = "字重 ${fontWeightLabel(settings.fontWeight)}",
                selected = settings.fontWeight != 1,
                onClick = {
                    onSettingsChange(settings.copy(fontWeight = (settings.fontWeight + 1) % 3))
                },
            )
            ConsoleChip(
                label = "缩进 ${if (settings.indentEnabled) "开" else "关"}",
                selected = settings.indentEnabled,
                onClick = { onSettingsChange(settings.copy(indentEnabled = !settings.indentEnabled)) },
            )
            ConsoleChip(
                label = "边距 ${settings.marginDp}",
                selected = false,
                onClick = {
                    val next = when (settings.marginDp) {
                        16 -> 24
                        24 -> 32
                        else -> 16
                    }
                    onSettingsChange(settings.copy(marginDp = next))
                },
            )
        }

        Spacer(Modifier.height(14.dp))

        SliderRow(
            label = "字号",
            value = settings.fontSize,
            valueText = "${settings.fontSize.toInt()}",
            range = 14f..30f,
            step = 1f,
            onChange = { onSettingsChange(settings.copy(fontSize = it)) },
        )
        SliderRow(
            label = "字距",
            value = settings.letterSpacing,
            valueText = String.format("%.1f", settings.letterSpacing),
            range = -0.5f..2f,
            step = 0.1f,
            onChange = { onSettingsChange(settings.copy(letterSpacing = it)) },
        )
        SliderRow(
            label = "行距",
            value = settings.lineHeight,
            valueText = String.format("%.1f×", settings.lineHeight),
            range = 1.2f..2.4f,
            step = 0.1f,
            onChange = { onSettingsChange(settings.copy(lineHeight = it)) },
        )
        SliderRow(
            label = "段距",
            value = settings.paragraphSpacing.toFloat(),
            valueText = "${settings.paragraphSpacing}",
            range = 0f..32f,
            step = 2f,
            onChange = { onSettingsChange(settings.copy(paragraphSpacing = it.toInt())) },
        )

        Spacer(Modifier.height(10.dp))

        Text("阅读模式", style = TextStyle(color = scheme.onSurfaceVariant, fontSize = 12.sp))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConsoleChip(
                "滚动",
                selected = settings.pagingMode == ReaderPagingMode.SCROLL,
                onClick = { onSettingsChange(settings.copy(pagingMode = ReaderPagingMode.SCROLL)) },
            )
            ConsoleChip(
                "无动画",
                selected = settings.pagingMode == ReaderPagingMode.PAGED,
                onClick = { onSettingsChange(settings.copy(pagingMode = ReaderPagingMode.PAGED)) },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "无动画：左 40% 上一页 / 右 40% 下一页 / 中间 20% 呼出控制台",
            style = TextStyle(color = scheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 10.sp),
        )

        Spacer(Modifier.height(28.dp))
    }
}

private fun fontWeightLabel(index: Int): String = when (index) {
    0 -> "细"
    2 -> "粗"
    else -> "中"
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = TextStyle(color = scheme.onSurfaceVariant, fontSize = 13.sp),
            modifier = Modifier.width(38.dp),
        )
        StepButton("-") { onChange((value - step).coerceIn(range.start, range.endInclusive)) }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        StepButton("+") { onChange((value + step).coerceIn(range.start, range.endInclusive)) }
        Text(
            text = valueText,
            style = TextStyle(color = scheme.primary, fontSize = 12.sp),
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(scheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = TextStyle(color = scheme.onSurface, fontSize = 14.sp))
    }
}

@Composable
private fun ConsoleChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(lumiShape())
            .background(if (selected) scheme.primary else scheme.surfaceVariant)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = if (selected) scheme.onPrimary else scheme.onSurface,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
        )
    }
}

// ── 设置（AI）───────────────────────────────────────────────────────────────

@Composable
private fun ConsoleSettings(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "AI 智能分析",
                    style = TextStyle(color = scheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    "开启后调用 ${ApiConfig.ZHIPU_MODEL} 提取人物与场景，消耗 Token",
                    style = TextStyle(color = scheme.onSurfaceVariant, fontSize = 10.sp),
                )
            }
            Switch(
                checked = settings.aiEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(aiEnabled = it)) },
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "API Key 已内置在 ApiConfig.kt，设置页不再提供输入框。",
            style = TextStyle(color = scheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 10.sp),
        )

        Spacer(Modifier.height(20.dp))
    }
}

// ── 全局二级菜单（控制台）────────────────────────────────────────────────────

/**
 * 全屏悬浮液态玻璃控制台。
 *
 * - 0.4 黑遮罩，点按关闭；
 * - 面板贴底，纯玻璃质感：模糊 + 低透明度底色 + 边缘高光描边 + 向上阴影；
 * - 底部 navigationBarsPadding 保证系统导航栏安全区。
 */
@Composable
fun ReaderConsoleOverlay(
    visible: Boolean,
    hazeState: HazeState,
    chapters: List<Chapter>,
    currentChapter: Int,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onJumpChapter: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LumiColors.Scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            LiquidGlassPanel(
                hazeState = hazeState,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                shape = lumiShape(),
                strong = true,
            ) {
                Column {
                    ReaderConsole(
                        chapters = chapters,
                        currentChapter = currentChapter,
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                        onJumpChapter = onJumpChapter,
                    )
                }
            }
        }
    }
}
