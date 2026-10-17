package com.lutqiu.lumiread.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EPUB 清洗前两层的单测：用的是 [ParserService.stripMarkup] 这段线上真实代码，
 * 只是把最后一步 `Html.fromHtml` 换成断言（该步骤是 Android 框架行为，纯 JVM 不可用）。
 */
class ParserServiceTest {

    /** 真实 EPUB 章节的样子：XHTML + <head> 里一大坨 @font-face / body 规则 + 内联脚本 + SVG。 */
    private val xhtml = """
        <?xml version="1.0" encoding="utf-8"?>
        <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
        <html xmlns="http://www.w3.org/1999/xhtml">
        <head>
          <title>第一章</title>
          <style type="text/css">
            @font-face { font-family: "宋体"; src: url(fonts/song.ttf); }
            html, body { margin: 0; padding: 0; font-family: "宋体", serif; font-size: 1em; }
            p { text-indent: 2em; line-height: 1.6; color: #333; }
          </style>
          <script type="text/javascript">document.write("x");</script>
        </head>
        <body>
          <h1>第一章 起点</h1>
          <p>第一套要跟三个男生合租，我本来以为自己已经做好了心理准备。</p>
          <div>可推开门的那一刻，客厅里三道视线同时抬起来。</div>
          <p>一个在系围裙，一个抱着吉他。<br/>还有一个把书扣在膝盖上。</p>
          <svg xmlns="http://www.w3.org/2000/svg"><path d="M0 0 L10 10"/></svg>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun stripMarkup_removesCssScriptSvgAndAllTags() {
        val out = ParserService.stripMarkup(xhtml)

        // 1) 一个尖括号都不许剩
        assertFalse("不应残留 '<'，实际: $out", out.contains('<'))
        assertFalse("不应残留 '>'，实际: $out", out.contains('>'))

        // 2) CSS / 脚本 / SVG 路径全部消失
        listOf("font-family", "@font-face", "margin", "text-indent", "document.write", "M0 0 L10 10", "svg")
            .forEach { banned ->
                assertFalse("不应残留 CSS/脚本片段 「$banned」，实际: $out", out.lowercase().contains(banned))
            }
        assertFalse("不应残留花括号", out.contains('{') || out.contains('}'))

        // 3) 正文必须完整保留，且段落被换行分开
        assertTrue(out.contains("第一套要跟三个男生合租"))
        assertTrue(out.contains("一个在系围裙，一个抱着吉他"))
        assertTrue("段落应有换行", out.contains("\n"))
        assertTrue("h1 应被保留为文本", out.contains("第一章 起点"))
    }

    @Test
    fun stripMarkup_keepsEntitiesForHtmlLayer() {
        // 实体留给第三步 Html.fromHtml 解码，这里只要求标签被清掉、文本不丢
        val out = ParserService.stripMarkup("<p>A&amp;B&nbsp;C</p>")
        assertTrue(out.contains("A&amp;B"))
        assertFalse(out.contains("<p>"))
    }

    @Test
    fun shouldSkipHref_filtersTocNavCover() {
        // 目录 / 导航 / 封面页必须被过滤
        assertTrue(ParserService.shouldSkipHref("OEBPS/nav.xhtml"))
        assertTrue(ParserService.shouldSkipHref("text/toc.html"))
        assertTrue(ParserService.shouldSkipHref("Images/cover.jpg"))
        assertTrue(ParserService.shouldSkipHref("Text/Cover.xhtml"))
        // 正常章节不能被误杀
        assertFalse(ParserService.shouldSkipHref("Text/chapter1.xhtml"))
        assertFalse(ParserService.shouldSkipHref("OEBPS/content-002.xhtml"))
        assertFalse(ParserService.shouldSkipHref(null))
    }

    @Test
    fun splitByHeading_splitsSingleHugeXhtml() {
        // 整本书塞进一个 xhtml 的典型情况：清洗后正文里有多处「第N章」行
        val blob = """
            第一套要跟三个男生合租。

            第一章 起点
            正文一。
            第二章 转折
            正文二。
            第三章 终局
            正文三。
        """.trimIndent()

        val chapters = ParserService.splitByHeading(blob, "整本")

        assertEquals("前言 + 3 章", 4, chapters.size)
        assertEquals("整本", chapters[0].title)
        assertEquals("第一章 起点", chapters[1].title)
        assertEquals("第三章 终局", chapters[3].title)
        assertTrue(chapters[2].content.contains("正文二"))
    }

    @Test
    fun splitByHeading_keepsSingleChapterWhenNoHeadings() {
        val text = "就只有一段正文，没有章节标题。"
        val chapters = ParserService.splitByHeading(text, "第一章")
        assertEquals(1, chapters.size)
        assertEquals("第一章", chapters[0].title)
    }

    @Test
    fun splitTxt_splitsOnChapterHeadings() {
        val text = """
            前言一句话。
            第一章 开始
            正文一。
            第二章 继续
            正文二。
            第十章 终章
            正文三。
        """.trimIndent()

        val chapters = ParserService.splitTxt(text)
        assertEquals("前言 + 3 章", 4, chapters.size)
        assertEquals("前言", chapters[0].title)
        assertEquals("第一章 开始", chapters[1].title)
        assertTrue(chapters[3].content.contains("正文三"))
    }
}
