@file:OptIn(ExperimentalHazeApi::class)

package com.lutqiu.lumiread.ui.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import coil.compose.AsyncImage
import dev.chrisbanes.haze.rememberHazeState
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// 16:9 叙事舞台（第三期：删掉全部假数据，文本一律由调用方传入）
//
// 结构：浅灰画布 → 正中央 16:9 舞台 → 背景占位 + 立绘占位 + 底部对话框(Haze)
//      + 左右悬浮胶囊 + 顶部信息栏
// 文本为空 → 对话框就是空的，页面里不存在任何写死的旁白。
// ─────────────────────────────────────────────────────────────────────────────

private val CanvasGray = Color(0xFFF7F8FA)
private val StageBlack = Color(0xFF000000)
private val BackdropTop = Color(0xFF34363C)
private val BackdropBottom = Color(0xFF1B1C1F)

/** 立绘占位：极淡透明蓝。 */
private val PortraitBlue = Color(0xFF3B82F6).copy(alpha = 0.2f)

/**
 * @param text 当前旁白；空串 = 空对话框（不伪造任何内容）。
 * @param chapterLabel 顶部信息栏文案。
 * @param onBack 非空时右上角出现「书架」返回胶囊。
 */
@Composable
fun StageScreen(
    text: String = "",
    chapterLabel: String = "第1章 正文 · 场景地点",
    onBack: (() -> Unit)? = null,
    backgroundImagePath: String? = null,
    modifier: Modifier = Modifier,
) {
    val hazeState = rememberHazeState()

    MaterialTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(CanvasGray),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(16f / 9f)
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(StageBlack),
            ) {
                // ① 背景：AI 生图优先，否则深灰占位（Haze 取景源）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState),
                ) {
                    if (backgroundImagePath != null) {
                        AsyncImage(
                            model = File(backgroundImagePath),
                            contentDescription = "AI 生成的场景",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(BackdropTop, BackdropBottom))),
                        )
                    }
                }

                // ② 立绘占位：左侧固定 120dp 宽，高 80%，底部对齐
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxHeight(0.8f)
                        .width(120.dp)
                        .background(PortraitBlue),
                )

                // ③ 顶部信息栏
                InfoChip(
                    text = chapterLabel,
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                )

                if (onBack != null) {
                    ReturnChip(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .zIndex(10f),
                    )
                }

                // ④ 悬浮按钮（zIndex 10：永远压在对话框之上，不被 28% 对话框吃掉）
                FloatingColumn(
                    labels = listOf("上一句", "菜单"),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .zIndex(10f),
                )
                FloatingColumn(
                    labels = listOf("下一句", "设置"),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .zIndex(10f),
                )

                // ⑤ 对话框：高 28%，Haze 毛玻璃 + 0.6 黑遮罩；无文本则空着
                NarrationDialog(
                    text = text,
                    hazeState = hazeState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.28f),
                )
            }
        }
    }
}

@Composable
private fun NarrationDialog(
    text: String,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val input = remember(hazeState) { HazeInput.Sources(hazeState) }
    val style = remember {
        GlassStyle.regular.then {
            shape(RoundedCornerShape(0.dp))
            tint(Color.Black.copy(alpha = 0.42f))
        }
    }

    Box(
        modifier = modifier
            .hazeGlass(input = input, style = style)
            // 0.6 黑遮罩：压在毛玻璃之上，保证文字对比度
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isNotEmpty()) {
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                style = TextStyle(
                    color = Color(0xFFF3F5F9),
                    fontSize = 18.sp,
                    lineHeight = 28.8.sp, // 18sp × 1.6
                    textIndent = TextIndent(firstLine = 2.em),
                    letterSpacing = 0.2.sp,
                ),
            )
        }
    }
}

/** 顶部信息栏胶囊。 */
@Composable
private fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = Color(0xFFF3F5F9),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
            ),
        )
    }
}

/** 右上角返回书架胶囊。 */
@Composable
private fun ReturnChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = "‹ 书架",
            style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
    }
}

/** 舞台侧边悬浮胶囊按钮列。 */
@Composable
private fun FloatingColumn(labels: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        labels.forEach { label -> CapsuleButton(label) }
    }
}

@Composable
private fun CapsuleButton(text: String) {
    Row(
        modifier = Modifier
            .widthIn(min = 72.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Preview(name = "16:9 Stage (empty)", showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun StageScreenPreview() {
    StageScreen()
}
