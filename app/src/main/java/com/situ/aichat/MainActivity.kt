package com.situ.aichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.situ.aichat.ui.stage.StageScreen

/**
 * 第一期唯一入口：全屏静态 16:9 舞台。
 *
 * 只画皮 —— 不读旧项目、不建网络层、不接 API。启动即渲染一页静态 UI。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            StageScreen()
        }
    }
}
