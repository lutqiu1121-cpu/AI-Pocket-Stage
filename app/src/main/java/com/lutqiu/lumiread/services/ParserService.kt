package com.lutqiu.lumiread.services

import android.content.Context
import android.net.Uri
import android.text.Html
import android.util.Log
import com.lutqiu.lumiread.data.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.siegmann.epublib.domain.Book as EpubBook
import nl.siegmann.epublib.domain.TOCReference
import nl.siegmann.epublib.epub.EpubReader

/**
 * 文件解析服务：TXT / EPUB → [Chapter] 列表。
 *
 * 两条路径都走 `contentResolver.openInputStream(uri)`，因此对 SAF（系统文件选择器）返回的
 * `content://` Uri 直接可用，不需要申请存储权限。
 */
object ParserService {

    private const val TAG = "ParserService"

    /** 解析器版本：改动切章逻辑时 +1，书架会据此自动重解析旧缓存。 */
    const val PARSER_VERSION = 2

    /** 章节标题正则（TXT 与 EPUB 兜底共用）：第 + 数字/中文数字 + 章|节|回。 */
    private val CHAPTER_HEADING = Regex("""第[0-9一二三四五六七八九十百千万]+[章|节|回]""")

    /** 行首章节标题（多行模式）：把单个巨大 XHTML 二次切章用。 */
    private val HEADING_LINE = Regex("""(?m)^第[0-9一二三四五六七八九十百千万]+[章|节|回].*""")

    /** 目录 / 导航 / 封面页的文件名特征 —— 这些是书的结构元数据，绝不能当正文。 */
    private val SKIP_PATH_KEYWORDS = listOf("toc", "nav", "cover")

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
     * EPUB 解析（v0.2.4 重构）：
     *
     * 1. **目录页 / 封面页过滤**：href 里含 `toc` / `nav` / `cover` 的文档一律跳过 ——
     *    旧实现把 `nav.xhtml` 当正文，读者第一页看到的就是整本书的目录；
     * 2. **优先用 OPF 的真实目录**（`epub.tableOfContents.tocReferences`）：用目录项指向的
     *    资源出章，标题用目录名；同一 xhtml 被多个锚点引用时去重；
     * 3. **兜底**：没有 TOC，或整本书只有一个巨大 XHTML 时，用行首章节号正则
     *    `(?m)^第[0-9一二三四五六七八九十百千万]+[章|节|回].*` 对清洗后的正文二次切章。
     *
     * 每个文档清洗后仍会再走一次 [splitByHeading]，保证「一章一个巨文件」的书也能拆开。
     */
    suspend fun parseEpub(context: Context, uri: Uri): List<Chapter> = withContext(Dispatchers.IO) {
        val epub = readEpub(context, uri) ?: return@withContext emptyList()

        val fromToc = chaptersFromToc(epub)
        val base = fromToc.ifEmpty { chaptersFromSpine(epub) }

        // 每个文档内部若还有多个「第X章」标题 → 二次切分
        base.flatMap { chapter -> splitByHeading(chapter.content, chapter.title) }
            .filter { it.content.isNotBlank() }
            .mapIndexed { index, chapter -> chapter.copy(index = index) }
    }

    /** 目录页 / 导航页 / 封面页判定。 */
    internal fun shouldSkipHref(href: String?): Boolean {
        val path = href?.lowercase().orEmpty()
        if (path.isBlank()) return false
        return SKIP_PATH_KEYWORDS.any { path.contains(it) }
    }

    /** 从 OPF 目录建章（递归子目录，最多 4 层防环；同 href 只取第一次）。 */
    private fun chaptersFromToc(epub: EpubBook): List<Chapter> {
        val roots = runCatching { epub.tableOfContents?.tocReferences }.getOrNull().orEmpty()
        if (roots.isEmpty()) return emptyList()

        val chapters = mutableListOf<Chapter>()
        val seen = mutableSetOf<String>()

        fun visit(refs: List<TOCReference>, depth: Int) {
            refs.forEach { ref ->
                val resource = ref.resource
                val href = resource?.href ?: ref.completeHref
                if (resource != null && href != null && !shouldSkipHref(href)) {
                    // 同一 xhtml 被多个锚点引用（#ch1 / #ch2）只取一次，剩下的交给标题正则切
                    val key = href.substringBefore('#')
                    if (seen.add(key)) {
                        val text = cleanHtml(resource.data?.toString(Charsets.UTF_8).orEmpty())
                        if (text.isNotBlank()) {
                            val title = ref.title?.trim().orEmpty()
                                .ifBlank { "第${chapters.size + 1}章" }
                            chapters += Chapter(index = chapters.size, title = title, content = text)
                        }
                    }
                }
                if (depth < 3) visit(ref.children.orEmpty(), depth + 1)
            }
        }

        visit(roots, 0)
        return chapters
    }

    /** 没有目录时：按 spine 顺序出章，跳过 toc/nav/cover。 */
    private fun chaptersFromSpine(epub: EpubBook): List<Chapter> = buildList {
        epub.spine.spineReferences.forEach { reference ->
            val resource = reference.resource ?: return@forEach
            if (shouldSkipHref(resource.href)) return@forEach
            val text = cleanHtml(resource.data?.toString(Charsets.UTF_8).orEmpty())
            if (text.isNotBlank()) {
                val title = resource.title?.takeIf { it.isNotBlank() } ?: "第${size + 1}章"
                add(Chapter(index = size, title = title, content = text))
            }
        }
    }

    /**
     * 按行首章节号二次切分（兜底核心）。
     *
     * 命中不足 2 处时原样返回单章 —— 避免把正文里偶尔出现的「第1章」一句拆坏。
     */
    internal fun splitByHeading(text: String, fallbackTitle: String = "正文"): List<Chapter> {
        if (text.isBlank()) return emptyList()

        val marks = HEADING_LINE.findAll(text).toList()
        if (marks.size < 2) {
            return listOf(Chapter(index = 0, title = fallbackTitle, content = text.trim()))
        }

        val out = mutableListOf<Chapter>()
        val preface = text.substring(0, marks.first().range.first).trim()
        if (preface.isNotEmpty()) {
            out += Chapter(index = 0, title = fallbackTitle, content = preface)
        }

        marks.forEachIndexed { i, mark ->
            val start = mark.range.first
            val end = if (i + 1 < marks.size) marks[i + 1].range.first else text.length
            val body = text.substring(start, end).trim()
            if (body.isEmpty()) return@forEachIndexed
            val heading = body.lineSequence().firstOrNull().orEmpty().trim().take(48)
            out += Chapter(
                index = out.size,
                title = heading.ifBlank { "第${i + 1}章" },
                content = body,
            )
        }
        return out
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
     * 2. **保段落**：块级标签统一换成 `\n\n`，剩余标签换成空格，避免词粘连；
     * 3. **提纯**：[Html.fromHtml]（LEGACY 模式）解码实体并再剥一次残留标签。
     */
    internal fun cleanHtml(rawHtml: String): String {
        if (rawHtml.isBlank()) return ""

        val html = stripMarkup(rawHtml)
        val text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()

        var cleaned = text
        if (cleaned.contains('{') || cleaned.contains('<') || cleaned.contains('>')) {
            cleaned = CSS_RULE.replace(cleaned, " ")
            cleaned = ANY_TAG.replace(cleaned, " ")
        }
        return tidy(cleaned)
    }

    /** 第 ①② 层（纯正则、无 Android 依赖，可在 JVM 单测里直接验证）。 */
    internal fun stripMarkup(rawHtml: String): String {
        if (rawHtml.isBlank()) return ""

        var html = rawHtml

        html = XML_DECL.replace(html, " ")
        html = DOCTYPE.replace(html, " ")
        html = HTML_COMMENT.replace(html, " ")
        html = HEAD_BLOCK.replace(html, " ")
        html = TITLE_BLOCK.replace(html, " ")
        html = STYLE_BLOCK.replace(html, " ")
        html = SCRIPT_BLOCK.replace(html, " ")
        html = SVG_BLOCK.replace(html, " ")

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
