package com.lutqiu.lumiread.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 无动画阅读模式的分页纯函数测试。 */
class PagingTest {

    @Test
    fun paginateParagraphs_packsByHeightAndSpacing() {
        val pages = paginateParagraphs(
            paragraphs = listOf("A".repeat(10), "B".repeat(10), "C".repeat(10)),
            pageHeight = 25f,
            spacing = 5f,
            measure = { text, _ -> text.length.toFloat() },
            split = { listOf(it) },
        )
        assertEquals("应分成 2 页", 2, pages.size)
        assertEquals("第一页两段", 2, pages[0].chunks.size)
        assertEquals(0, pages[0].chunks[0].paragraphIndex)
        assertEquals(1, pages[0].chunks[1].paragraphIndex)
        assertEquals(2, pages[1].chunks[0].paragraphIndex)
    }

    @Test
    fun paginateParagraphs_splitsTallParagraphIntoChunks() {
        val pages = paginateParagraphs(
            paragraphs = listOf("X".repeat(20)),
            pageHeight = 25f,
            spacing = 5f,
            measure = { text, _ -> text.length * 2f },
            split = { text -> listOf(text.take(10), text.drop(10)) },
        )
        assertEquals("超高一页装不下 → 2 页", 2, pages.size)
        assertTrue("首块保留首行缩进标记", pages[0].chunks[0].firstChunk)
        assertFalse("续块不再缩进", pages[1].chunks[0].firstChunk)
        assertEquals(0, pages[0].chunks[0].paragraphIndex)
        assertEquals(0, pages[1].chunks[0].paragraphIndex)
    }

    @Test
    fun paginateParagraphs_emptyContentStillReturnsOnePage() {
        val pages = paginateParagraphs(
            paragraphs = emptyList(),
            pageHeight = 100f,
            spacing = 0f,
            measure = { _, _ -> 0f },
            split = { listOf(it) },
        )
        assertEquals(1, pages.size)
        assertTrue(pages[0].chunks.isEmpty())
    }

    @Test
    fun jumpToChapter_resetsScrollAndPagedState() {
        val vm = ReaderScrollViewModel()
        vm.openBook("book-1", 3)
        vm.pagedPage = 7
        vm.jumpTo(9)

        assertEquals(9, vm.chapterIndex)
        assertEquals(listOf(9), vm.loadedChapters)
        assertEquals("目录/滑块跳章后必须回到第 0 页", 0, vm.pagedPage)
        assertTrue("跳章信号必须自增，触发滚动复位", vm.jumpToken > 0)
    }
}
