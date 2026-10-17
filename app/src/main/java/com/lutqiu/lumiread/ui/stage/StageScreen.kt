@file:OptIn(ExperimentalHazeApi::class)

package com.lutqiu.lumiread.ui.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

// ─────────────────────────────────────────────────────────────────────────────
// 第一期 · 16:9 纯原生静态舞台（只画皮）
//
// 全部布局写在本文件同一个 Compose 页面里：
//   外层浅灰画布 → 正中央 16:9 舞台 → 背景占位 + 立绘占位 + 底部对话框(Haze 毛玻璃)
//   + 左右悬浮胶囊按钮 + 左上顶部信息栏
//
// 约束：无网络权限、无 ViewModel、无 API、无旧项目代码。纯静态颜色与文字。
// ─────────────────────────────────────────────────────────────────────────────

/** 画布底色：浅灰。 */
private val CanvasGray = Color(0xFFF7F8FA)

/** 舞台底色：纯黑。 */
private val StageBlack = Color(0xFF000000)

/** 背景占位（深灰渐变，给 Haze 一点可被糊掉的层次）。 */
private val BackdropTop = Color(0xFF34363C)
private val BackdropBottom = Color(0xFF1B1C1F)

/** 立绘占位：半透明蓝。 */
private val PortraitBlue = Color(0xFF3B82F6).copy(alpha = 0.45f)

/** 对话框假文字（写死，纯静态）。 */
private const val FakeNarration =
    "第一套要跟三个男生合租，我本来以为自己已经做好了心理准备。" +
        "可推开门的那一刻，客厅里三道视线同时抬起来，空气还是安静了一拍。" +
        "一个在系围裙，一个抱着吉他，还有一个把书扣在膝盖上，冲我笑了一下。"

@Composable
fun StageScreen(modifier: Modifier = Modifier) {
    // Haze 取景状态：背景占位是取景源，对话框从这里取模糊。
    val hazeState = rememberHazeState()

    MaterialTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(CanvasGray),
            contentAlignment = Alignment.Center,
        ) {
            Stage16x9(hazeState = hazeState)
        }
    }
}

/** 16:9 舞台本体。 */
@Composable
private fun Stage16x9(hazeState: HazeState) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .aspectRatio(16f / 9f)
            .shadow(elevation = 24.dp, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(StageBlack),
    ) {
        // ① 背景占位：舞台内部最底层，铺满。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(Brush.verticalGradient(listOf(BackdropTop, BackdropBottom))),
        )

        // ② 立绘占位：左侧，高 80%，底部对齐，半透明蓝。
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxHeight(0.8f)
                .fillMaxWidth(0.32f)
                .background(PortraitBlue),
        )

        // ③ 顶部信息栏：左上角胶囊。
        InfoChip(
            text = "第1章 正文 · 场景地点",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        )

        // ④ 悬浮按钮：左侧中间 / 右侧中间，紧贴舞台边缘 16dp。
        FloatingColumn(
            labels = listOf("上一句", "菜单"),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp),
        )
        FloatingColumn(
            labels = listOf("下一句", "设置"),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
        )

        // ⑤ 对话框：底部，高 150dp，铺满宽度，Haze 毛玻璃。
        NarrationDialog(
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp),
        )
    }
}

/** 底部对话框：Haze 毛玻璃 + 假文字（行高 1.6，首行缩进 2 字符）。 */
@Composable
private fun NarrationDialog(
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
            .padding(horizontal = 28.dp, vertical = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = FakeNarration,
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

/** 舞台侧边悬浮胶囊按钮列。 */
@Composable
private fun FloatingColumn(labels: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        labels.forEach { label ->
            CapsuleButton(label)
        }
    }
}

@Composable
private fun CapsuleButton(text: String) {
    Row(
        modifier = Modifier
            .widthIn(min = 72.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = Color(0xFFF3F5F9),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Preview(name = "16:9 Stage", showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun StageScreenPreview() {
    StageScreen()
}
