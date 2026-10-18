package com.lutqiu.lumiread.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lutqiu.lumiread.ui.theme.lumiShape

/**
 * 深色 / 自定义颜色选择器：
 * - 支持直接输入 HEX（#RRGGBB）；
 * - 同时提供 H / S / V 三个滑块做可视化取色；
 * - 颜色值全程用 Compose Color，不写死任何色值。
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val startHsv = remember(initial) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hex by remember(initial) { mutableStateOf(initial.toHex()) }
    var hue by remember(initial) { mutableFloatStateOf(startHsv[0]) }
    var saturation by remember(initial) { mutableFloatStateOf(startHsv[1]) }
    var value by remember(initial) { mutableFloatStateOf(startHsv[2]) }

    fun current(): Color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

    fun syncHexFromSliders() {
        hex = current().toHex()
    }

    fun applyHex(text: String) {
        hex = text
        val parsed = parseHexColor(text) ?: return
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(parsed.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = lumiShape(),
            color = scheme.surface,
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = TextStyle(fontSize = 16.sp, color = scheme.onSurface),
                )
                Spacer(Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(lumiShape())
                        .background(current()),
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = hex,
                    onValueChange = ::applyHex,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("HEX（#RRGGBB）", fontSize = 11.sp) },
                )

                Spacer(Modifier.height(10.dp))
                HsvSlider(
                    label = "色相 H",
                    value = hue,
                    range = 0f..360f,
                    onChange = { hue = it; syncHexFromSliders() },
                )
                HsvSlider(
                    label = "饱和度 S",
                    value = saturation,
                    range = 0f..1f,
                    onChange = { saturation = it; syncHexFromSliders() },
                )
                HsvSlider(
                    label = "明度 V",
                    value = value,
                    range = 0f..1f,
                    onChange = { value = it; syncHexFromSliders() },
                )

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(onClick = { onConfirm(current()) }) { Text("确定") }
                }
            }
        }
    }
}

@Composable
private fun HsvSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant),
            modifier = Modifier.padding(top = 14.dp),
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
    }
}

private fun Color.toHex(): String = "#%06X".format(0xFFFFFF and toArgb())

private fun parseHexColor(raw: String): Color? {
    val cleaned = raw.trim().removePrefix("#")
    if (cleaned.length != 6 || cleaned.any { it !in "0123456789abcdefABCDEF" }) return null
    return runCatching { Color(android.graphics.Color.parseColor("#$cleaned")) }.getOrNull()
}
