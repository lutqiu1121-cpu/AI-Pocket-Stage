package com.lutqiu.lumiread.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List as ListIcon
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.lutqiu.lumiread.data.Book
import com.lutqiu.lumiread.data.ReadingMode
import com.lutqiu.lumiread.data.VnLaunch
import com.lutqiu.lumiread.ui.bookshelf.BookshelfScreen
import com.lutqiu.lumiread.ui.reader.ReaderScreen
import com.lutqiu.lumiread.ui.settings.GlobalSettingsScreen
import com.lutqiu.lumiread.ui.stage.StageScreen
import com.lutqiu.lumiread.ui.wiki.WikiScreen

private val PageBg = Color(0xFFFDFDFC)
private val LumiBlue = Color(0xFF3B82F6)

private data class OpenedBook(val book: Book, val mode: ReadingMode)

/**
 * 全局壳：
 * 主界面 = Scaffold + 底部导航（书架 / 资料库 / 设置）
 * 阅读 / 剧场 = 全屏沉浸，不挂底部导航、不挂系统状态栏
 */
@Composable
fun LumiReadApp() {
    var opened by remember { mutableStateOf<OpenedBook?>(null) }
    var vn by remember { mutableStateOf<VnLaunch?>(null) }

    val current = opened
    if (current != null) {
        BackHandler { opened = null }

        val vnLaunch = vn
        if (vnLaunch != null) {
            BackHandler { vn = null }
            StageScreen(vn = vnLaunch, onBack = { vn = null })
            return
        }

        when (current.mode) {
            ReadingMode.READER -> ReaderScreen(
                book = current.book,
                onBack = { opened = null },
                onOpenVn = { vn = it },
            )

            ReadingMode.VN -> {
                StageScreen(
                    vn = VnLaunch(
                        book = current.book,
                        chapterIndex = 0,
                        paragraphIndex = 0,
                        sceneDesc = "",
                        imagePath = null,
                    ),
                    onBack = { opened = null },
                )
            }
        }
        return
    }

    // ---- 主界面 ----
    // 回到主界面时恢复系统状态栏
    val view = LocalView.current
    DisposableEffect(Unit) {
        val controller = view.context.findActivity()?.window
            ?.let { WindowCompat.getInsetsController(it, view) }
        controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = PageBg,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                AppTab(
                    selected = tab == 0,
                    label = "书架",
                    icon = { Icon(Icons.AutoMirrored.Filled.ListIcon, contentDescription = null) },
                    onClick = { tab = 0 },
                )
                AppTab(
                    selected = tab == 1,
                    label = "资料库",
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = { tab = 1 },
                )
                AppTab(
                    selected = tab == 2,
                    label = "设置",
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = { tab = 2 },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (tab) {
                0 -> BookshelfScreen(onOpenBook = { book, mode -> opened = OpenedBook(book, mode) })
                1 -> WikiScreen()
                else -> GlobalSettingsScreen()
            }
        }
    }
}

@Composable
private fun RowScope.AppTab(
    selected: Boolean,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = { Text(label, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = LumiBlue,
            selectedTextColor = LumiBlue,
            unselectedIconColor = Color(0xFF9AA0A6),
            unselectedTextColor = Color(0xFF9AA0A6),
            indicatorColor = LumiBlue.copy(alpha = 0.10f),
        ),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
