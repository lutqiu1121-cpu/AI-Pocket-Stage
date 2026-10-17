package com.lutqiu.lumiread.ui.reader

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lutqiu.lumiread.data.Book
import com.lutqiu.lumiread.data.BookFormat
import com.lutqiu.lumiread.data.Chapter
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────────────────────────────────────
// 普通文字阅读器（第三期起替代纯黑 VN 骨架作为默认进入方式）
//
// 交互约定（因为「点中间」既要显隐状态栏、又要呼出设置面板，这里按节奏拆开）：
//   · 点顶部 / 底部区域   → 状态栏 + 返回按钮 显示 ⇄ 隐藏（全屏沉浸）
//   · 沉浸态点中间        → 先唤回状态栏
//   · 已显示时点中间      → 弹出设置面板（ModalBottomSheet）
//   · 点顶栏右侧「Aa」    → 任何时候都能直接打开设置面板
//   · 长按正文            → Toast「正在分析上下文，准备切换 VN 模式...」（第四期接 AI）
// ─────────────────────────────────────────────────────────────────────────────

/** 阅读背景主题：白 / 米黄 / 深灰。 */
private enum class ReaderPalette(val label: String, val background: Color, val ink: Color) {
    PAPER("白", Color(0xFFFFFFFF), Color(0xFF1F2024)),
    SEPIA("米黄", Color(0xFFF5EFE6), Color(0xFF3A3226)),
    NIGHT("深灰", Color(0xFF2B2B2B), Color(0xFFD9D9D9)),
}

private val DividerGray = Color(0xFFEEEEEE)
private val SheetContainer = Color(0xFFF7F8FA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: Book,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialChapterIndex: Int = 0,
) {
    val context = LocalContext.current
    val chapter: Chapter? = book.chapters.getOrNull(initialChapterIndex)

    // 沉浸（状态栏 / 返回按钮显隐）
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var sheetVisible by rememberSaveable { mutableStateOf(false) }

    // 阅读设置：全部 rememberSaveable，转屏 / 进程内重组都不丢
    var paletteIndex by rememberSaveable { mutableIntStateOf(0) }
    var fontSize by rememberSaveable { mutableStateOf(18f) }
    var lineHeightMultiplier by rememberSaveable { mutableStateOf(1.6f) }
    var aiEnabled by rememberSaveable { mutableStateOf(true) }

    val palette = ReaderPalette.entries[paletteIndex]
    val scrollState = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 底部状态栏：当前时间（每 30s 刷新，省电且看不出跳秒）
    var nowText by remember { mutableStateOf(formatClock(LocalTime.now())) }
    LaunchedEffect(Unit) {
        while (true) {
            nowText = formatClock(LocalTime.now())
            delay(30_000)
        }
    }

    // 阅读进度：滚动比例
    val progressPercent =
        if (scrollState.maxValue <= 0) 100 else scrollState.value * 100 / scrollState.maxValue

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            // 单击：分区处理显隐 / 设置；长按：AI 分析入口（本期只 Toast）
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val height = size.height.toFloat()
                        val inTopBand = offset.y < height * 0.18f
                        val inBottomBand = offset.y > height * 0.82f
                        when {
                            inTopBand || inBottomBand -> chromeVisible = !chromeVisible
                            chromeVisible -> sheetVisible = true
                            else -> chromeVisible = true
                        }
                    },
                    onLongPress = {
                        Toast.makeText(
                            context,
                            "正在分析上下文，准备切换 VN 模式...",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            },
    ) {
        // ① 正文
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(24.dp),
        ) {
            Text(
                text = chapter?.content.orEmpty(),
                style = TextStyle(
                    color = palette.ink,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * lineHeightMultiplier).sp,
                    textIndent = TextIndent(firstLine = 2.em),
                    letterSpacing = 0.2.sp,
                ),
            )
        }

        // ② 顶部：返回按钮 + 分割线
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            ReaderTopBar(
                palette = palette,
                onBack = onBack,
                onOpenSettings = { sheetVisible = true },
            )
        }

        // ③ 底部：40dp 分割线 + 时间 / 进度
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            ReaderBottomBar(
                palette = palette,
                time = nowText,
                chapterLabel = "第${(chapter?.index ?: 0) + 1}章",
                percent = progressPercent,
            )
        }
    }

    // ④ 设置面板
    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = sheetState,
            containerColor = SheetContainer,
        ) {
            ReaderSettingsPanel(
                paletteIndex = paletteIndex,
                onPaletteChange = { paletteIndex = it },
                fontSize = fontSize,
                onFontSizeChange = { fontSize = it },
                lineHeightMultiplier = lineHeightMultiplier,
                onLineHeightChange = { lineHeightMultiplier = it },
                aiEnabled = aiEnabled,
                onAiEnabledChange = { aiEnabled = it },
            )
        }
    }
}

// ── 顶栏 ────────────────────────────────────────────────────────────────────

@Composable
private fun ReaderTopBar(
    palette: ReaderPalette,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 返回
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "‹",
                    style = TextStyle(fontSize = 22.sp, color = palette.ink, fontWeight = FontWeight.Light),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "返回",
                    style = TextStyle(fontSize = 14.sp, color = palette.ink),
                )
            }

            // 设置入口（任何时候都能开面板，避免「必须点中间」的死角）
            Text(
                text = "Aa",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = TextStyle(fontSize = 15.sp, color = palette.ink, fontWeight = FontWeight.Medium),
            )
        }

        // 顶部区域极细分割线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DividerGray),
        )
    }
}

// ── 底栏 ────────────────────────────────────────────────────────────────────

@Composable
private fun ReaderBottomBar(
    palette: ReaderPalette,
    time: String,
    chapterLabel: String,
    percent: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.background),
    ) {
        // 底部分割线：距离屏幕底部 40dp（下面这一行就是 40dp 高的状态栏）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DividerGray),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = time,
                style = TextStyle(fontSize = 11.sp, color = palette.ink.copy(alpha = 0.55f)),
            )
            Text(
                text = "$chapterLabel $percent%",
                style = TextStyle(fontSize = 11.sp, color = palette.ink.copy(alpha = 0.55f)),
            )
        }
    }
}

// ── 设置面板（ModalBottomSheet 内容）────────────────────────────────────────

@Composable
private fun ReaderSettingsPanel(
    paletteIndex: Int,
    onPaletteChange: (Int) -> Unit,
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    lineHeightMultiplier: Float,
    onLineHeightChange: (Float) -> Unit,
    aiEnabled: Boolean,
    onAiEnabledChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Text(
            text = "阅读设置",
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2024)),
        )

        Spacer(Modifier.height(20.dp))

        // 背景色
        Text("背景色", style = TextStyle(fontSize = 13.sp, color = Color(0xFF6B7078)))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ReaderPalette.entries.forEachIndexed { index, entry ->
                PaletteDot(
                    palette = entry,
                    selected = index == paletteIndex,
                    onClick = { onPaletteChange(index) },
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // 字号 14–24
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("字号", style = TextStyle(fontSize = 13.sp, color = Color(0xFF6B7078)))
            Text(
                text = "${fontSize.toInt()}sp",
                style = TextStyle(fontSize = 13.sp, color = Color(0xFF3B82F6)),
            )
        }
        Slider(
            value = fontSize,
            onValueChange = onFontSizeChange,
            valueRange = 14f..24f,
            steps = 9,
        )

        Spacer(Modifier.height(8.dp))

        // 行间距
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("行间距", style = TextStyle(fontSize = 13.sp, color = Color(0xFF6B7078)))
            Text(
                text = String.format("%.1f×", lineHeightMultiplier),
                style = TextStyle(fontSize = 13.sp, color = Color(0xFF3B82F6)),
            )
        }
        Slider(
            value = lineHeightMultiplier,
            onValueChange = onLineHeightChange,
            valueRange = 1.2f..2.2f,
            steps = 9,
        )

        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerGray))
        Spacer(Modifier.height(16.dp))

        // AI 智能分析总开关
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI 智能分析",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1F2024)),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "开启后将调用大模型提取人物与场景，消耗 Token",
                    style = TextStyle(fontSize = 10.sp, color = Color(0xFF9AA0A6)),
                )
            }
            Switch(checked = aiEnabled, onCheckedChange = onAiEnabledChange)
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PaletteDot(
    palette: ReaderPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(palette.background)
                .clickable(onClick = onClick),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.Transparent),
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.18f)),
                    )
                }
            }
            // 深色系靠描边区分，浅色系靠边缘一层浅灰
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .then(
                        Modifier.border1dp(if (selected) Color(0xFF3B82F6) else Color(0xFFE3E5E8)),
                    ),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = palette.label,
            style = TextStyle(
                fontSize = 10.sp,
                color = if (selected) Color(0xFF3B82F6) else Color(0xFF9AA0A6),
            ),
        )
    }
}

private fun Modifier.border1dp(color: Color): Modifier =
    this.border(width = 1.dp, color = color, shape = CircleShape)

private fun formatClock(time: LocalTime): String =
    time.format(DateTimeFormatter.ofPattern("HH:mm"))

@Preview(name = "Reader", showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun ReaderScreenPreview() {
    ReaderScreen(
        book = Book(
            id = "preview",
            title = "示例书",
            format = BookFormat.TXT,
            chapters = listOf(
                Chapter(
                    index = 0,
                    title = "第一章",
                    content = "这是一段用于预览的正文。长按屏幕任意位置会触发 AI 分析提示，" +
                        "点击顶部或底部区域可以切换沉浸模式，点击中间会打开阅读设置面板。",
                ),
            ),
        ),
        onBack = {},
    )
}
