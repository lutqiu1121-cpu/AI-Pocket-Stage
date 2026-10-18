package com.lutqiu.lumiread.ui.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * 阅读页全局滚动状态（v0.4.0）。
 *
 * 为什么必须提升到 ViewModel：
 * 从阅读页长按进入 VN 时，[ReaderScreen] 会离开 Compose 组合树；
 * 如果 `LazyListState` 仍由页面内的 `remember` 持有，返回时列表会重置到章首。
 * 这里把列表状态、已加载章节与跳章信号都挂到 Activity 级 ViewModel 上，
 * VN 返回后直接复用同一个 [LazyListState]，滚动位置与滚动偏移逐像素保留。
 */
class ReaderScrollViewModel : ViewModel() {

    /** 当前阅读的书（用于判断是否换书，换书才重置）。 */
    var bookId by mutableStateOf<String?>(null)
        private set

    /** 当前章。 */
    var chapterIndex by mutableIntStateOf(0)

    /** 已加载进列表的章（从 chapterIndex 起连续，用于无缝续章）。 */
    var loadedChapters by mutableStateOf<List<Int>>(emptyList())
        private set

    /** 显式跳章时 +1，触发回到顶部；自动追加不会变。 */
    var jumpToken by mutableIntStateOf(0)

    /** 无动画翻页模式下的当前页索引（与滚动位置相互独立，互不覆盖）。 */
    var pagedPage by mutableIntStateOf(0)

    /** 长按选中的段（VN 的基准段 / 日志核对用）。 */
    var anchorChapter by mutableIntStateOf(0)
    var anchorParagraph by mutableIntStateOf(0)

    /**
     * 列表滚动状态本体。换书时整体替换成新的 [LazyListState]，
     * 同一本书跨页面往返则复用同一个实例 —— 这是「精准回位」的关键。
     */
    var listState by mutableStateOf(LazyListState())
        private set

    /** 打开一本书；只有换书才重置，VN 往返不会清空进度。 */
    fun openBook(id: String, startChapter: Int) {
        if (bookId == id) return
        bookId = id
        chapterIndex = startChapter
        loadedChapters = listOf(startChapter)
        jumpToken = 0
        pagedPage = 0
        anchorChapter = startChapter
        anchorParagraph = 0
        listState = LazyListState()
    }

    /** 无缝续章：追加下一章（去重、只在尾部追加）。 */
    fun appendChapter(next: Int) {
        if (loadedChapters.contains(next)) return
        loadedChapters = loadedChapters + next
        chapterIndex = next
    }

    /** 显式跳章：只保留目标章并回到顶部。 */
    fun jumpTo(index: Int) {
        chapterIndex = index
        loadedChapters = listOf(index)
        // 跳章必须同时重置两套状态：滚动列表回到顶部 + 翻页模式回到第 0 页
        pagedPage = 0
        jumpToken += 1
    }

    fun rememberAnchor(chapter: Int, paragraph: Int) {
        anchorChapter = chapter
        anchorParagraph = paragraph
    }
}
