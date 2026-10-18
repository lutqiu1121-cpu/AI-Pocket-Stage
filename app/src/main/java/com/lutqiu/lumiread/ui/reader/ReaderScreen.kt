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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
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
import com.lutqiu.lumiread.data.ReaderPagingMode
import com.lutqiu.lumiread.data.ReaderPrefs
import com.lutqiu.lumiread.data.ReaderSettings
import com.lutqiu.lumiread.data.VnLaunch
import com.lutqiu.lumiread.services.AiSceneService
import com.lutqiu.lumiread.services.HttpStatusException
import com.lutqiu.lumiread.services.RequestQueue
import com.lutqiu.lumiread.ui.components.LiquidGlassPanel
import com.lutqiu.lumiread.ui.theme.LocalCornerRadius
import com.lutqiu.lumiread.ui.theme.LumiColors
import com.lutqiu.lumiread.ui.theme.lumiShape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────────────────────────────────────
// 阅读器 v0.4.2（双阅读模式 + 6 维主题 + 液态玻璃）
//
// 滚动模式：LazyColumn 竖向滑动，随滑动实时更新进度
// 无动画模式：按页切分文字，点左 40% 上一页 / 右 40% 下一页，瞬间切换
// 两种模式：点中间 20% 区域呼出控制台；长按段落淡蓝高亮 + 气泡菜单
// 滚动状态与翻页状态都提升到 ReaderScrollViewModel，VN 往返精准回位
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

/** 无动画模式的一页文字切片。 */
internal data class PagedChunk(
    val paragraphIndex: Int,
    val text: String,
    val firstChunk: Boolean,
)

internal data class PagedPage(val chunks: List<PagedChunk>)

/** 无动画模式的页面缓存（供点击区域读取当前页数）。 */
private class PagedViewState {
    var pages by mutableStateOf<List<PagedPage>>(emptyList())
}

@Composable
fun ReaderScreen(
    book: Book,
    scroll: ReaderScrollViewModel,
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

    var settings by remember { mutableStateOf(ReaderPrefs.load(context)) }
    fun updateSettings(next: ReaderSettings) {
        settings = next
        ReaderPrefs.save(context, next)
    }

    val paged = settings.pagingMode == ReaderPagingMode.PAGED
    val pagedState = remember { PagedViewState() }
    var pendingLastPage by remember { mutableStateOf(false) }

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

    // 显式跳章：回到列表顶部（滚动模式）
    LaunchedEffect(scroll.jumpToken) {
        if (scroll.jumpToken > 0) listState.scrollToItem(0)
    }

    // 无感知续章：倒数第 2 段可见时追加下一章（仅滚动模式）
    LaunchedEffect(listState, items.size, loadedChapters, paged) {
        if (paged) return@LaunchedEffect
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

    // 底栏：跟随当前可见段落所在章（滚动模式）或当前页（无动画模式）
    val firstVisible = listState.firstVisibleItemIndex
    val firstItem = items.getOrNull(firstVisible)
    val visibleChapterIndex = firstItem?.chapterIndex ?: scroll.chapterIndex
    val chapterItems = items.filter { it.chapterIndex == visibleChapterIndex }
    val chapterStartIndex = items.indexOfFirst { it.chapterIndex == visibleChapterIndex }
    val withinChapter = (firstVisible - chapterStartIndex).coerceAtLeast(0)
    val scrollPercent = if (chapterItems.isEmpty()) 100
    else ((withinChapter + 1) * 100 / chapterItems.size).coerceIn(0, 100)

    // 阅读模式切换：两套状态各自复位，绝不互相覆盖
    var lastPaged by remember { mutableStateOf(paged) }
    LaunchedEffect(paged) {
        if (paged) {
            if (!lastPaged) {
                // 滚动 → 无动画：以当前可见章为基准，从第 0 页开始
                val target = firstItem?.chapterIndex ?: scroll.chapterIndex
                scroll.chapterIndex = target
                scroll.pagedPage = 0
            }
        } else if (lastPaged) {
            // 无动画 → 滚动：强制重置列表到当前章顶部，避免旧 ScrollState 卡死
            scroll.jumpTo(scroll.chapterIndex)
        }
        lastPaged = paged
    }

    // 页码越界保护 / 向前翻页需要跳到上一章最后一页
    LaunchedEffect(pagedState.pages, scroll.chapterIndex, paged) {
        if (!paged) return@LaunchedEffect
        val lastPage = (pagedState.pages.size - 1).coerceAtLeast(0)
        if (pendingLastPage) {
            scroll.pagedPage = lastPage
            pendingLastPage = false
        } else if (scroll.pagedPage > lastPage) {
            scroll.pagedPage = lastPage
        }
    }

    val shownPercent = if (paged) {
        val total = pagedState.pages.size
        if (total <= 1) 100 else ((scroll.pagedPage + 1) * 100 / total).coerceIn(0, 100)
    } else {
        scrollPercent
    }
    val shownChapter = if (paged) scroll.chapterIndex else visibleChapterIndex

    var nowText by remember { mutableStateOf(formatClock(LocalTime.now())) }
    LaunchedEffect(Unit) {
        while (true) {
            nowText = formatClock(LocalTime.now())
            delay(30_000)
        }
    }
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2200)
            toastMessage = null
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

    val currentItems by rememberUpdatedState(items)
    val pagedNow by rememberUpdatedState(paged)
    val consoleNow = consoleVisible

    BackHandler(enabled = consoleVisible) { consoleVisible = false }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width.toFloat()
                        val x = offset.x / width
                        when {
                            // 中间 20%（40%~60%）→ 控制台，两种模式一致
                            x in 0.4f..0.6f -> consoleVisible = true
                            // 无动画模式：左侧 40% 上一页，右侧 40% 下一页
                            pagedNow && x < 0.4f -> {
                                if (scroll.pagedPage > 0) {
                                    scroll.pagedPage -= 1
                                } else if (scroll.chapterIndex > 0) {
                                    scroll.jumpTo(scroll.chapterIndex - 1)
                                    pendingLastPage = true
                                }
                            }
                            pagedNow && x > 0.6f -> {
                                val lastPage = pagedState.pages.lastIndex
                                if (scroll.pagedPage < lastPage) {
                                    scroll.pagedPage += 1
                                } else if (scroll.chapterIndex < book.chapters.lastIndex) {
                                    scroll.jumpTo(scroll.chapterIndex + 1)
                                }
                            }
                            !pagedNow -> chromeVisible = !chromeVisible
                        }
                    },
                    onLongPress = { offset ->
                        val hit = itemBounds.entries
                            .firstOrNull { (_, rect) -> rect.contains(offset) }
                        if (hit != null) {
                            val target = resolveMenuTarget(hit.key, book, currentItems)
                            if (target != null) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                scroll.rememberAnchor(target.chapterIndex, target.paragraphIndex)
                                menuTarget = target.copy(press = offset)
                            }
                        }
                    },
                )
            },
    ) {
        if (paged) {
            // 无动画模式：单页文字，不滚动，无任何翻页动画
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = settings.marginDp.dp,
                        end = settings.marginDp.dp,
                        top = if (chromeVisible) 72.dp else 28.dp,
                        bottom = if (chromeVisible) 64.dp else 28.dp,
                    )
                    .hazeSource(hazeState),
            ) {
                val chapter = book.chapters.getOrNull(scroll.chapterIndex)
                if (chapter != null) {
                    PagedContent(
                        chapterIndex = scroll.chapterIndex,
                        chapterContent = chapter.content,
                        settings = settings,
                        pageIndex = scroll.pagedPage,
                        selectedParagraph = menuTarget
                            ?.takeIf { it.chapterIndex == scroll.chapterIndex }
                            ?.paragraphIndex,
                        itemBounds = itemBounds,
                        onPages = { pagedState.pages = it },
                        widthPx = constraints.maxWidth,
                        heightPx = constraints.maxHeight,
                    )
                }
            }
        } else {
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
                    key = { _, item -> scrollKey(item.chapterIndex, item.paragraphIndex) },
                ) { index, item ->
                    val isChapterStart = index == 0 || items[index - 1].chapterIndex != item.chapterIndex
                    val selected = menuTarget?.let {
                        it.chapterIndex == item.chapterIndex && it.paragraphIndex == item.paragraphIndex
                    } == true
                    val key = scrollKey(item.chapterIndex, item.paragraphIndex)
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
                        Text(
                            text = highlighted(item.text, selected),
                            style = paragraphStyle(settings, ink),
                        )
                    }
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
                chapterLabel = "第${shownChapter + 1}章",
                percent = shownPercent,
            )
        }

        // 主题化轻提示（替代系统 Toast，跟随阅读器配色）
        toastMessage?.let { message ->
            LiquidGlassPanel(
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp),
                shape = lumiShape(),
                strong = true,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = TextStyle(fontSize = 12.sp, color = scheme.onSurface),
                )
            }
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
                    shape = lumiShape(),
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

        // 全屏悬浮液态玻璃控制台
        ReaderConsoleOverlay(
            visible = consoleNow,
            hazeState = hazeState,
            chapters = book.chapters,
            currentChapter = shownChapter,
            settings = settings,
            onSettingsChange = { updateSettings(it) },
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

// ── 无动画分页 ──────────────────────────────────────────────────────────────

@Composable
private fun PagedContent(
    chapterIndex: Int,
    chapterContent: String,
    settings: ReaderSettings,
    pageIndex: Int,
    selectedParagraph: Int?,
    itemBounds: MutableMap<String, Rect>,
    onPages: (List<PagedPage>) -> Unit,
    widthPx: Int,
    heightPx: Int,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val baseStyle = paragraphStyle(settings, ink)
    val indentStyle = baseStyle.copy(
        textIndent = if (settings.indentEnabled) TextIndent(firstLine = 2.em) else TextIndent.None,
    )
    val spacingPx = with(density) { settings.paragraphSpacing.dp.toPx() }

    val paragraphs = remember(chapterContent) { paragraphsOf(chapterContent) }

    val pages = remember(chapterContent, baseStyle, indentStyle, widthPx, heightPx, spacingPx) {
        paginateParagraphs(
            paragraphs = paragraphs,
            pageHeight = heightPx.toFloat(),
            spacing = spacingPx,
            measure = { text, firstChunk ->
                val style = if (firstChunk) indentStyle else baseStyle
                measurer.measure(
                    text = AnnotatedString(text),
                    style = style,
                    constraints = Constraints(maxWidth = widthPx),
                ).size.height.toFloat()
            },
            split = { text ->
                val layout = measurer.measure(
                    text = AnnotatedString(text),
                    style = indentStyle,
                    constraints = Constraints(maxWidth = widthPx),
                )
                if (layout.size.height <= heightPx) listOf(text)
                else splitTextByLines(layout, text, heightPx.toFloat())
            },
        )
    }

    // 把页面信息同步给点击区域（SideEffect 在绘制前生效）
    SideEffect { onPages(pages) }

    val page = pages.getOrNull(pageIndex) ?: pages.firstOrNull() ?: PagedPage(emptyList())

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacing.dp),
    ) {
        page.chunks.forEachIndexed { chunkIndex, chunk ->
            val key = pagedKey(chapterIndex, chunk.paragraphIndex, chunkIndex)
            val selected = selectedParagraph == chunk.paragraphIndex
            val style = if (chunk.firstChunk) indentStyle else baseStyle
            Text(
                text = highlighted(chunk.text, selected),
                style = style,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { itemBounds[key] = it.boundsInRoot() },
            )
            DisposableEffect(key) {
                onDispose { itemBounds.remove(key) }
            }
        }
    }
}

/**
 * 分页纯函数（可单测）：
 * 段落按高度逐块装页；超高段落由 [split] 先按行切成多块，保证任何一页都不溢出。
 */
internal fun paginateParagraphs(
    paragraphs: List<String>,
    pageHeight: Float,
    spacing: Float,
    measure: (text: String, firstChunk: Boolean) -> Float,
    split: (text: String) -> List<String>,
): List<PagedPage> {
    if (paragraphs.isEmpty()) return listOf(PagedPage(emptyList()))

    val pages = mutableListOf<PagedPage>()
    var current = mutableListOf<PagedChunk>()
    var used = 0f

    paragraphs.forEachIndexed { paragraphIndex, paragraph ->
        val chunks = split(paragraph).ifEmpty { listOf("") }
        chunks.forEachIndexed { chunkIndex, chunk ->
            val height = measure(chunk, chunkIndex == 0)
            val extra = if (current.isEmpty()) 0f else spacing
            if (current.isNotEmpty() && used + extra + height > pageHeight) {
                pages += PagedPage(current)
                current = mutableListOf()
                used = 0f
            }
            current += PagedChunk(
                paragraphIndex = paragraphIndex,
                text = chunk,
                firstChunk = chunkIndex == 0,
            )
            used += if (current.size == 1) height else height + spacing
        }
    }

    if (current.isNotEmpty()) pages += PagedPage(current)
    return pages.ifEmpty { listOf(PagedPage(emptyList())) }
}

/** 按行把超高文本切成一页能容纳的块。 */
private fun splitTextByLines(layout: TextLayoutResult, text: String, maxHeight: Float): List<String> {
    val out = mutableListOf<String>()
    var startLine = 0
    while (startLine < layout.lineCount) {
        var endLine = startLine
        val top = layout.getLineTop(startLine)
        while (endLine < layout.lineCount && layout.getLineBottom(endLine) - top <= maxHeight) {
            endLine++
        }
        if (endLine == startLine) endLine = startLine + 1
        val startChar = layout.getLineStart(startLine)
        val endChar = layout.getLineEnd(endLine - 1, visibleEnd = false)
        if (endChar > startChar) {
            out += text.substring(startChar, endChar).trimEnd()
        }
        startLine = endLine
    }
    return out.ifEmpty { listOf(text) }
}

// ── 样式 / 工具 ─────────────────────────────────────────────────────────────

private fun paragraphStyle(settings: ReaderSettings, ink: androidx.compose.ui.graphics.Color): TextStyle =
    TextStyle(
        color = ink,
        fontSize = settings.fontSize.sp,
        lineHeight = (settings.fontSize * settings.lineHeight).sp,
        letterSpacing = settings.letterSpacing.sp,
        textAlign = TextAlign.Justify,
        fontWeight = when (settings.fontWeight) {
            0 -> FontWeight.Light
            2 -> FontWeight.Bold
            else -> FontWeight.Normal
        },
    )

@Composable
private fun highlighted(text: String, selected: Boolean): AnnotatedString {
    // 高亮色跟随「阅读器重点色」，透明度固定 20%
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    return if (selected) {
        buildAnnotatedString {
            withStyle(SpanStyle(background = highlight)) { append(text) }
        }
    } else {
        AnnotatedString(text)
    }
}

private fun scrollKey(chapter: Int, paragraph: Int) = "$chapter-$paragraph"

private fun pagedKey(chapter: Int, paragraph: Int, chunk: Int) = "p-$chapter-$paragraph-$chunk"

private fun resolveMenuTarget(
    key: String,
    book: Book,
    items: List<ReaderItem>,
): MenuTarget? {
    val parts = key.split("-")
    return if (parts.firstOrNull() == "p") {
        val chapter = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val paragraph = parts.getOrNull(2)?.toIntOrNull() ?: return null
        val text = book.chapters.getOrNull(chapter)
            ?.let { paragraphsOf(it.content).getOrNull(paragraph) }
            ?: return null
        MenuTarget(chapter, paragraph, text, Offset.Zero)
    } else {
        val chapter = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val paragraph = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val item = items.firstOrNull { it.chapterIndex == chapter && it.paragraphIndex == paragraph }
            ?: return null
        MenuTarget(chapter, paragraph, item.text, Offset.Zero)
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
private fun ReaderTopBar(hazeState: HazeState, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val radius = LocalCornerRadius.current
    LiquidGlassPanel(
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = radius, bottomEnd = radius),
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
                    .clip(lumiShape())
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
    val radius = LocalCornerRadius.current
    LiquidGlassPanel(
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = radius, topEnd = radius),
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
            shape = lumiShape(),
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
            .clip(lumiShape())
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        style = TextStyle(fontSize = 13.sp, color = scheme.onSurface, fontWeight = FontWeight.Medium),
    )
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
