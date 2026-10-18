package com.lutqiu.lumiread.ui.stage

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lutqiu.lumiread.data.VnLaunch
import com.lutqiu.lumiread.ui.reader.VnSceneController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * VN 剧场模式（三段式）：
 * 上半 1:1 画面区 / 中间对话框（打字机）/ 底部「返回上一段 · 菜单」。
 *
 * 交互：点画面或对话框 → 打字中则立刻补全，否则进入下一段；
 * 图片由 [VnSceneController] 静默预加载，未就绪时显示深色占位 + 小 loading，好了 Crossfade 淡入。
 */
@Composable
fun StageScreen(
    vn: VnLaunch,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var chapterIndex by remember { mutableIntStateOf(vn.chapterIndex) }
    var paraIndex by remember { mutableIntStateOf(vn.paragraphIndex) }

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

    // 静默预加载 N+1 / N+2
    LaunchedEffect(controller, paraIndex, chapterIndex) {
        val c = controller ?: return@LaunchedEffect
        launch { c.preload(paraIndex + 1) }
        launch { c.preload(paraIndex + 2) }
    }

    val text = controller?.paragraph(paraIndex).orEmpty()
    val imagePath = controller?.image(paraIndex)
    val imageLoading = controller?.isLoading(paraIndex) == true

    // 打字机
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
            paraIndex = (vn.book.chapters.getOrNull(chapterIndex)?.let { paragraphsSize(it.content) } ?: 1) - 1
        }
    }

    fun revealOrNext() {
        if (typing) shown = text.length else goNext()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101114)),
    ) {
        // ① 上半：1:1 画面区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) { detectTapGestures { revealOrNext() } },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1B1C1F)),
            ) {
                Crossfade(
                    targetState = imagePath,
                    animationSpec = tween(durationMillis = 420),
                    label = "vn-scene",
                ) { path ->
                    if (path != null) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = "AI 场景",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1B1C1F)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "画面生成中…",
                                style = TextStyle(fontSize = 12.sp, color = Color(0xFF7A7F87)),
                            )
                        }
                    }
                }

                if (imageLoading) {
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
        }

        // ② 对话框
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(Unit) { detectTapGestures { revealOrNext() } }
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            Text(
                text = text.take(shown),
                style = TextStyle(
                    color = Color(0xFFF3F5F9),
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                ),
            )
        }

        // ③ 底部操作栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101114))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StageAction(text = "‹ 返回上一段", onClick = ::goPrev)
            StageAction(text = "菜单", onClick = onBack)
        }
    }
}

@Composable
private fun StageAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        style = TextStyle(fontSize = 13.sp, color = Color(0xFFD8DCE2), fontWeight = FontWeight.Medium),
    )
}

private fun paragraphsSize(content: String): Int =
    if (content.isBlank()) 0 else content.split(Regex("\\n{2,}")).count { it.isNotBlank() }
