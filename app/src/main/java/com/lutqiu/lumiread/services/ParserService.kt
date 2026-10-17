package com.lutqiu.lumiread.services

import android.content.Context
import android.net.Uri
import android.text.Html
import android.util.Log
import com.lutqiu.lumiread.data.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.siegmann.epublib.domain.Book as EpubBook
import nl.siegmann.epublib.epub.EpubReader

/**
 * 文件解析服务：TXT / EPUB → [Chapter] 列表。
 *
 * 两条路径都走 `contentResolver.openInputStream(uri)`，因此对 SAF（系统文件选择器）返回的
 * `content://` Uri 直接可用，不需要申请存储权限。
 */
object ParserService {

    private const val TAG = "ParserService"

    /** 章节标题正则：第 + 数字/中文数字 + 章|节|回。 */
    private val CHAPTER_HEADING = Regex("""第[0-9一二三四五六七八九十百千万]+[章|节|回]""")

    // ── EPUB 清洗用正则（全部 DOTALL，跨行匹配）───────────────────────────
    private val XML_DECL = Regex("""(?is)<\?xml.*?\?>""")
    private val DOCTYPE = Regex("""(?is)<!DOCTYPE.*?>""")
    private val HTML_COMMENT = Regex("""(?s)<!--.*?-->""")
    private val HEAD_BLOCK = Regex("""(?is)<head\b[^>]*>.*?</head\s*>""")
    private val STYLE_BLOCK = Regex("""(?is)<style\b[^>]*>.*?</style\s*>""")
    private val SCRIPT_BLOCK = Regex("""(?is)<script\b[^>]*>.*?</script\s*>""")
    private val SVG_BLOCK = Regex("""(?is)<svg\b[^>]*>.*?</svg\s*>""")
    private val TITLE_BLOCK = Regex("""(?is)<title\b[^>]*>.*?</title\s*>""")

    /** 块级标签 → 双换行（保留自然段）。 */
    private val BLOCK_TAG = Regex(
        """(?is)</?(p|div|br|h[1-6]|li|ul|ol|tr|table|section|article|blockquote|pre|hr)\b[^>]*>""",
    )

    /** 剩下所有标签（含尖括号内的属性）→ 空格。 */
    private val ANY_TAG = Regex("""(?s)<[^>]+>""")

    /** 兜底：逃过前面清洗的 CSS 规则块 { ... }。 */
    private val CSS_RULE = Regex("""(?s)\{[^{}]*\}""")

    private val MULTI_BLANK_LINE = Regex("""\n{3,}""")
    private val INLINE_SPACE = Regex("""[ \t\u00A0\u3000]+""")

    // ── TXT ────────────────────────────────────────────────────────────────

    suspend fun parseTxt(context: Context, uri: Uri): List<Chapter> = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.onFailure { Log.w(TAG, "打开 TXT 失败: $uri", it) }.getOrNull()

        if (bytes == null || bytes.isEmpty()) return@withContext emptyList()
        splitTxt(decodeText(bytes))
    }

    /** 按章节正则切分纯文本；没有命中任何标题时整篇作为一章返回。 */
    internal fun splitTxt(text: String): List<Chapter> {
        if (text.isBlank()) return emptyList()

        val marks = CHAPTER_HEADING.findAll(text).toList()
        if (marks.isEmpty()) {
            return listOf(Chapter(index = 0, title = "正文", content = text.trim()))
        }

        val chapters = mutableListOf<Chapter>()
        val preface = text.substring(0, marks.first().range.first).trim()
        if (preface.isNotEmpty()) {
            chapters += Chapter(index = 0, title = "前言", content = preface)
        }

        marks.forEachIndexed { i, mark ->
            val start = mark.range.first
            val end = if (i + 1 < marks.size) marks[i + 1].range.first else text.length
            val body = text.substring(start, end).trim()
            if (body.isEmpty()) return@forEachIndexed
            val heading = body.lineSequence().firstOrNull().orEmpty().trim().take(48)
            chapters += Chapter(
                index = chapters.size,
                title = heading.ifBlank { "第${i + 1}章" },
                content = body,
            )
        }
        return chapters
    }

    // ── EPUB ───────────────────────────────────────────────────────────────

    /**
     * EPUB 解析。
     *
     * 关键点：epublib 给出来的是**原始 XHTML 源码**（含 `<head><style>` 里那一大坨 CSS），
     * 直接 `toString()` 就会把 `html, body { margin: 0; }` 整段塞进阅读界面 —— 这就是真机
     * 反馈的乱码根因。所以这里必须做三层清洗（见 [cleanHtml]）。
     */
    suspend fun parseEpub(context: Context, uri: Uri): List<Chapter> = withContext(Dispatchers.IO) {
        val epub = readEpub(context, uri) ?: return@withContext emptyList()

        epub.spine.spineReferences.mapIndexedNotNull { i, reference ->
            val resource = reference.resource ?: return@mapIndexedNotNull null
            val rawHtml = runCatching { resource.data?.toString(Charsets.UTF_8) }
                .getOrNull()
                .orEmpty()
            val content = cleanHtml(rawHtml)
            if (content.isBlank()) return@mapIndexedNotNull null
            Chapter(
                index = i,
                title = resource.title?.takeIf { it.isNotBlank() } ?: "第${i + 1}章",
                content = content,
            )
        }
    }

    /** 单独取书名（书架列表用）；解析失败返回 null。 */
    suspend fun readEpubTitle(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching { readEpub(context, uri)?.title?.takeIf { it.isNotBlank() } }
            .onFailure { Log.w(TAG, "读取 EPUB 书名失败: $uri", it) }
            .getOrNull()
    }

    private fun readEpub(context: Context, uri: Uri): EpubBook? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            EpubReader().readEpub(stream)
        }
    }.onFailure { Log.w(TAG, "打开 EPUB 失败: $uri", it) }.getOrNull()

    /**
     * 三层清洗：XHTML 源码 → 纯可见文本。
     *
     * 1. **剥区块**：`<?xml?>` / `<!DOCTYPE>` / 注释 / `<head>` / `<title>` / `<style>` /
     *    `<script>` / `<svg>` —— CSS、内联脚本、SVG 路径数据在这一步全部丢掉；
     * 2. **保段落**：`<p>` `<div>` `<br>` `</h1..h6>` 等块级标签统一换成 `\n\n`，
     *    剩余标签换成空格，避免词粘连；
     * 3. **提纯**：[Html.fromHtml]（LEGACY 模式）解码 `&nbsp;` `&amp;` 等实体并再剥一次残留标签；
     *    最后做白空格 / 空行归一，并兜底清掉任何漏网的 `{ ... }` CSS 规则块。
     *
     * 出口保证：不含 `<`、`>`、`{`、`}`，也不含 `html, body {` 之类的规则文本。
     */
    internal fun cleanHtml(rawHtml: String): String {
        if (rawHtml.isBlank()) return ""

        // ①② 先做纯正则清洗（可单测，不依赖 Android 框架）
        val html = stripMarkup(rawHtml)

        // ③ 交给系统 Html 解析器解码实体，再兜底
        val text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()

        var cleaned = text
        // 极端容错：某些 XHTML 用了从未闭合的 <style>，正则没匹配上 → 这里按 CSS 规则块再切一次
        if (cleaned.contains('{') || cleaned.contains('<') || cleaned.contains('>')) {
            cleaned = CSS_RULE.replace(cleaned, " ")
            cleaned = ANY_TAG.replace(cleaned, " ")
        }
        return tidy(cleaned)
    }

    /**
     * 第 ①② 层（纯正则、无 Android 依赖，可在 JVM 单测里直接验证）：
     * 剥 head/style/script/svg 等区块 → 块级标签换段 → 其余标签清空。
     * 出口已不含任何 `<...>`，也不会残留 CSS 规则文本。
     */
    internal fun stripMarkup(rawHtml: String): String {
        if (rawHtml.isBlank()) return ""

        var html = rawHtml

        // ① 剥掉所有不可能出现在正文里的区块
        html = XML_DECL.replace(html, " ")
        html = DOCTYPE.replace(html, " ")
        html = HTML_COMMENT.replace(html, " ")
        html = HEAD_BLOCK.replace(html, " ")
        html = TITLE_BLOCK.replace(html, " ")
        html = STYLE_BLOCK.replace(html, " ")
        html = SCRIPT_BLOCK.replace(html, " ")
        html = SVG_BLOCK.replace(html, " ")

        // ② 块级标签 → 段落分隔；其余标签 → 空格
        html = BLOCK_TAG.replace(html, "\n\n")
        html = ANY_TAG.replace(html, " ")
        return html
    }

    /** 空白归一：行内多空格压成一个，空行最多保留一个。 */
    private fun tidy(text: String): String =
        text.replace('\u00A0', ' ')
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(INLINE_SPACE, " ")
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(MULTI_BLANK_LINE, "\n\n")
            .trim()

    // ── 工具 ───────────────────────────────────────────────────────────────

    /** 中文小说大量是 GBK：先按 UTF-8 严格解，出现替换符再退回 GBK。 */
    private fun decodeText(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8
        return runCatching { bytes.toString(charset("GBK")) }.getOrDefault(utf8)
    }
}
