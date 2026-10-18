package com.lutqiu.lumiread.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ParagraphsTest {

    @Test
    fun splitsOnDoubleNewline() {
        val paragraphs = paragraphsOf("第一段。\n\n第二段。\n\n第三段。")
        assertEquals(3, paragraphs.size)
        assertEquals("第一段。", paragraphs[0])
    }

    @Test
    fun fallsBackToSingleNewline() {
        val paragraphs = paragraphsOf("第一行\n第二行")
        assertEquals(2, paragraphs.size)
    }

    @Test
    fun blankContentGivesEmptyList() {
        assertEquals(0, paragraphsOf("   ").size)
    }
}
