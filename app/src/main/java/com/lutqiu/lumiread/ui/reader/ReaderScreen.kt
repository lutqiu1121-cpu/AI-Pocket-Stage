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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import com.lutqiu.lumiread.services.AiScene
import com.lutqiu.lumiread.services.AiSceneService
import com.lutqiu.lumiread.services.ApiConfigRepository
import com.lutqiu.lumiread.services.HttpStatusException
import com.lutqiu.lumiread.services.RequestQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────────────────────────────────────
// 阅读器 v0.3.0
//
// · 整章放进一个竖向滚动容器（无分页、无翻页），滚动位置即真实进度
// · 点屏幕中心 60% 区域 → 弹出面板（Tab：目录 / 设置）
// · 点四周边缘 → 顶/底栏显隐
// · 长按正文 → ±2 句上下文 → 智谱分析 → Pollinations 生图 → VN 舞台
// ─────────────────────────────────────────────────────────────────────────────

private enum class ReaderPalette(val label: String, val background: Color, val ink: Color) {
    PAPER("白", Color(0xFFFFFFFF), Color(0xFF1F2024)),
    SEPIA("米黄", Color(0xFFF5EFE6), Color(0xFF3A3226)),
    NIGHT("深灰", Color(0xFF2B2B2B), Color(0xFFD9D9D9)),
}

private val DividerGray = Color(0xFFEEEEEE)
private val SheetContainer = Color(0xFFF7F8FA)
private val AccentBlue = Color(0xFF3B82F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: Book,
    onBack: () -> Unit,
    onOpenScene: (AiScene) -> Unit,
    modifier: Modifier = Modifier,
    initialChapterIndex: Int = 0,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // 进入阅读页 → 系统状态栏 / 导航栏完全隐藏；离开时恢复
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val lastIndex = (book.chapters.size - 1).coerceAtLeast(0)
    var chapterIndex by rememberSaveable(book.id) {
        mutableIntStateOf(initialChapterIndex.coerceIn(0, lastIndex))
    }

    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var sheetVisible by rememberSaveable { mutableStateOf(false) }
    var sheetTab by rememberSaveable { mutableIntStateOf(0) }

    var paletteIndex by rememberSaveable { mutableIntStateOf(0) }
    var fontSize by rememberSaveable { mutableStateOf(18f) }
    var lineHeightMultiplier by rememberSaveable { mutableStateOf(1.6f) }
    var aiEnabled by rememberSaveable { mutableStateOf(true) }

    var loadingText by remember { mutableStateOf<String?>(null) }

    val palette = ReaderPalette.entries[paletteIndex]
    val chapter: Chapter? = book.chapters.getOrNull(chapterIndex)
    val scrollState = rememberScrollState()

    // 换章自动回到顶部
    LaunchedEffect(chapterIndex) { scrollState.scrollTo(0) }

    var nowText by remember { mutableStateOf(formatClock(LocalTime.now())) }
    LaunchedEffect(Unit) {
        while (true) {
            nowText = formatClock(LocalTime.now())
            delay(30_000)
        }
    }

    // 真实进度：当前滚动位置 / 可滚动范围
    val percent =
        if (scrollState.maxValue <= 0) 100 else (scrollState.value * 100 / scrollState.maxValue).coerceIn(0, 100)
    val scrollFraction =
        if (scrollState.maxValue <= 0) 0f else scrollState.value.toFloat() / scrollState.maxValue

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val isCenterClick = offset.x > width * 0.2f && offset.x < width * 0.8f &&
                            offset.y > height * 0.2f && offset.y < height * 0.8f
                        if (isCenterClick) {
                            sheetVisible = true
                        } else {
                            chromeVisible = !chromeVisible
                        }
                    },
                    onLongPress = {
                        val content = chapter?.content.orEmpty()
                        if (content.isBlank() || loadingText != null) return@detectTapGestures
                        if (!aiEnabled) {
                            Toast.makeText(context, "AI 智能分析已关闭（设置里可开启）", Toast.LENGTH_SHORT).show()
                            return@detectTapGestures
                        }
                        val snippet = extractContext(content, scrollFraction)
                        loadingText = "AI 构思场景…"
                        scope.launch {
                            try {
                                val scene = RequestQueue.submit("ai-analyze") {
                                    AiSceneService.analyze(context, snippet)
                                }
                                if (!scene.changed) {
                                    loadingText = null
                                    Toast.makeText(context, "这一段没有场景变化", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                loadingText = "AI 绘制画面…"
                                val seed = System.currentTimeMillis() % 1_000_000
                                val imagePath = RequestQueue.submit("ai-image") {
                                    AiSceneService.generateImage(context, scene.prompt, seed)
                                }
                                loadingText = null
                                onOpenScene(scene.copy(imagePath = imagePath))
                            } catch (t: Throwable) {
                                loadingText = null
                                Toast.makeText(context, friendlyError(t), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                )
            },
    ) {
        // ① 正文：整章一个竖向滚动容器
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(
                text = chapter?.content.orEmpty(),
                style = TextStyle(
                    color = palette.ink,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * lineHeightMultiplier).sp,
                    textIndent = TextIndent(firstLine = 2.em),
                    textAlign = TextAlign.Justify,
                    letterSpacing = 0.2.sp,
                ),
            )
            Spacer(Modifier.height(40.dp))
        }

        // ② 顶栏（只保留返回）
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            ReaderTopBar(palette = palette, onBack = onBack)
        }

        // ③ 底栏：时间 + 第N章 xx%
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
            )
        }

        // ④ Loading 遮罩
        loadingText?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = AccentBlue,
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(text = message, style = TextStyle(fontSize = 14.sp, color = Color(0xFF1F2024)))
                    }
                }
            }
        }
    }

    // ⑤ 合并面板：目录 / 设置
    if (sheetVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = sheetState,
            containerColor = SheetContainer,
        ) {
            Column(Modifier.fillMaxWidth()) {
                PrimaryTabRow(
                    selectedTabIndex = sheetTab,
                    containerColor = SheetContainer,
                    contentColor = AccentBlue,
                ) {
                    Tab(
                        selected = sheetTab == 0,
                        onClick = { sheetTab = 0 },
                        text = { Text("目录", fontSize = 14.sp) },
                    )
                    Tab(
                        selected = sheetTab == 1,
                        onClick = { sheetTab = 1 },
                        text = { Text("设置", fontSize = 14.sp) },
                    )
                }

                if (sheetTab == 0) {
                    TocList(
                        chapters = book.chapters,
                        currentIndex = chapterIndex,
                        onSelect = {
                            chapterIndex = it
                            sheetVisible = false
                        },
                    )
                } else {
                    ReaderSettingsPane(
                        context = context,
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
    }
}

private fun friendlyError(t: Throwable): String = when {
    t is HttpStatusException && t.code == 401 -> "智谱鉴权失败（401）：请检查 API Key"
    t is HttpStatusException && t.code == 429 -> "请求过于频繁（429 / 触发限流）"
    t is java.net.SocketTimeoutException -> "AI 请求超时，请稍后重试"
    t is java.io.IOException -> "网络异常：${t.message ?: "未知"}"
    else -> "AI 失败：${t.message ?: t.javaClass.simpleName}"
}

// ── 目录列表 ────────────────────────────────────────────────────────────────

@Composable
private fun TocList(
    chapters: List<Chapter>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("共 ${chapters.size} 章", style = TextStyle(fontSize = 12.sp, color = Color(0xFF9AA0A6)))
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
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
                        text = (index + 1).toString(),
                        style = TextStyle(fontSize = 11.sp, color = if (current) AccentBlue else Color(0xFFB0B4BA)),
                        modifier = Modifier.width(32.dp),
                    )
                    Text(
                        text = chapter.title,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (current) AccentBlue else Color(0xFF3A3D42),
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ── 顶栏 / 底栏 ─────────────────────────────────────────────────────────────

@Composable
private fun ReaderTopBar(palette: ReaderPalette, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("‹", style = TextStyle(fontSize = 22.sp, color = palette.ink, fontWeight = FontWeight.Light))
                Spacer(Modifier.width(4.dp))
                Text("返回", style = TextStyle(fontSize = 14.sp, color = palette.ink))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerGray))
    }
}

@Composable
private fun ReaderBottomBar(
    palette: ReaderPalette,
    time: String,
    chapterLabel: String,
    percent: Int,
) {
    Column(Modifier.fillMaxWidth().background(palette.background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerGray))
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(time, style = TextStyle(fontSize = 11.sp, color = palette.ink.copy(alpha = 0.55f)))
            Text("$chapterLabel $percent%", style = TextStyle(fontSize = 11.sp, color = palette.ink.copy(alpha = 0.55f)))
        }
    }
}

// ── 设置面板 ────────────────────────────────────────────────────────────────

@Composable
private fun ReaderSettingsPane(
    context: Context,
    paletteIndex: Int,
    onPaletteChange: (Int) -> Unit,
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    lineHeightMultiplier: Float,
    onLineHeightChange: (Float) -> Unit,
    aiEnabled: Boolean,
    onAiEnabledChange: (Boolean) -> Unit,
) {
    var apiKey by remember { mutableStateOf(ApiConfigRepository.getZhipuKey(context)) }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))

        Text("背景色", style = TextStyle(fontSize = 13.sp, color = Color(0xFF6B7078)))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ReaderPalette.entries.forEachIndexed { index, entry ->
                PaletteDot(entry, selected = index == paletteIndex, onClick = { onPaletteChange(index) })
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("字号", style = TextStyle(fontSize = 13.sp, color = Color(0xFF6B7078)))
            Text("${fontSize.toInt()}sp", style = TextStyle(fontSize = 13.sp, color = AccentBlue))
        }
        Slider(value = fontSize, onValueChange = onFontSizeChange, valueRange = 14f..24f, steps = 9)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("行间距", style = TextStyle(fontSize = 13.sp, color = Color(0xFF6B7078)))
            Text(String.format("%.1f×", lineHeightMultiplier), style = TextStyle(fontSize = 13.sp, color = AccentBlue))
        }
        Slider(value = lineHeightMultiplier, onValueChange = onLineHeightChange, valueRange = 1.2f..2.2f, steps = 9)

        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerGray))
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "AI 智能分析",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1F2024)),
                )
                Text(
                    "开启后将调用大模型提取人物与场景，消耗 Token",
                    style = TextStyle(fontSize = 10.sp, color = Color(0xFF9AA0A6)),
                )
            }
            Switch(checked = aiEnabled, onCheckedChange = onAiEnabledChange)
        }

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                saved = false
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("智谱 API Key", fontSize = 12.sp) },
            placeholder = { Text("粘贴 Key；填 MOCK 可离线验收", fontSize = 12.sp) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                ApiConfigRepository.setZhipuKey(context, apiKey)
                saved = true
                Toast.makeText(context, "API Key 已保存", Toast.LENGTH_SHORT).show()
            }) {
                Text(if (saved) "已保存" else "保存", color = AccentBlue)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PaletteDot(palette: ReaderPalette, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(palette.background)
                .border(1.dp, if (selected) AccentBlue else Color(0xFFE3E5E8), CircleShape)
                .clickable(onClick = onClick),
        ) {
            if (selected) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(AccentBlue.copy(alpha = 0.18f)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            palette.label,
            style = TextStyle(fontSize = 10.sp, color = if (selected) AccentBlue else Color(0xFF9AA0A6)),
        )
    }
}

// ── 上下文抽取（长按 ±2 句）──────────────────────────────────────────────────

internal fun extractContext(text: String, fraction: Float, before: Int = 2, after: Int = 2): String {
    if (text.isBlank()) return ""
    val sentences = splitSentences(text)
    if (sentences.isEmpty()) return text.take(400)
    val index = (fraction.coerceIn(0f, 0.999f) * sentences.size).toInt().coerceIn(0, sentences.lastIndex)
    val from = (index - before).coerceAtLeast(0)
    val to = (index + after + 1).coerceAtMost(sentences.size)
    return sentences.subList(from, to).joinToString("")
}

internal fun splitSentences(text: String): List<String> {
    val out = mutableListOf<String>()
    val buffer = StringBuilder()
    text.forEach { c ->
        buffer.append(c)
        if (c == '。' || c == '！' || c == '？' || c == '\n') {
            val sentence = buffer.toString().trim()
            if (sentence.isNotEmpty()) out += sentence
            buffer.clear()
        }
    }
    val tail = buffer.toString().trim()
    if (tail.isNotEmpty()) out += tail
    return out
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
                    content = "这是一段用于预览的正文。长按屏幕任意位置会触发 AI 分析，" +
                        "点击屏幕中心会打开目录 / 设置面板。",
                ),
            ),
        ),
        onBack = {},
        onOpenScene = {},
    )
}
