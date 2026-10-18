package com.lutqiu.lumiread.ui.stage

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.lutqiu.lumiread.data.ReaderPrefs
import com.lutqiu.lumiread.data.ReaderSettings
import com.lutqiu.lumiread.data.ReaderTheme
import com.lutqiu.lumiread.data.VnLaunch
import com.lutqiu.lumiread.ui.components.LiquidGlassPanel
import com.lutqiu.lumiread.ui.reader.ReaderConsoleOverlay
import com.lutqiu.lumiread.ui.reader.VnSceneController
import com.lutqiu.lumiread.ui.reader.paragraphsOf
import com.lutqiu.lumiread.ui.theme.LumiColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * VN 剧场模式（v0.4.0 液态玻璃三段式）。
 *
 * 视觉：
 * - 根节点 Surface(color = MaterialTheme.colorScheme.background)，绝不出现纯黑底；
 * - 上方约 55%：1:1 场景卡（圆角 + 阴影 + 玻璃描边），未就绪时使用液态玻璃占位 + Loading 圈；
 * - 中间约 27%：放大的液态玻璃对话框（打字机）；
 * - 最底部：返回上一段 / 菜单（菜单原地呼出同一个液态玻璃控制台）。
 *
 * 交互：
 * - 点画面或对话框：打字中立即补全，否则进入下一段；
 * - 停留在第 N 段时静默预加载 N+1 / N+2（[VnSceneController] 内 Set 去重、命中缓存直接复用）。
 */
@Composable
fun StageScreen(
    vn: VnLaunch,
    readerTheme: ReaderTheme,
    onReaderThemeChange: (ReaderTheme) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scheme = MaterialTheme.colorScheme
    val hazeState: HazeState = rememberHazeState()

    // 沉浸式：状态栏 / 导航栏隐藏，离开时恢复
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    var chapterIndex by remember { mutableIntStateOf(vn.chapterIndex) }
    var paraIndex by remember { mutableIntStateOf(vn.paragraphIndex) }
    var consoleVisible by remember { mutableStateOf(false) }

    var settings by remember { mutableStateOf(ReaderPrefs.load(context)) }
    fun updateSettings(next: ReaderSettings) {
        settings = next
        ReaderPrefs.save(context, next)
    }

    val chapter = vn.book.chapters.getOrNull(chapterIndex)
    val controller = remember(chapterIndex, chapter?.content) {
        chapter?.let { VnSceneController(context, it) }
    }

    // 基准锁定：进入 VN 的第一段 = 长按时选中的第 N 段，图也钉在这一段
    LaunchedEffect(controller) {
        if (chapterIndex == vn.chapterIndex) {
            controller?.seed(vn.paragraphIndex, vn.imagePath)
        }
    }

    // 预加载：当前段（书架 VN 模式直接进入时无图）+ N+1 / N+2，全部静默
    LaunchedEffect(controller, paraIndex, chapterIndex) {
        val c = controller ?: return@LaunchedEffect
        launch { c.preload(paraIndex) }
        launch { c.preload(paraIndex + 1) }
        launch { c.preload(paraIndex + 2) }
    }

    val text = controller?.paragraph(paraIndex).orEmpty()
    val imagePath = controller?.image(paraIndex)
    val generating = controller?.isLoading(paraIndex) == true

    // 打字机：每段从头开始逐字显示
    var shown by remember(paraIndex, chapterIndex) { mutableIntStateOf(0) }
    LaunchedEffect(paraIndex, chapterIndex, text) {
        shown = 0
        var i = 0
        while (i < text.length) {
            delay(16)
            i++
            shown = i
        }
    }
    val typing = shown < text.length
    val dialogScroll = remember(paraIndex, chapterIndex) { ScrollState(0) }

    fun goNext() {
        if (paraIndex + 1 < (controller?.size ?: 0)) {
            paraIndex += 1
        } else if (chapterIndex < vn.book.chapters.lastIndex) {
            chapterIndex += 1
            paraIndex = 0
        }
    }

    fun goPrev() {
        if (paraIndex > 0) {
            paraIndex -= 1
        } else if (chapterIndex > 0) {
            chapterIndex -= 1
            paraIndex = (vn.book.chapters.getOrNull(chapterIndex)
                ?.let { paragraphsOf(it.content).size } ?: 1) - 1
        }
    }

    fun revealOrNext() {
        if (typing) shown = text.length else goNext()
    }

    BackHandler(enabled = consoleVisible) { consoleVisible = false }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = scheme.background,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val imageHeight = maxHeight * 0.55f
            val dialogHeight = maxHeight * 0.27f

            // 背景层：主题渐变（Haze 采样源）。禁止任何纯黑底色。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                scheme.background,
                                scheme.surfaceVariant.copy(alpha = 0.55f),
                                scheme.background,
                            ),
                        ),
                    ),
            )

            // 内容层：图片 / 玻璃对话框 / 底部操作
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                // ① 上方 55%：1:1 场景卡（上移，圆角 + 阴影 + 玻璃描边）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(imageHeight)
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 6.dp)
                        .pointerInput(Unit) { detectTapGestures { revealOrNext() } },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(10.dp, RoundedCornerShape(22.dp))
                            .clip(RoundedCornerShape(22.dp))
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    listOf(
                                        scheme.onSurface.copy(alpha = 0.14f),
                                        LumiColors.Transparent,
                                        scheme.onSurface.copy(alpha = 0.14f),
                                    ),
                                ),
                                shape = RoundedCornerShape(22.dp),
                            ),
                    ) {
                        SceneImage(
                            context = context,
                            hazeState = hazeState,
                            imagePath = imagePath,
                            generating = generating,
                        )
                    }
                }

                // ② 中间 27%：放大的液态玻璃对话框
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dialogHeight)
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp)
                        .pointerInput(Unit) { detectTapGestures { revealOrNext() } },
                ) {
                    LiquidGlassPanel(
                        hazeState = hazeState,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(20.dp),
                        strong = true,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                                .verticalScroll(dialogScroll),
                        ) {
                            Text(
                                text = text.take(shown),
                                style = TextStyle(
                                    color = scheme.onSurface,
                                    fontSize = 16.sp,
                                    lineHeight = 27.sp,
                                ),
                            )
                        }
                        Text(
                            text = if (typing) "点击继续显示" else "点击继续",
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 10.dp),
                            style = TextStyle(fontSize = 10.sp, color = scheme.onSurfaceVariant),
                        )
                    }
                }

                // ③ 最底部：返回上一段 / 菜单（液态玻璃胶囊）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    StageAction(hazeState = hazeState, text = "‹ 返回上一段", onClick = ::goPrev)
                    StageAction(hazeState = hazeState, text = "菜单", onClick = { consoleVisible = true })
                }
            }

            // 菜单 → 原地呼出同一个液态玻璃控制台
            ReaderConsoleOverlay(
                visible = consoleVisible,
                hazeState = hazeState,
                chapters = vn.book.chapters,
                currentChapter = chapterIndex,
                settings = settings,
                readerTheme = readerTheme,
                onSettingsChange = { updateSettings(it) },
                onReaderThemeChange = onReaderThemeChange,
                onJumpChapter = {
                    chapterIndex = it.coerceIn(0, vn.book.chapters.lastIndex)
                    paraIndex = 0
                    consoleVisible = false
                },
                onDismiss = { consoleVisible = false },
            )
        }
    }
}

/** 场景图：液态玻璃占位 + Loading 圈；图片就绪后 Crossfade 淡入。 */
@Composable
private fun BoxScope.SceneImage(
    context: Context,
    hazeState: HazeState,
    imagePath: String?,
    generating: Boolean,
) {
    if (imagePath == null) {
        GlassPlaceholder(hazeState = hazeState, hint = if (generating) "画面生成中…" else "等待生成画面")
        if (generating) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }

    val request = remember(imagePath) {
        ImageRequest.Builder(context)
            .data(File(imagePath))
            .crossfade(450)
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = "AI 场景",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        loading = { _ ->
            Box(Modifier.fillMaxSize()) {
                GlassPlaceholder(hazeState = hazeState, hint = "画面加载中…")
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        error = { _ ->
            Box(Modifier.fillMaxSize()) {
                GlassPlaceholder(hazeState = hazeState, hint = "画面加载失败")
            }
        },
        success = { SubcomposeAsyncImageContent() },
    )

    if (generating) {
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun GlassPlaceholder(hazeState: HazeState, hint: String) {
    LiquidGlassPanel(
        hazeState = hazeState,
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(22.dp),
        strong = false,
    ) {
        Text(
            text = hint,
            modifier = Modifier.align(Alignment.Center),
            style = TextStyle(
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun StageAction(
    hazeState: HazeState,
    text: String,
    onClick: () -> Unit,
) {
    LiquidGlassPanel(
        hazeState = hazeState,
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        strong = false,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            style = TextStyle(
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
