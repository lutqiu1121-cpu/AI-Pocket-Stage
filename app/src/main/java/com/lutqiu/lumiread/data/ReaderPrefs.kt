package com.lutqiu.lumiread.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

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
    /** 翻页动画：0 覆盖 / 1 滑动 / 2 仿真 / 3 滚动 / 4 无动画。 */
    val pageAnimation: Int = 3,
    val aiEnabled: Boolean = true,
)

/**
 * 阅读器独立主题（与全局 App 主题彻底隔离）。
 *
 * 颜色本身不允许硬编码在枚举里：具体配色见 `ui/theme/Color.kt` 的 `readerScheme`。
 */
enum class ReaderTheme(val label: String) {
    PAPER("白"),
    SEPIA("米黄"),
    EYE_GREEN("护眼绿"),
    DARK_GRAY("深灰"),
    PURE_BLACK("纯黑"),
}

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
            pageAnimation = p.getInt("page_animation", 3),
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
            putInt("page_animation", settings.pageAnimation)
            putBoolean("ai_enabled", settings.aiEnabled)
        }
    }
}
