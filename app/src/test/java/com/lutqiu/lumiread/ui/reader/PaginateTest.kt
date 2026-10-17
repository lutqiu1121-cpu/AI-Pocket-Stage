package com.lutqiu.lumiread.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginateTest {

    @Test
    fun shortChapterStaysSinglePage() {
        val text = "短章节，一页读完。"
        val pages = paginate(text)
        assertEquals(1, pages.size)
        assertEquals(text, pages[0])
    }

    @Test
    fun longChapterIsSplitIntoPagesAndKeepsAllText() {
        // 构造 3000 字，段落之间用 \n\n
        val paragraph = "这是一段测试正文，用来验证长章节会被切成多页。" // 24 字
        val text = (1..125).joinToString("\n\n") { paragraph }

        val pages = paginate(text, pageChars = 1000)
        assertTrue("应切成多页，实际 ${pages.size}", pages.size >= 3)

        // 每页不超过目标字数太多（允许在段落边界断页）
        pages.forEach { assertTrue("单页过长: ${it.length}", it.length <= 1100) }

        // 内容不丢：去掉空白后总长度一致
        val joined = pages.joinToString("").filterNot { it.isWhitespace() }
        assertEquals(text.filterNot { it.isWhitespace() }, joined)
    }
}
