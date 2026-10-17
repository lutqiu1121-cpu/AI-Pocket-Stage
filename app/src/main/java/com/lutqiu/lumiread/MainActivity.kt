package com.lutqiu.lumiread

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.lutqiu.lumiread.services.RequestQueue
import com.lutqiu.lumiread.ui.LumiReadApp
import kotlinx.coroutines.launch

/**
 * 唯一入口。
 *
 * 1. 全屏沉浸：`WindowCompat.setDecorFitsSystemWindows(window, false)`，内容铺到状态栏 / 导航栏下面；
 * 2. 默认落地书架（[LumiReadApp] 内做「书架 ⇄ 舞台」的状态切换）；
 * 3. 启动时跑一次队列自检（5 个模拟任务，串行 + 3s 节流），Logcat tag = `RequestQueue`。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        lifecycleScope.launch { RequestQueue.testQueue() }

        setContent {
            LumiReadApp()
        }
    }
}
