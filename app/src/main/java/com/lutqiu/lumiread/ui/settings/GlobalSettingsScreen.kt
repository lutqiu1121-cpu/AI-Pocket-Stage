package com.lutqiu.lumiread.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lutqiu.lumiread.services.ApiConfigRepository

/** 全局设置：AI 能力配置（从阅读页挪过来）。 */
@Composable
fun GlobalSettingsScreen() {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(ApiConfigRepository.getZhipuKey(context)) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFDFDFC))
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = "设置",
            style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1B1E)),
        )

        Spacer(Modifier.height(28.dp))
        Text(
            text = "智谱 API Key",
            style = TextStyle(fontSize = 12.sp, color = Color(0xFF6B7078)),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                saved = false
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("粘贴 Key；填 MOCK 可离线验收", fontSize = 12.sp) },
            visualTransformation = PasswordVisualTransformation(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                ApiConfigRepository.setZhipuKey(context, apiKey)
                saved = true
            }) {
                Text(if (saved) "已保存" else "保存", color = Color(0xFF3B82F6))
            }
        }
    }
}
