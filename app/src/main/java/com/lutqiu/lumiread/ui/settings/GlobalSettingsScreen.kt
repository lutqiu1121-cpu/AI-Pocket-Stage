package com.lutqiu.lumiread.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lutqiu.lumiread.services.ApiConfig
import com.lutqiu.lumiread.services.ApiConfigRepository
import com.lutqiu.lumiread.ui.components.PageHeader

/**
 * 全局设置（v0.4.0 极简版）。
 *
 * API Key 已按产品要求移入 [ApiConfig] 硬编码，这里只做只读状态展示，不再有任何输入框。
 */
@Composable
fun GlobalSettingsScreen() {
    val context = LocalContext.current
    val key = ApiConfigRepository.getZhipuKey(context)
    val configured = key.isNotBlank() && key != "请填入真实Key"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFDFDFC)),
    ) {
        PageHeader(title = "设置")

        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "AI 视觉小说引擎",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1B1E)),
                )
                Spacer(Modifier.height(10.dp))
                SettingLine(label = "模型", value = ApiConfig.ZHIPU_MODEL)
                Spacer(Modifier.height(6.dp))
                SettingLine(
                    label = "API Key",
                    value = if (configured) "已内置配置" else "未填写 · 请在 ApiConfig.kt 中填入",
                    valueColor = if (configured) Color(0xFF16A34A) else Color(0xFFB45309),
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Key 与模型地址统一在 ApiConfig.kt 中维护，设置页不再提供输入。",
                style = TextStyle(fontSize = 11.sp, color = Color(0xFF9AA0A6), lineHeight = 18.sp),
            )
        }
    }
}

@Composable
private fun SettingLine(label: String, value: String, valueColor: Color = Color(0xFF6B7078)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 12.sp, color = Color(0xFF6B7078)),
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(
            text = value,
            style = TextStyle(fontSize = 12.sp, color = valueColor),
        )
    }
}
