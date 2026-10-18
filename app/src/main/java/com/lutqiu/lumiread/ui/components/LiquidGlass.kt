package com.lutqiu.lumiread.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import com.lutqiu.lumiread.ui.theme.LocalGlassEnabled
import com.lutqiu.lumiread.ui.theme.LumiColors

/**
 * 液态玻璃面板（Haze 2.0 Glass）。
 *
 * 液态感三要素：
 * 1. 背景模糊 + 折射（Haze Glass 光学层，随底层内容流动）；
 * 2. 极低透明度底色叠加（来自当前 MaterialTheme 的 surface，深浅主题自适应）；
 * 3. 四周边缘高光描边（线性渐变 border，模拟玻璃厚度与折射）。
 *
 * 6 维主题里的「玻璃质感开关」关闭时，自动退化为纯色 surface 面板，不采样背景。
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun LiquidGlassPanel(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(0.dp),
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val glassEnabled = LocalGlassEnabled.current

    val tint = when {
        !glassEnabled -> scheme.surface
        dark && strong -> LumiColors.GlassTintDarkStrong
        dark -> LumiColors.GlassTintDark
        strong -> LumiColors.GlassTintStrong
        else -> LumiColors.GlassTint
    }
    val edge = if (dark) LumiColors.GlassBorderDark else LumiColors.GlassBorder
    val edgeSoft = if (dark) LumiColors.GlassBorderDarkSoft else LumiColors.GlassBorderSoft

    val style = remember(shape, dark, strong) {
        GlassStyle.regular.then {
            shape(shape)
            backgroundColor(tint)
            tint(scheme.primary.copy(alpha = 0.06f))
            specularIntensity(if (strong) 0.62f else 0.5f)
            edgeShadow(LumiColors.GlassEdgeShadow)
            edgeSoftness(1.dp)
            alpha(if (strong) 0.96f else 0.9f)
        }
    }

    val borderBrush = Brush.linearGradient(listOf(edge, edgeSoft, edge))

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (glassEnabled) {
                    Modifier.hazeGlass(input = HazeInput.Sources(hazeState), style = style)
                } else {
                    Modifier.background(scheme.surface)
                },
            )
            .background(tint)
            .border(width = 1.dp, brush = borderBrush, shape = shape),
        content = content,
    )
}
