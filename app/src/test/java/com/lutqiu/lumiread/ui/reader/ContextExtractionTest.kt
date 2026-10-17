package com.lutqiu.lumiread.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextExtractionTest {

    @Test
    fun splitSentences_splitsOnChinesePunctuation() {
        val sentences = splitSentences("第一句。第二句！第三句？")
        assertEquals(3, sentences.size)
        assertEquals("第一句。", sentences[0])
    }

    @Test
    fun extractContext_takesTwoBeforeAndTwoAfter() {
        val text = "一。二。三。四。五。六。七。八。九。十。"
        // fraction=0.45 → 第 5 句附近（index=4, "五。"），前后各 2 句 → 三~七
        val ctx = extractContext(text, 0.45f, before = 2, after = 2)
        assertTrue("应包含前两句", ctx.contains("三。"))
        assertTrue("应包含当句", ctx.contains("五。"))
        assertTrue("应包含后两句", ctx.contains("七。"))
        assertTrue("不应包含更远句子", !ctx.contains("一。"))
    }

    @Test
    fun extractContext_handlesEmptyText() {
        assertEquals("", extractContext("", 0.5f))
    }
}
