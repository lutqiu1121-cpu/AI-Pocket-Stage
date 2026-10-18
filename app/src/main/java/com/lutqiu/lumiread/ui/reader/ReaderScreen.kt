package com.lutqiu.lumiread.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lutqiu.lumiread.data.Book
import com.lutqiu.lumiread.data.ReaderPrefs
import com.lutqiu.lumiread.data.ReaderSettings
import com.lutqiu.lumiread.data.ReaderTheme
import com.lutqiu.lumiread.data.VnLaunch
import com.lutqiu.lumiread.services.AiSceneService
import com.lutqiu.lumiread.services.HttpStatusException
import com.lutqiu.lumiread.services.RequestQueue
import com.lutqiu.lumiread.ui.components.LiquidGlassPanel
import com.lutqiu.lumiread.ui.theme.LumiColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────────────────────────────────────
// 阅读器 v0.4.0（双层主题 + 液态玻璃）
//
// · 全部颜色来自 MaterialTheme.colorScheme（阅读器独立配色），零硬编码
// · 长按正文：该段文字淡蓝高亮 + 文字正上方白色气泡菜单（不是居中 Dialog）
// · 点屏幕中心 → 全屏液态玻璃控制台（Box + 0.4 遮罩，不是 ModalBottomSheet）
// · 滚动状态提升到 ReaderScrollViewModel，VN 往返逐像素回位
// ─────────────────────────────────────────────────────────────────────────────

private const val TAG = "ReaderScroll"

/** 列表里的一段正文。 */
private data class ReaderItem(
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val text: String,
)

/** 长按命中的段落 + 手指按下的窗口坐标（气泡以此为锚点向上弹）。 */
private data class MenuTarget(
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val text: String,
    val press: Offset,
)

@Composable
fun ReaderScreen(
    book: Book,
    scroll: ReaderScrollViewModel,
    readerTheme: ReaderTheme,
    onReaderThemeChange: (ReaderTheme) -> Unit,
    onBack: () -> Unit,
    onOpenVn: (VnLaunch) -> Unit,
    modifier: Modifier = Modifier,
    initialChapterIndex: Int = 0,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val hazeState: HazeState = rememberHazeState()
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

    // 换书才重置；从 VN 回来时同一本书 → 状态原样保留
    LaunchedEffect(book.id) { scroll.openBook(book.id, startIndex) }

    val listState = scroll.listState
    val loadedChapters = scroll.loadedChapters.ifEmpty { listOf(scroll.chapterIndex) }

    var chromeVisible by remember { mutableStateOf(true) }
    var consoleVisible by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf<String?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var menuTarget by remember { mutableStateOf<MenuTarget?>(null) }
    /** 可见段落的根坐标，长按命中的就是手指下那一段。 */
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }

    val ink = scheme.onBackground

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
    LaunchedEffect(scroll.jumpToken) {
        if (scroll.jumpToken > 0) listState.scrollToItem(0)
    }

    // 无感知续章：倒数第 2 段可见时追加下一章
    LaunchedEffect(listState, items.size, loadedChapters) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                if (lastVisible >= items.size - 2) {
                    val last = loadedChapters.lastOrNull() ?: return@collect
                    if (last < book.chapters.lastIndex) {
                        scroll.appendChapter(last + 1)
                    }
                }
            }
    }

    // ── 滚动位置：记录 + 用于验收的日志 ─────────────────────────────────────
    LaunchedEffect(Unit) {
        Log.i(
            TAG,
            "[restore] book=${book.id} firstVisible=${listState.firstVisibleItemIndex} " +
                "offset=${listState.firstVisibleItemScrollOffset} loaded=$loadedChapters",
        )
    }
    LaunchedEffect(listState, items) {
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key,
            )
        }.collect { (index, offset, key) ->
            val item = items.getOrNull(index)
            if (item != null) {
                scroll.rememberAnchor(item.chapterIndex, item.paragraphIndex)
                Log.i(
                    TAG,
                    "[save] index=$index offset=$offset chapter=${item.chapterIndex} " +
                        "paragraph=${item.paragraphIndex} key=$key",
                )
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            Log.i(
                TAG,
                "[leave] firstVisible=${listState.firstVisibleItemIndex} " +
                    "offset=${listState.firstVisibleItemScrollOffset}",
            )
        }
    }

    // 底栏：跟随当前可见段落所在章
    val firstVisible = listState.firstVisibleItemIndex
    val firstItem = items.getOrNull(firstVisible)
    val visibleChapterIndex = firstItem?.chapterIndex ?: scroll.chapterIndex
    val chapterItems = items.filter { it.chapterIndex == visibleChapterIndex }
    val chapterStartIndex = items.indexOfFirst { it.chapterIndex == visibleChapterIndex }
    val withinChapter = (firstVisible - chapterStartIndex).coerceAtLeast(0)
    val percent = if (chapterItems.isEmpty()) 100
    else ((withinChapter + 1) * 100 / chapterItems.size).coerceIn(0, 100)

    var nowText by remember { mutableStateOf(formatClock(LocalTime.now())) }
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2200)
            toastMessage = null
        }
    }
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
                    VnLaunch(
                        book = book,
                        chapterIndex = target.chapterIndex,
                        paragraphIndex = target.paragraphIndex,
                        sceneDesc = scene.description,
                        imagePath = path,
                    ),
                )
            } catch (t: Throwable) {
                loadingText = null
                toastMessage = friendlyError(t)
            }
        }
    }

    fun jumpTo(index: Int) {
        scroll.jumpTo(index.coerceIn(0, lastIndex))
    }

    // pointerInput(Unit) 不会随重组重启，命中逻辑必须读最新 items
    val currentItems by rememberUpdatedState(items)

    BackHandler(enabled = consoleVisible) { consoleVisible = false }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
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
                    onLongPress = { offset ->
                        // 命中手指下那一段（只对当前组合出来的可见段落做矩形命中）
                        val hit = itemBounds.entries
                            .firstOrNull { (_, rect) -> rect.contains(offset) }
                        if (hit != null) {
                            val item = currentItems.firstOrNull {
                                boundKey(it.chapterIndex, it.paragraphIndex) == hit.key
                            }
                            if (item != null) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                scroll.rememberAnchor(item.chapterIndex, item.paragraphIndex)
                                menuTarget = MenuTarget(
                                    chapterIndex = item.chapterIndex,
                                    paragraphIndex = item.paragraphIndex,
                                    text = item.text,
                                    press = offset,
                                )
                            }
                        }
                    },
                )
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            contentPadding = PaddingValues(
                start = settings.marginDp.dp,
                end = settings.marginDp.dp,
                top = if (chromeVisible) 72.dp else 24.dp,
                bottom = if (chromeVisible) 64.dp else 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacing.dp),
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> boundKey(item.chapterIndex, item.paragraphIndex) },
            ) { index, item ->
                val isChapterStart = index == 0 || items[index - 1].chapterIndex != item.chapterIndex
                val selected = menuTarget?.let {
                    it.chapterIndex == item.chapterIndex && it.paragraphIndex == item.paragraphIndex
                } == true
                val key = boundKey(item.chapterIndex, item.paragraphIndex)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { itemBounds[key] = it.boundsInRoot() },
                ) {
                    DisposableEffect(key) {
                        onDispose { itemBounds.remove(key) }
                    }
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
                    val body: AnnotatedString = if (selected) {
                        buildAnnotatedString {
                            withStyle(SpanStyle(background = LumiColors.Highlight)) {
                                append(item.text)
                            }
                        }
                    } else {
                        AnnotatedString(item.text)
                    }
                    Text(
                        text = body,
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

        // 顶部：液态玻璃返回栏
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            ReaderTopBar(hazeState = hazeState, onBack = onBack)
        }

        // 底部：液态玻璃时间 / 进度栏
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            ReaderBottomBar(
                hazeState = hazeState,
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
                    .background(LumiColors.Scrim),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = scheme.surface,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = scheme.primary,
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = message,
                            style = TextStyle(fontSize = 14.sp, color = scheme.onSurface),
                        )
                    }
                }
            }
        }

        // 主题化轻提示（替代系统 Toast，跟随阅读器配色）
        toastMessage?.let { message ->
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LiquidGlassPanel(
                    hazeState = hazeState,
                    shape = RoundedCornerShape(50),
                    strong = true,
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        style = TextStyle(fontSize = 12.sp, color = scheme.onSurface),
                    )
                }
            }
        }

        // 全屏悬浮液态玻璃控制台（不再是 ModalBottomSheet）
        ReaderConsoleOverlay(
            visible = consoleVisible,
            hazeState = hazeState,
            chapters = book.chapters,
            currentChapter = visibleChapterIndex,
            settings = settings,
            readerTheme = readerTheme,
            onSettingsChange = { updateSettings(it) },
            onReaderThemeChange = onReaderThemeChange,
            onJumpChapter = {
                jumpTo(it)
                consoleVisible = false
            },
            onDismiss = { consoleVisible = false },
        )
    }

    // 长按气泡菜单：白色小气泡，锚在手指 / 高亮文字正上方
    menuTarget?.let { target ->
        val marginPx = with(density) { 10.dp.roundToPx() }
        Popup(
            popupPositionProvider = BubbleAboveProvider(target.press, marginPx),
            onDismissRequest = { menuTarget = null },
            properties = PopupProperties(
                focusable = true,
                dismissOnClickOutside = true,
                dismissOnBackPress = true,
            ),
        ) {
            TextBubbleMenu(
                onGenerate = {
                    menuTarget = null
                    startGenerate(target)
                },
                onCopy = {
                    clipboard?.setPrimaryClip(
                        android.content.ClipData.newPlainText("lumiread", target.text),
                    )
                    menuTarget = null
                    toastMessage = "已复制本段"
                },
            )
        }
    }
}

private fun boundKey(chapter: Int, paragraph: Int) = "$chapter-$paragraph"

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

// ── 气泡菜单 ────────────────────────────────────────────────────────────────

/** 把气泡放在手指（高亮文字）正上方；顶部空间不够时翻到下方，并夹在窗口内。 */
private class BubbleAboveProvider(
    private val point: Offset,
    private val marginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        var x = point.x.toInt() - popupContentSize.width / 2
        var y = point.y.toInt() - popupContentSize.height - marginPx
        if (y < marginPx) y = point.y.toInt() + marginPx
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val maxY = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx)
        x = x.coerceIn(marginPx, maxX)
        y = y.coerceIn(marginPx, maxY)
        return IntOffset(x, y)
    }
}

@Composable
private fun TextBubbleMenu(onGenerate: () -> Unit, onCopy: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = scheme.surface,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BubbleAction(text = "生成视觉小说场景", onClick = onGenerate)
                Box(
                    Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(scheme.outlineVariant),
                )
                BubbleAction(text = "复制文本", onClick = onCopy)
            }
        }
        // 指向高亮文字的小三角
        Canvas(modifier = Modifier.size(width = 14.dp, height = 7.dp)) {
            val p = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(p, scheme.surface)
        }
    }
}

@Composable
private fun BubbleAction(text: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        style = TextStyle(fontSize = 13.sp, color = scheme.onSurface, fontWeight = FontWeight.Medium),
    )
}

// ── 顶栏 / 底栏 ─────────────────────────────────────────────────────────────

@Composable
private fun ReaderTopBar(hazeState: HazeState, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    LiquidGlassPanel(
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        strong = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("‹", style = TextStyle(fontSize = 22.sp, color = scheme.onSurface, fontWeight = FontWeight.Light))
                Spacer(Modifier.width(4.dp))
                Text("返回", style = TextStyle(fontSize = 14.sp, color = scheme.onSurface))
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    hazeState: HazeState,
    time: String,
    chapterLabel: String,
    percent: Int,
) {
    val scheme = MaterialTheme.colorScheme
    LiquidGlassPanel(
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(time, style = TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant))
            Text(
                "$chapterLabel $percent%",
                style = TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant),
            )
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
