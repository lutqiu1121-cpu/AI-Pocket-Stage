package com.lutqiu.lumiread.data

import kotlinx.serialization.Serializable

/** 进入一本书的方式：普通阅读 / VN 剧场。 */
@Serializable
enum class ReadingMode {
    READER,
    VN,
}

/** 书籍格式。 */
@Serializable
enum class BookFormat {
    TXT,
    EPUB,
}

/**
 * 书架上的书。章节内容随书一起序列化落地（第三期先用 JSON 文件，第四期再换 Room）。
 */
@Serializable
data class Book(
    val id: String,
    val title: String,
    val format: BookFormat,
    val author: String = "",
    val importedAt: Long = 0L,
    /** 用哪一版解析器切出来的；旧版本(0)会在书架加载时自动重解析。 */
    val parsedWith: Int = 0,
    val chapters: List<Chapter> = emptyList(),
)

/** 一章：正文 + 在书内的序号。 */
@Serializable
data class Chapter(
    val index: Int,
    val title: String,
    val content: String,
)

/**
 * 一次舞台演出所需的场景描述。第三期只建模，不生成、不渲染。
 */
@Serializable
data class Scene(
    val id: String,
    val bookId: String,
    val chapterIndex: Int,
    /** 背景画面提示词（生图用，第三期恒为空）。 */
    val backgroundPrompt: String = "",
    /** 角色立绘提示词（生图用，第三期恒为空）。 */
    val characterPrompt: String = "",
    /** 固定 Seed，保证同一角色跨场景一致。 */
    val seed: Long = 0L,
    /** 本场景的旁白文本。 */
    val narration: String = "",
)
