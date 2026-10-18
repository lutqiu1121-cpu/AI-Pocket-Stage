package com.lutqiu.lumiread.ui.stage

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.lutqiu.lumiread.data.VnLaunch
import com.lutqiu.lumiread.ui.reader.ReaderConsoleOverlay
import com.lutqiu.lumiread.ui.reader.VnSceneController
import com.lutqiu.lumiread.ui.reader.paragraphsOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * VN 剧场模式（v0.4.0 白底三段式）。
 *
 * 视觉：
 * - 整页浅米色底（绝无纯黑背景）；
 * - 上方约 55% 高度：1:1 场景卡（圆角 + 轻微阴影），未加载时深色占位 + Loading 圈，加载完 Crossfade 淡入；
 * - 中间约 27% 高度：放大的纯白对话框（打字机）；
 * - 最底部：返回上一段 / 菜单（菜单原地呼出纯白控制台，不再退回书架）。
 *
 * 交互：
 * - 点画面或对话框：打字中立即补全，否则进入下一段；
 * - 停留在第 N 段时静默预加载 N+1 / N+2（[VnSceneController] 内 Set 去重、命中缓存直接复用）。
 */
@Composable
fun StageScreen(
    vn: VnLaunch,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current

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

    val pageBg = Color(0xFFF7F8FA)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg),
    ) {
        val screenH = maxHeight
        val imageHeight = screenH * 0.55f
        val dialogHeight = screenH * 0.27f

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ① 上方 55%：1:1 场景卡（上移，圆角 + 轻微阴影）
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
                        .background(Color(0xFF24262B)),
                ) {
                    SceneImage(
                        context = context,
                        imagePath = imagePath,
                        generating = generating,
                    )
                }
            }

            // ② 中间 27%：放大的纯白对话框
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dialogHeight)
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp)
                    .pointerInput(Unit) { detectTapGestures { revealOrNext() } },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(8.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFEDE8DE), RoundedCornerShape(18.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(dialogScroll),
                    ) {
                        Text(
                            text = text.take(shown),
                            style = TextStyle(
                                color = Color(0xFF2A2A2A),
                                fontSize = 16.sp,
                                lineHeight = 27.sp,
                            ),
                        )
                    }
                    Text(
                        text = if (typing) "点击继续显示" else "点击继续",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(top = 4.dp),
                        style = TextStyle(fontSize = 10.sp, color = Color(0xFFB0AAA0)),
                    )
                }
            }

            // ③ 最底部：返回上一段 / 菜单
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                StageAction(text = "‹ 返回上一段", onClick = ::goPrev)
                StageAction(text = "菜单", onClick = { consoleVisible = true })
            }
        }

        // 菜单 → 原地呼出同一个纯白控制台
        ReaderConsoleOverlay(
            visible = consoleVisible,
            chapters = vn.book.chapters,
            currentChapter = chapterIndex,
            settings = settings,
            onSettingsChange = { updateSettings(it) },
            onJumpChapter = {
                chapterIndex = it.coerceIn(0, vn.book.chapters.lastIndex)
                paraIndex = 0
                consoleVisible = false
            },
            onDismiss = { consoleVisible = false },
        )
    }
}

/** 场景图：深色占位 + Loading 圈；图片就绪后 Crossfade 淡入。 */
@Composable
private fun BoxScope.SceneImage(
    context: Context,
    imagePath: String?,
    generating: Boolean,
) {
    if (imagePath == null) {
        ScenePlaceholder(hint = if (generating) "画面生成中…" else "等待生成画面")
        if (generating) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF3B82F6),
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
                ScenePlaceholder(hint = "画面加载中…")
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF3B82F6),
                )
            }
        },
        error = { _ ->
            Box(Modifier.fillMaxSize()) {
                ScenePlaceholder(hint = "画面加载失败")
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
            color = Color(0xFF3B82F6),
        )
    }
}

@Composable
private fun ScenePlaceholder(hint: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2A2D34), Color(0xFF1B1D21)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = hint,
            style = TextStyle(fontSize = 12.sp, color = Color(0xFF8A8F98)),
        )
    }
}

@Composable
private fun StageAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.9f))
            .border(1.dp, Color(0xFFE7E1D6), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        style = TextStyle(fontSize = 13.sp, color = Color(0xFF2A2A2A), fontWeight = FontWeight.Medium),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
