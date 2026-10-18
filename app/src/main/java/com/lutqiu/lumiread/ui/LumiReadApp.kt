package com.lutqiu.lumiread.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List as ListIcon
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.lutqiu.lumiread.ui.theme.LumiColors
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.lutqiu.lumiread.data.AppThemeMode
import com.lutqiu.lumiread.data.Book
import com.lutqiu.lumiread.data.ReadingMode
import com.lutqiu.lumiread.data.ThemeConfig
import com.lutqiu.lumiread.data.ThemeRepository
import com.lutqiu.lumiread.data.VnLaunch
import com.lutqiu.lumiread.ui.bookshelf.BookshelfScreen
import com.lutqiu.lumiread.ui.components.LiquidGlassPanel
import com.lutqiu.lumiread.ui.reader.ReaderScreen
import com.lutqiu.lumiread.ui.reader.ReaderScrollViewModel
import com.lutqiu.lumiread.ui.settings.GlobalSettingsScreen
import com.lutqiu.lumiread.ui.stage.StageScreen
import com.lutqiu.lumiread.ui.theme.AppDarkScheme
import com.lutqiu.lumiread.ui.theme.AppLightScheme
import com.lutqiu.lumiread.ui.theme.LumiReaderTheme
import com.lutqiu.lumiread.ui.wiki.WikiScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

private data class OpenedBook(val book: Book, val mode: ReadingMode)

/**
 * 全局壳：
 * - 主界面 = 液态玻璃底部导航（书架 / 资料库 / 设置），全局 App 主题由 DataStore 驱动；
 * - 阅读 / 剧场 = 全屏沉浸，使用阅读器独立主题（与全局主题互不干扰）。
 */
@Composable
fun LumiReadApp() {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val themeConfig by remember(context) { ThemeRepository.flow(context) }
        .collectAsState(initial = ThemeConfig())
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeConfig.appTheme) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> systemDark
    }
    val scheme = if (dark) AppDarkScheme else AppLightScheme

    // 状态栏 / 导航栏底色与图标颜色跟随当前 App 主题
    @Suppress("DEPRECATION")
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        window.statusBarColor = scheme.background.toArgb()
        window.navigationBarColor = scheme.background.toArgb()
        window.isNavigationBarContrastEnforced = false
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }

    MaterialTheme(colorScheme = scheme) {
        var opened by remember { mutableStateOf<OpenedBook?>(null) }
        var vn by remember { mutableStateOf<VnLaunch?>(null) }

        // 阅读进度挂在 Activity 级 ViewModel：VN 往返后复用同一个 LazyListState
        val owner = context.findActivity() as? ViewModelStoreOwner
        val readerScroll = remember(owner) {
            owner?.let { ViewModelProvider(it)[ReaderScrollViewModel::class.java] } ?: ReaderScrollViewModel()
        }

        val onReaderThemeChange: (com.lutqiu.lumiread.data.ReaderTheme) -> Unit = { theme ->
            scope.launch { ThemeRepository.setReaderTheme(context, theme) }
        }

        val current = opened
        if (current != null) {
            BackHandler { opened = null }

            val vnLaunch = vn
            if (vnLaunch != null) {
                BackHandler { vn = null }
                LumiReaderTheme(themeConfig.readerTheme) {
                    StageScreen(
                        vn = vnLaunch,
                        readerTheme = themeConfig.readerTheme,
                        onReaderThemeChange = onReaderThemeChange,
                        onBack = { vn = null },
                    )
                }
                return@MaterialTheme
            }

            LumiReaderTheme(themeConfig.readerTheme) {
                when (current.mode) {
                    ReadingMode.READER -> ReaderScreen(
                        book = current.book,
                        scroll = readerScroll,
                        readerTheme = themeConfig.readerTheme,
                        onReaderThemeChange = onReaderThemeChange,
                        onBack = { opened = null },
                        onOpenVn = { vn = it },
                    )

                    ReadingMode.VN -> StageScreen(
                        vn = VnLaunch(
                            book = current.book,
                            chapterIndex = 0,
                            paragraphIndex = 0,
                            sceneDesc = "",
                            imagePath = null,
                        ),
                        readerTheme = themeConfig.readerTheme,
                        onReaderThemeChange = onReaderThemeChange,
                        onBack = { opened = null },
                    )
                }
            }
            return@MaterialTheme
        }

        // ---- 主界面 ----
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val hazeState: HazeState = rememberHazeState()

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // 内容层：Haze 采样源，滚动时玻璃后方光影流动
            Box(
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            ) {
                when (tab) {
                    0 -> BookshelfScreen(onOpenBook = { book, mode ->
                        readerScroll.openBook(book.id, 0)
                        opened = OpenedBook(book, mode)
                    })

                    1 -> WikiScreen()
                    else -> GlobalSettingsScreen(
                        theme = themeConfig,
                        onAppThemeChange = { mode ->
                            scope.launch { ThemeRepository.setAppTheme(context, mode) }
                        },
                        onReaderThemeChange = onReaderThemeChange,
                    )
                }
            }

            GlassBottomBar(
                hazeState = hazeState,
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun GlassBottomBar(
    hazeState: HazeState,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassPanel(
        hazeState = hazeState,
        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        strong = true,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(
                selected = selected == 0,
                label = "书架",
                icon = { Icon(Icons.AutoMirrored.Filled.ListIcon, contentDescription = null) },
                onClick = { onSelect(0) },
            )
            NavItem(
                selected = selected == 1,
                label = "资料库",
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                onClick = { onSelect(1) },
            )
            NavItem(
                selected = selected == 2,
                label = "设置",
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = { onSelect(2) },
            )
        }
    }
}

@Composable
private fun NavItem(
    selected: Boolean,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val color = if (selected) scheme.primary else scheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (selected) scheme.primary.copy(alpha = 0.14f) else LumiColors.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides color,
            ) {
                icon()
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
            color = color,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
