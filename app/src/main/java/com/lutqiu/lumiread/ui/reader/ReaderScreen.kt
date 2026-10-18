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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lutqiu.lumiread.data.Book
import com.lutqiu.lumiread.data.ReaderPrefs
import com.lutqiu.lumiread.data.ReaderSettings
import com.lutqiu.lumiread.data.ReaderTheme
import com.lutqiu.lumiread.services.AiScene
import com.lutqiu.lumiread.services.AiSceneService
import com.lutqiu.lumiread.services.HttpStatusException
import com.lutqiu.lumiread.services.RequestQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────────────────────────────────────
// 阅读器 v0.3.2
//
// · LazyColumn 段落流：滑到当前章倒数第 2 段时静默追加下一章，无按钮、无断点
// · 段间距由用户滑块控制（UI 层 verticalArrangement），解析层只保证 \n\n
// · 点屏幕中心 → 黑色控制台（进度条 / 目录 / 界面 / 设置）
// · 全部排版参数持久化到 SharedPreferences
// ─────────────────────────────────────────────────────────────────────────────

private val DividerGray = Color(0xFFEEEEEE)
private val AccentBlue = Color(0xFF3B82F6)

/** 列表里的一段正文。 */
private data class ReaderItem(
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val text: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: Book,
    onBack: () -> Unit,
    onOpenVn: (com.lutqiu.lumiread.data.VnLaunch) -> Unit,
    modifier: Modifier = Modifier,
    initialChapterIndex: Int = 0,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val clipboard = remember(context) {
        context.getSystemService(android.content.ClipboardManager::class.java)
    }

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

    // 排版参数：启动时从 SharedPreferences 读，改动即写回
    var settings by remember { mutableStateOf(ReaderPrefs.load(context)) }
    fun updateSettings(next: ReaderSettings) {
        settings = next
        ReaderPrefs.save(context, next)
    }

    val lastIndex = (book.chapters.size - 1).coerceAtLeast(0)
    val startIndex = initialChapterIndex.coerceIn(0, lastIndex)

    var chapterIndex by remember(book.id) { mutableIntStateOf(startIndex) }
    // 已加载进列表的章节（从 chapterIndex 起连续），用于无感知追加
    var loadedChapters by remember(book.id) { mutableStateOf(listOf(startIndex)) }
    // 显式跳章时 +1，触发回到顶部；自动追加不会变
    var jumpToken by remember { mutableIntStateOf(0) }

    var chromeVisible by remember { mutableStateOf(true) }
    var consoleVisible by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf<String?>(null) }
    var menuTarget by remember { mutableStateOf<MenuTarget?>(null) }

    val theme = ReaderTheme.entries[settings.theme.coerceIn(0, ReaderTheme.entries.lastIndex)]
    val bg = Color(theme.background)
    val ink = Color(theme.ink)

    val listState = rememberLazyListState()

    val items = remember(loadedChapters, book.id) {
        loadedChapters.flatMap { ci ->
            val chapter = book.chapters.getOrNull(ci)
            if (chapter == null) {
                emptyList()
            } else {
                paragraphsOf(chapter.content).mapIndexed { pi, text ->
                    ReaderItem(chapterIndex = ci, paragraphIndex = pi, text = text)
                }
            }
        }
    }

    // 显式跳章：回到列表顶部
    LaunchedEffect(jumpToken) {
        if (jumpToken > 0) listState.scrollToItem(0)
    }

    // 无感知续章：倒数第 2 段可见时追加下一章
    LaunchedEffect(listState, items.size, loadedChapters) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                if (lastVisible >= items.size - 2) {
                    val last = loadedChapters.lastOrNull() ?: return@collect
                    if (last < book.chapters.lastIndex) {
                        val next = last + 1
                        loadedChapters = loadedChapters + next
                        chapterIndex = next
                    }
                }
            }
    }

    // 底栏：跟随当前可见段落所在章
    val firstVisible = listState.firstVisibleItemIndex
    val firstItem = items.getOrNull(firstVisible)
    val visibleChapterIndex = firstItem?.chapterIndex ?: chapterIndex
    val chapterItems = items.filter { it.chapterIndex == visibleChapterIndex }
    val chapterStartIndex = items.indexOfFirst { it.chapterIndex == visibleChapterIndex }
    val withinChapter = (firstVisible - chapterStartIndex).coerceAtLeast(0)
    val percent = if (chapterItems.isEmpty()) 100
    else ((withinChapter + 1) * 100 / chapterItems.size).coerceIn(0, 100)

    var nowText by remember { mutableStateOf(formatClock(LocalTime.now())) }
    LaunchedEffect(Unit) {
        while (true) {
            nowText = formatClock(LocalTime.now())
            delay(30_000)
        }
    }

    fun startGenerate(target: MenuTarget) {
        loadingText = "AI 构思场景…"
        scope.launch {
            try {
                val scene = RequestQueue.submit("ai-analyze") {
                    AiSceneService.analyze(context, target.text)
                }
                loadingText = "AI 绘制画面…"
                val path = RequestQueue.submit("ai-image") {
                    AiSceneService.generateImage(context, scene.prompt, target.paragraphIndex.toLong())
                }
                loadingText = null
                onOpenVn(
                    com.lutqiu.lumiread.data.VnLaunch(
                        book = book,
                        chapterIndex = target.chapterIndex,
                        paragraphIndex = target.paragraphIndex,
                        sceneDesc = scene.description,
                        imagePath = path,
                    ),
                )
            } catch (t: Throwable) {
                loadingText = null
                Toast.makeText(context, friendlyError(t), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun jumpTo(index: Int) {
        val target = index.coerceIn(0, lastIndex)
        chapterIndex = target
        loadedChapters = listOf(target)
        jumpToken += 1
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val isCenterClick = offset.x > width * 0.2f && offset.x < width * 0.8f &&
                            offset.y > height * 0.2f && offset.y < height * 0.8f
                        if (isCenterClick) {
                            consoleVisible = true
                        } else {
                            chromeVisible = !chromeVisible
                        }
                    },
                    onLongPress = {
                        val item = items.getOrNull(firstVisible)
                        if (item != null) {
                            menuTarget = MenuTarget(item.chapterIndex, item.paragraphIndex, item.text)
                        }
                    },
                )
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = settings.marginDp.dp,
                end = settings.marginDp.dp,
                top = if (chromeVisible) 60.dp else 20.dp,
                bottom = if (chromeVisible) 56.dp else 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacing.dp),
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> "${item.chapterIndex}-${item.paragraphIndex}" },
            ) { index, item ->
                // 章首插入标题（该章第一条段落之前）
                val isChapterStart = index == 0 || items[index - 1].chapterIndex != item.chapterIndex
                Column {
                    if (isChapterStart) {
                        val title = book.chapters.getOrNull(item.chapterIndex)?.title.orEmpty()
                        if (title.isNotBlank()) {
                            Text(
                                text = title,
                                modifier = Modifier.padding(bottom = 6.dp),
                                style = TextStyle(
                                    color = ink,
                                    fontSize = (settings.fontSize + 6).sp,
                                    lineHeight = (settings.fontSize + 16).sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.3.sp,
                                ),
                            )
                        }
                    }
                    Text(
                        text = item.text,
                        style = TextStyle(
                            color = ink,
                            fontSize = settings.fontSize.sp,
                            lineHeight = (settings.fontSize * settings.lineHeight).sp,
                            letterSpacing = settings.letterSpacing.sp,
                            textIndent = if (settings.indentEnabled) TextIndent(firstLine = 2.em) else TextIndent.None,
                            textAlign = TextAlign.Justify,
                            fontWeight = when (settings.fontWeight) {
                                0 -> FontWeight.Light
                                2 -> FontWeight.Bold
                                else -> FontWeight.Normal
                            },
                        ),
                    )
                }
            }
        }

        // 顶部：只保留返回
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            ReaderTopBar(background = bg, ink = ink, onBack = onBack)
        }

        // 底部：时间 + 第N章 xx%
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            ReaderBottomBar(
                background = bg,
                ink = ink,
                time = nowText,
                chapterLabel = "第${visibleChapterIndex + 1}章",
                percent = percent,
            )
        }

        // Loading 遮罩
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

    // 长按菜单（纯白 Dialog）
    menuTarget?.let { target ->
        ReaderTextMenu(
            onDismiss = { menuTarget = null },
            onGenerate = {
                menuTarget = null
                startGenerate(target)
            },
            onCopy = {
                clipboard?.setPrimaryClip(
                    android.content.ClipData.newPlainText("lumiread", target.text),
                )
                menuTarget = null
                Toast.makeText(context, "已复制本段", Toast.LENGTH_SHORT).show()
            },
        )
    }

    // 沉浸式控制台
    if (consoleVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { consoleVisible = false },
            sheetState = sheetState,
            containerColor = ConsoleBg,
            scrimColor = Color.Black.copy(alpha = 0.55f),
        ) {
            ReaderConsole(
                chapters = book.chapters,
                currentChapter = visibleChapterIndex,
                settings = settings,
                onSettingsChange = { updateSettings(it) },
                onJumpChapter = {
                    jumpTo(it)
                    consoleVisible = false
                },
            )
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

/** 段落切分：优先按 `\n\n`，没有双换行时按单换行兜底。 */
internal fun paragraphsOf(content: String): List<String> {
    if (content.isBlank()) return emptyList()
    val split = if (content.contains("\n\n")) {
        content.split(Regex("\\n{2,}"))
    } else {
        content.split('\n')
    }
    return split.map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }
}

// ── 顶栏 / 底栏 ─────────────────────────────────────────────────────────────

@Composable
private fun ReaderTopBar(background: Color, ink: Color, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
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
                Text("‹", style = TextStyle(fontSize = 22.sp, color = ink, fontWeight = FontWeight.Light))
                Spacer(Modifier.width(4.dp))
                Text("返回", style = TextStyle(fontSize = 14.sp, color = ink))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerGray))
    }
}

@Composable
private fun ReaderBottomBar(
    background: Color,
    ink: Color,
    time: String,
    chapterLabel: String,
    percent: Int,
) {
    Column(Modifier.fillMaxWidth().background(background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerGray))
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(time, style = TextStyle(fontSize = 11.sp, color = ink.copy(alpha = 0.55f)))
            Text("$chapterLabel $percent%", style = TextStyle(fontSize = 11.sp, color = ink.copy(alpha = 0.55f)))
        }
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


private data class MenuTarget(val chapterIndex: Int, val paragraphIndex: Int, val text: String)

@Composable
private fun ReaderTextMenu(
    onDismiss: () -> Unit,
    onGenerate: () -> Unit,
    onCopy: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = "生成视觉小说场景",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onGenerate)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                style = TextStyle(fontSize = 15.sp, color = Color(0xFF1F2024)),
            )
            Text(
                text = "复制文本",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCopy)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                style = TextStyle(fontSize = 15.sp, color = Color(0xFF1F2024)),
            )
        }
    }
}
