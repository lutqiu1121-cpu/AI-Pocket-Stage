package com.lutqiu.lumiread.data

/** 从阅读页长按「生成视觉小说场景」进入 VN 的入口参数（基准段 = 用户长按时的可见段落）。 */
data class VnLaunch(
    val book: Book,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val sceneDesc: String,
    val imagePath: String?,
)
