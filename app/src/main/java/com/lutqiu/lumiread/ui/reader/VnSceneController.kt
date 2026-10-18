package com.lutqiu.lumiread.ui.reader

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import com.lutqiu.lumiread.data.Chapter
import com.lutqiu.lumiread.services.AiSceneService
import com.lutqiu.lumiread.services.RequestQueue

/**
 * VN 场景状态机：段落 → 图片 的缓存 / 预加载 / 去重。
 *
 * 规则：
 * - 已缓存（或场景未变复用旧图）→ 不发请求，只打 `[Preload] 命中缓存跳过`；
 * - 同一段永远不会重复入队（[started] 去重）；
 * - 预加载静默执行，不弹 Toast、不打断当前 UI。
 */
class VnSceneController(
    context: Context,
    chapter: Chapter,
) {
    private companion object {
        const val TAG = "VnScene"
    }

    private val appContext = context.applicationContext
    private val paragraphs: List<String> = paragraphsOf(chapter.content)

    /** 段落 → 图片缓存（null 表示还需要生成）。 */
    private val cache = mutableStateMapOf<Int, String?>()
    private val loadingMap = mutableStateMapOf<Int, Boolean>()
    private val started = mutableSetOf<Int>()

    val size: Int get() = paragraphs.size

    fun paragraph(index: Int): String = paragraphs.getOrNull(index).orEmpty()

    fun image(index: Int): String? = cache[index]

    fun isLoading(index: Int): Boolean = loadingMap[index] == true

    /** 进入 VN 时把外部生成好的首图塞进第 N 段，保证「基准锁定」。 */
    fun seed(index: Int, path: String?) {
        if (path != null) cache[index] = path
    }

    /** 静默预加载某一段（可由 LaunchedEffect 并发调用，内部串行入队 + 去重）。 */
    suspend fun preload(index: Int) {
        if (index !in paragraphs.indices) return

        if (cache.containsKey(index)) {
            Log.i(TAG, "[Preload] 命中缓存跳过 段落 $index")
            return
        }
        if (!started.add(index)) return

        Log.i(TAG, "[Preload] 开始预加载段落 $index")
        loadingMap[index] = true
        try {
            val text = paragraphs[index]
            val scene = RequestQueue.submit("vn-analyze-$index") {
                AiSceneService.analyze(appContext, text)
            }

            val previousImage = cache[index - 1]
            if (!scene.changed && previousImage != null) {
                cache[index] = previousImage
                Log.i(TAG, "[Preload] 段落 $index 场景未变，复用上一张图")
            } else {
                val path = RequestQueue.submit("vn-image-$index") {
                    AiSceneService.generateImage(appContext, scene.prompt, index.toLong())
                }
                cache[index] = path
                Log.i(TAG, "[Preload] 段落 $index 生图完成")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "[Preload] 段落 $index 预加载失败：${t.message}")
        } finally {
            loadingMap[index] = false
        }
    }
}
