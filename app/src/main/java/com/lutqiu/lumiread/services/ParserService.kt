package com.lutqiu.lumiread.services

import android.content.Context
import android.net.Uri
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
    private val HTML_TAG = Regex("<[^>]+>")
    private val BLANK_LINES = Regex("\n{3,}")

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

    suspend fun parseEpub(context: Context, uri: Uri): List<Chapter> = withContext(Dispatchers.IO) {
        val epub = readEpub(context, uri) ?: return@withContext emptyList()

        epub.spine.spineReferences.mapIndexedNotNull { i, reference ->
            val resource = reference.resource ?: return@mapIndexedNotNull null
            val raw = runCatching { resource.data?.toString(Charsets.UTF_8) }.getOrNull().orEmpty()
            val content = stripHtml(raw)
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

    // ── 工具 ───────────────────────────────────────────────────────────────

    /** 中文小说大量是 GBK：先按 UTF-8 严格解，出现替换符再退回 GBK。 */
    private fun decodeText(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8
        return runCatching { bytes.toString(charset("GBK")) }.getOrDefault(utf8)
    }

    private fun stripHtml(html: String): String =
        HTML_TAG.replace(html, "\n")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .let { BLANK_LINES.replace(it, "\n\n") }
            .trim()
}
