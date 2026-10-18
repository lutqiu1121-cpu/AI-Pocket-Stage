package com.lutqiu.lumiread.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** 普通阅读页的两种模式。 */
enum class ReaderPagingMode(val label: String) {
    /** 竖向滚动（默认）。 */
    SCROLL("滚动"),

    /** 无动画点击瞬切：左 40% 上一页 / 右 40% 下一页。 */
    PAGED("无动画"),
}

/** 阅读器可调参数（排版类，持久化到 SharedPreferences）。主题由 [ThemeRepository] 用 DataStore 单独管理。 */
data class ReaderSettings(
    val fontSize: Float = 18f,
    val letterSpacing: Float = 0.2f,
    val lineHeight: Float = 1.6f,
    /** 段间距，单位 dp。 */
    val paragraphSpacing: Int = 12,
    /** 0 细 / 1 中 / 2 粗。 */
    val fontWeight: Int = 1,
    val indentEnabled: Boolean = true,
    /** 左右边距，单位 dp。 */
    val marginDp: Int = 24,
    /** 阅读模式：滚动 / 无动画点击翻页。 */
    val pagingMode: ReaderPagingMode = ReaderPagingMode.SCROLL,
    val aiEnabled: Boolean = true,
)

object ReaderPrefs {

    private const val PREFS = "lumiread_reader_prefs"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): ReaderSettings {
        val p = prefs(context)
        return ReaderSettings(
            fontSize = p.getFloat("font_size", 18f),
            letterSpacing = p.getFloat("letter_spacing", 0.2f),
            lineHeight = p.getFloat("line_height", 1.6f),
            paragraphSpacing = p.getInt("paragraph_spacing", 12),
            fontWeight = p.getInt("font_weight", 1),
            indentEnabled = p.getBoolean("indent", true),
            marginDp = p.getInt("margin", 24),
            pagingMode = runCatching {
                ReaderPagingMode.valueOf(p.getString("paging_mode", ReaderPagingMode.SCROLL.name).orEmpty())
            }.getOrDefault(ReaderPagingMode.SCROLL),
            aiEnabled = p.getBoolean("ai_enabled", true),
        )
    }

    fun save(context: Context, settings: ReaderSettings) {
        prefs(context).edit {
            putFloat("font_size", settings.fontSize)
            putFloat("letter_spacing", settings.letterSpacing)
            putFloat("line_height", settings.lineHeight)
            putInt("paragraph_spacing", settings.paragraphSpacing)
            putInt("font_weight", settings.fontWeight)
            putBoolean("indent", settings.indentEnabled)
            putInt("margin", settings.marginDp)
            putString("paging_mode", settings.pagingMode.name)
            putBoolean("ai_enabled", settings.aiEnabled)
        }
    }
}
