package com.lutqiu.lumiread.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lutqiu.lumiread.data.Book
import com.lutqiu.lumiread.services.AiScene
import com.lutqiu.lumiread.data.ReadingMode
import com.lutqiu.lumiread.ui.bookshelf.BookshelfScreen
import com.lutqiu.lumiread.ui.reader.ReaderScreen
import com.lutqiu.lumiread.ui.stage.StageScreen

/** 一次打开请求：书 + 进入方式（文本阅读 / VN 剧场）。 */
private data class OpenedBook(val book: Book, val mode: ReadingMode)

/**
 * 极简导航：状态切换（不引 Navigation Compose）。
 * 书架 → 阅读器 / VN 舞台；系统返回键回书架。
 */
@Composable
fun LumiReadApp() {
    var opened by remember { mutableStateOf<OpenedBook?>(null) }
    var aiScene by remember { mutableStateOf<AiScene?>(null) }

    val current = opened
    if (current == null) {
        BookshelfScreen(onOpenBook = { book, mode -> opened = OpenedBook(book, mode) })
        return
    }

    // AI 长按 → 生成场景 → 直接进 VN 舞台
    val scene = aiScene
    if (scene != null) {
        BackHandler { aiScene = null }
        StageScreen(
            text = scene.description,
            chapterLabel = "AI 场景",
            backgroundImagePath = scene.imagePath,
            onBack = { aiScene = null },
        )
        return
    }

    BackHandler { opened = null }

    when (current.mode) {
        ReadingMode.READER -> ReaderScreen(
            book = current.book,
            onBack = { opened = null },
            onOpenScene = { aiScene = it },
        )

        ReadingMode.VN -> {
            val chapter = current.book.chapters.firstOrNull()
            StageScreen(
                text = chapter?.content.orEmpty(),
                chapterLabel = listOfNotNull(current.book.title, chapter?.title).joinToString(" · "),
                onBack = { opened = null },
            )
        }
    }
}
