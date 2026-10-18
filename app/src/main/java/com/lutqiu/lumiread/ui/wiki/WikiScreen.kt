package com.lutqiu.lumiread.ui.wiki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/** 资料库：占位页，后续放人物 / 世界观词条。 */
@Composable
fun WikiScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFDFDFC)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "人物与世界观资料库 - 开发中",
            style = TextStyle(fontSize = 14.sp, color = Color(0xFF9AA0A6)),
        )
    }
}
