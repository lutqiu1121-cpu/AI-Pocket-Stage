package com.lutqiu.lumiread.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List as MenuListIcon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lutqiu.lumiread.data.Book
import com.lutqiu.lumiread.data.BookFormat
import com.lutqiu.lumiread.data.Chapter
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────────────────────────────────────
// 普通文字阅读器（v0.2.4：目录 + 章节跳转 + 章节内分页）
//
// 交互约定（严格按屏幕坐标分区）：
//   · 屏幕中心 60% 区域（X 20%~80% 且 Y 20%~80%）→ 弹出阅读设置面板
//   · 四周边缘（上下 20% + 左右边缘）→ 只切换顶底栏显隐，绝不弹面板
//   · 顶栏「目录」图标 → 目录面板；「Aa」→ 阅读设置；两者独立于分区
//   · 长按正文 → Toast「正在分析上下文，准备切换 VN 模式...」
//   · 进入本页即隐藏系统状态栏 / 导航栏，退出时恢复
//   · 章节内超过 1000 字自动分页，左右滑动翻页（每页内仍可上下滚动）
// ─────────────────────────────────────────────────────────────────────────────

/** 阅读背景主题：白 / 米黄 / 深灰。 */
private enum class ReaderPalette(val label: String, val background: Color, val ink: Color) {
    PAPER("白", Color(0xFFFFFFFF), Color(0xFF1F2024)),
    SEPIA("米黄", Color(0xFFF5EFE6), Color(0xFF3A3226)),
    NIGHT("深灰", Color(0xFF2B2B2B), Color(0xFFD9D9D9)),
}

private val DividerGray = Color(0xFFEEEEEE)
private val SheetContainer = Color(0xFFF7F8FA)
private val AccentBlue = Color(0xFF3B82F6)

/** 每个分页的目标字数。 */
private const val PAGE_CHARS = 1000

/** 超过这个字数才启用分页（短章节一页到底）。 */
private const val PAGING_THRESHOLD = 2000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: Book,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialChapterIndex: Int = 0,
) {
    val context = LocalContext.current
    val view = LocalView.current

    // 进入阅读页 → 系统状态栏 / 导航栏完全隐藏；离开（返回书架）时恢复。
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val lastIndex = (book.chapters.size - 1).coerceAtLeast(0)
    var chapterIndex by rememberSaveable(book.id) {
        mutableIntStateOf(initialChapterIndex.coerceIn(0, lastIndex))
    }

    // 沉浸（顶栏 / 底栏显隐）
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var sheetVisible by rememberSaveable { mutableStateOf(false) }
    var tocVisible by rememberSaveable { mutableStateOf(false) }

    // 阅读设置：rememberSaveable，转屏 / 进程内重组都不丢
    var paletteIndex by rememberSaveable { mutableIntStateOf(0) }
    var fontSize by rememberSaveable { mutableStateOf(18f) }
    var lineHeightMultiplier by rememberSaveable { mutableStateOf(1.6f) }
    var aiEnabled by rememberSaveable { mutableStateOf(true) }

    val palette = ReaderPalette.entries[paletteIndex]
    val chapter: Chapter? = book.chapters.getOrNull(chapterIndex)

    // 章节内分页：长章按 ~1000 字切页；短章就一页。
    val pages = remember(chapterIndex, chapter?.content) {
        paginate(chapter?.content.orEmpty())
    }
    // key(chapterIndex)：换章重建 pager，自动回到第一页
    val pagerState = key(chapterIndex) {
        rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    }

    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tocSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 底部状态栏：当前时间（每 30s 刷新）
    var nowText by remember { mutableStateOf(formatClock(LocalTime.now())) }
    LaunchedEffect(Unit) {
        while (true) {
            nowText = formatClock(LocalTime.now())
            delay(30_000)
        }
    }

    val percent = ((pagerState.currentPage + 1) * 100 / pages.size.coerceAtLeast(1)).coerceIn(0, 100)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            // 单击分区 / 长按 AI 入口
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()

                        // 中间安全区：X 在 20%~80%、Y 在 20%~80%（屏幕中心 60% 区域）
                        val isCenterClick = offset.x > width * 0.2f && offset.x < width * 0.8f &&
                            offset.y > height * 0.2f && offset.y < height * 0.8f

                        if (isCenterClick) {
                            // 只有正中心才允许弹设置面板
                            sheetVisible = true
                        } else {
                            // 四周边缘只切换顶底栏显隐，绝不弹面板
                            chromeVisible = !chromeVisible
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
        // ① 正文（横向分页；每页内部可纵向滚动）
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val pageText = pages.getOrElse(page) { "" }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = pageText,
                    style = TextStyle(
                        color = palette.ink,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * lineHeightMultiplier).sp,
                        textIndent = TextIndent(firstLine = 2.em),
                        letterSpacing = 0.2.sp,
                    ),
                )
            }
        }

        // ② 顶部：返回 / 目录 / Aa + 分割线
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            ReaderTopBar(
                palette = palette,
                onBack = onBack,
                onOpenToc = {
                    sheetVisible = false
                    tocVisible = true
                },
                onOpenSettings = {
                    tocVisible = false
                    sheetVisible = true
                },
            )
        }

        // ③ 底部：40dp 状态栏（时间 / 章节 + 页码进度）
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            ReaderBottomBar(
                palette = palette,
                time = nowText,
                chapterLabel = "第${chapterIndex + 1}章",
                percent = percent,
                pageLabel = if (pages.size > 1) "${pagerState.currentPage + 1}/${pages.size}" else null,
            )
        }
    }

    // ④ 目录面板
    if (tocVisible) {
        ModalBottomSheet(
            onDismissRequest = { tocVisible = false },
            sheetState = tocSheetState,
            containerColor = SheetContainer,
        ) {
            TocPanel(
                chapters = book.chapters,
                currentIndex = chapterIndex,
                onSelect = { index ->
                    chapterIndex = index
                    tocVisible = false
                },
            )
        }
    }

    // ⑤ 阅读设置面板
    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = settingsSheetState,
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

// ── 目录面板 ────────────────────────────────────────────────────────────────

@Composable
private fun TocPanel(
    chapters: List<Chapter>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "目录",
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2024)),
            )
            Text(
                text = "共 ${chapters.size} 章",
                style = TextStyle(fontSize = 12.sp, color = Color(0xFF9AA0A6)),
            )
        }

        Spacer(Modifier.height(12.dp))

        if (chapters.isEmpty()) {
            Text(
                text = "没有解析到章节",
                style = TextStyle(fontSize = 13.sp, color = Color(0xFF9AA0A6)),
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                itemsIndexed(items = chapters, key = { index, _ -> index }) { index, chapter ->
                    val current = index == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(index) }
                            .background(if (current) AccentBlue.copy(alpha = 0.10f) else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = (index + 1).toString().padStart(3, ' '),
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = if (current) AccentBlue else Color(0xFFB0B4BA),
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = chapter.title,
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                                // 当前章用主题色加亮
                                color = if (current) AccentBlue else Color(0xFF3A3D42),
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── 顶栏 ────────────────────────────────────────────────────────────────────

@Composable
private fun ReaderTopBar(
    palette: ReaderPalette,
    onBack: () -> Unit,
    onOpenToc: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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

                // 目录
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOpenToc)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuListIcon,
                        contentDescription = "目录",
                        tint = palette.ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // 阅读设置入口
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
    pageLabel: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.background),
    ) {
        // 底部分割线：下方 40dp 即状态栏
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                pageLabel?.let {
                    Text(
                        text = "$it · ",
                        style = TextStyle(fontSize = 11.sp, color = palette.ink.copy(alpha = 0.4f)),
                    )
                }
                Text(
                    text = "$chapterLabel $percent%",
                    style = TextStyle(fontSize = 11.sp, color = palette.ink.copy(alpha = 0.55f)),
                )
            }
        }
    }
}

// ── 设置面板 ────────────────────────────────────────────────────────────────

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("字号", style = TextStyle(fontSize = 13.sp, color = Color(0xFF6B7078)))
            Text(
                text = "${fontSize.toInt()}sp",
                style = TextStyle(fontSize = 13.sp, color = AccentBlue),
            )
        }
        Slider(
            value = fontSize,
            onValueChange = onFontSizeChange,
            valueRange = 14f..24f,
            steps = 9,
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("行间距", style = TextStyle(fontSize = 13.sp, color = Color(0xFF6B7078)))
            Text(
                text = String.format("%.1f×", lineHeightMultiplier),
                style = TextStyle(fontSize = 13.sp, color = AccentBlue),
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
                .border(
                    width = 1.dp,
                    color = if (selected) AccentBlue else Color(0xFFE3E5E8),
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(AccentBlue.copy(alpha = 0.18f)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = palette.label,
            style = TextStyle(
                fontSize = 10.sp,
                color = if (selected) AccentBlue else Color(0xFF9AA0A6),
            ),
        )
    }
}

// ── 章节内分页 ──────────────────────────────────────────────────────────────

/**
 * 把一章正文切成若干页：
 * - 不足 [PAGING_THRESHOLD] 字 → 原样一页；
 * - 超过则每 [PAGE_CHARS] 字一页，且优先在段落（\n\n）或句号处断页，避免断在半句话上。
 */
internal fun paginate(text: String, pageChars: Int = PAGE_CHARS): List<String> {
    if (text.isEmpty()) return listOf("")
    if (text.length <= PAGING_THRESHOLD) return listOf(text)

    val pages = mutableListOf<String>()
    var cursor = 0
    while (cursor < text.length) {
        val tentativeEnd = minOf(cursor + pageChars, text.length)
        var end = tentativeEnd
        if (tentativeEnd < text.length) {
            val window = text.substring(cursor, tentativeEnd)
            val cut = maxOf(window.lastIndexOf("\n\n"), window.lastIndexOf('。'))
            if (cut >= (pageChars * 0.6).toInt()) {
                end = cursor + cut + 1
            }
        }
        val piece = text.substring(cursor, end).trim()
        if (piece.isNotEmpty()) pages += piece
        cursor = end
    }
    return pages.ifEmpty { listOf(text) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

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
                    title = "第一章 起点",
                    content = "这是一段用于预览的正文。长按屏幕任意位置会触发 AI 分析提示，" +
                        "点击屏幕中心会打开阅读设置面板，点击四周边缘可切换顶底栏显隐。",
                ),
                Chapter(
                    index = 1,
                    title = "第二章 继续",
                    content = "第二章的正文内容。",
                ),
            ),
        ),
        onBack = {},
    )
}
