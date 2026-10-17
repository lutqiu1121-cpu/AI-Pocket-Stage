package com.lutqiu.lumiread.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lutqiu.lumiread.data.Book
import com.lutqiu.lumiread.ui.bookshelf.BookshelfScreen
import com.lutqiu.lumiread.ui.stage.StageScreen

/**
 * 极简导航：状态切换即可（第三期不引 Navigation Compose，少一层依赖、少一份路由表）。
 * 书架 ⇄ 舞台，系统返回键在舞台页退回书架。
 */
@Composable
fun LumiReadApp() {
    var openedBook by remember { mutableStateOf<Book?>(null) }

    val book = openedBook
    if (book == null) {
        BookshelfScreen(onOpenBook = { openedBook = it })
        return
    }

    BackHandler { openedBook = null }

    val chapter = book.chapters.firstOrNull()
    StageScreen(
        text = chapter?.content.orEmpty(),
        chapterLabel = listOfNotNull(book.title, chapter?.title).joinToString(" · "),
        onBack = { openedBook = null },
    )
}
