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
    fun stripMarkup_keepsParagraphBreaks() {
        // v0.3.1 回归：块级标签必须变成 \n\n，不能被压成空格
        val out = ParserService.stripMarkup("<p>第一段</p><p>第二段</p><div>第三段</div>")
        assertTrue("必须保留段落双换行，实际: ${out.replace("\n", "\\n")}", out.contains("\n\n"))
        val paragraphs = out.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals("应拆出 3 段", 3, paragraphs.size)
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
    fun normalizeTxt_unifiesLineBreaksAndParagraphs() {
        // 1) CRLF / CR → LF；2) 连续空行压成 \n\n
        val raw = "第一段。\r\n\r\n\r\n第二段。\r第三段。"
        val out = ParserService.normalizeTxt(raw)
        assertFalse("不应残留 \\r", out.contains('\r'))
        assertTrue("应有段落分隔", out.contains("\n\n"))
        assertTrue(out.contains("第二段。"))
    }

    @Test
    fun normalizeTxt_splitsMixedSingleAndDoubleNewlines() {
        // 真机 bug 回归：既有空行、又有单换行的 TXT，旧实现只按空行切 → 段落粘连
        val raw = "第一段。\n\n第二段开头。\n这里应该也是新的一段。\n还有这一段。"
        val out = ParserService.normalizeTxt(raw)
        val paragraphs = out.split("\n\n").filter { it.isNotBlank() }
        assertEquals(4, paragraphs.size)
        assertEquals("第一段。", paragraphs[0])
        assertEquals("这里应该也是新的一段。", paragraphs[2])
    }

    @Test
    fun normalizeTxt_splitsWhenNoBlankLines() {
        // 只有单换行 → 每行一段
        val out = ParserService.normalizeTxt("第一行\n第二行\n第三行")
        assertEquals(3, out.split("\n\n").size)
    }

    @Test
    fun normalizeTxt_splitsWhenNoLineBreaksAtAll() {
        // 全挤成一整段 → 按句号切、每 2 句一段
        val raw = "第一句。第二句。第三句。第四句。"
        val out = ParserService.normalizeTxt(raw)
        val paragraphs = out.split("\n\n")
        assertEquals(2, paragraphs.size)
        assertEquals("第一句。第二句。", paragraphs[0])
    }

    @Test
    fun splitTxt_splitsOnChapterHeadings() {
        val filler = "这是一段足够长的正文，用来避免被 Ghost 章节过滤器误判成标题碎片。".repeat(3)
        val text = """
            引子：这是一句题记。
            第一章 开始
            $filler
            第二章 继续
            $filler
            第十章 终章
            $filler
        """.trimIndent()

        val chapters = ParserService.splitTxt(text)
        assertEquals("前言 + 3 章", 4, chapters.size)
        assertEquals("前言", chapters[0].title)
        assertEquals("第一章 开始", chapters[1].title)
        assertTrue(chapters[3].content.contains("这是一段足够长的正文"))
    }

    @Test
    fun splitTxt_matchesCompoundHeadings() {
        val filler = "正文内容足够长，确保这一章不会被 Ghost 过滤器合并到上一章里去。".repeat(3)
        val filler2 = "第二段：换行后仍然要保留段落分隔，绝不能粘成一大坨。".repeat(2)
        val text = """
              楔子
            $filler
            $filler2
            序章 起点
            $filler
            $filler2
            第一章 雨夜
            $filler
            $filler2
            第十二卷 风暴
            $filler
            $filler2
            番外 后日谈
            $filler
            $filler2
            后记
            $filler
            $filler2
        """.trimIndent()

        val chapters = ParserService.splitTxt(text)
        assertEquals("6 个标题全部识别", 6, chapters.size)
        assertEquals("楔子", chapters[0].title)
        assertEquals("序章 起点", chapters[1].title)
        assertEquals("第十二卷 风暴", chapters[3].title)
        assertEquals("番外 后日谈", chapters[4].title)
        assertEquals("后记", chapters[5].title)
        assertTrue("段落必须 \n\n 分隔", chapters[1].content.contains("\n\n"))
    }

    @Test
    fun splitTxt_mergesGhostChapterIntoPrevious() {
        val filler = "这一章正文足够长，不会被 Ghost 过滤器当作误判标题。".repeat(4)
        val text = """
            第一章 长章节
            $filler
            第二章
            只有一句话。
            第三章 正常章节
            $filler
        """.trimIndent()

        val chapters = ParserService.splitTxt(text)
        assertEquals("Ghost 章必须并入上一章", 2, chapters.size)
        assertEquals("第一章 长章节", chapters[0].title)
        assertTrue("被合并的短正文不能丢", chapters[0].content.contains("只有一句话。"))
        assertEquals("第三章 正常章节", chapters[1].title)
    }

    @Test
    fun splitTxt_rejectsHeadingWithLongTail() {
        // 行尾超过 30 字 → 不是标题，按正文处理
        val longTail = "第一章 " + "这是很长的一句正文".repeat(5)
        val text = "$longTail\n正文正文。"
        val chapters = ParserService.splitTxt(text)
        assertEquals("不应切章", 1, chapters.size)
    }

    @Test
    fun parseNcxToc_extractsHrefAndTitle() {
        val ncx = """
            <ncx><navMap>
              <navPoint id="n1"><navLabel><text>第一章 雨夜</text></navLabel><content src="Text/ch1.xhtml"/></navPoint>
              <navPoint id="n2"><navLabel><text>第二章 清晨</text></navLabel><content src="Text/ch2.xhtml#part"/></navPoint>
            </navMap></ncx>
        """.trimIndent()
        val toc = ParserService.parseNcxToc(ncx)
        assertEquals(2, toc.size)
        assertEquals("text/ch1.xhtml", toc[0].href)
        assertEquals("第一章 雨夜", toc[0].title)
        assertEquals("text/ch2.xhtml", toc[1].href)
    }

    @Test
    fun parseNavToc_extractsAnchors() {
        val nav = """
            <nav epub:type="toc"><ol>
              <li><a href="Text/ch1.xhtml"><span>第一章</span></a></li>
              <li><a href="Text/ch2.xhtml">第二章</a></li>
            </ol></nav>
        """.trimIndent()
        val toc = ParserService.parseNavToc(nav)
        assertEquals(2, toc.size)
        assertEquals("text/ch1.xhtml", toc[0].href)
        assertEquals("第一章", toc[0].title)
    }

    @Test
    fun assembleEpubChapters_appendsOrphanSpineToPrevious() {
        val toc = listOf(
            ParserService.TocEntry("text/ch1.xhtml", "第一章"),
            ParserService.TocEntry("text/ch2.xhtml", "第二章"),
        )
        val spine = listOf(
            ParserService.SpineDoc("Text/ch1.xhtml", "第一章", "第一章正文"),
            ParserService.SpineDoc("Text/ch2.xhtml", "第二章", "第二章正文"),
            ParserService.SpineDoc("Text/fragment.xhtml", "碎片", "未出现在目录里的碎片正文"),
        )

        val chapters = ParserService.assembleEpubChapters(toc, spine)
        assertEquals("碎片不允许独立成章", 2, chapters.size)
        assertEquals("第一章", chapters[0].title)
        assertTrue("碎片应 append 到上一章末尾", chapters[1].content.contains("碎片正文"))
        assertTrue("第二章正文不能被覆盖", chapters[1].content.contains("第二章正文"))
    }

    @Test
    fun assembleEpubChapters_withoutToc_fallsBackToSpine() {
        val spine = listOf(
            ParserService.SpineDoc("Text/a.xhtml", "A", "正文 A"),
            ParserService.SpineDoc("Text/b.xhtml", "B", "正文 B"),
        )
        val chapters = ParserService.assembleEpubChapters(emptyList(), spine)
        assertEquals(2, chapters.size)
    }

    @Test
    fun headingScoring_doesNotStripCommaOrQuestionMark() {
        // 逗号 / 问号不剔除，只做轻度扣分
        assertTrue(ParserService.scoreHeadingLine("第一章，雨夜？") >= 60)
    }

    @Test
    fun headingScoring_rejectsTitleLongerThan25Chars() {
        val line = "第一章 " + "这是一个特别特别长的标题用来模拟正文句子被误判".repeat(2)
        assertEquals(0, ParserService.scoreHeadingLine(line))
    }

    @Test
    fun headingScoring_rejectsSentenceLikeLine() {
        // 有句末标点 + 偏长，综合评分必须低于阈值，不能被切章
        assertTrue(ParserService.scoreHeadingLine("第一章 他推开门，看见屋里没有人。") < 60)
    }
}
