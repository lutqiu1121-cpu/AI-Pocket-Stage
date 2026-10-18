package com.lutqiu.lumiread.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lutqiu.lumiread.ui.theme.LumiColorTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 6 维深度主题配置（DataStore 持久化）。
 *
 * 1. UI 圆润度（0-24dp）
 * 2. 玻璃质感开关
 * 3. UI 全局背景色
 * 4. UI 重点色（Accent）
 * 5. 阅读器背景色
 * 6. 阅读器重点色
 *
 * 每个维度独立存储、互不覆盖；颜色统一以 ARGB Long 存储，默认值来自 [LumiColorTokens]。
 */
data class ThemeConfig(
    val cornerRadiusDp: Int = 16,
    val glassEnabled: Boolean = true,
    val uiBackground: Long = LumiColorTokens.UI_BACKGROUND,
    val uiAccent: Long = LumiColorTokens.UI_ACCENT,
    val readerBackground: Long = LumiColorTokens.READER_BACKGROUND,
    val readerAccent: Long = LumiColorTokens.READER_ACCENT,
)

private val Context.themeDataStore by preferencesDataStore(name = "lumiread_theme_v2")

/** 主题持久化仓库：单项写入，避免「改圆角把颜色覆盖回默认值」。 */
object ThemeRepository {

    private val KEY_RADIUS = intPreferencesKey("corner_radius_dp")
    private val KEY_GLASS = booleanPreferencesKey("glass_enabled")
    private val KEY_UI_BG = longPreferencesKey("ui_background_argb")
    private val KEY_UI_ACCENT = longPreferencesKey("ui_accent_argb")
    private val KEY_READER_BG = longPreferencesKey("reader_background_argb")
    private val KEY_READER_ACCENT = longPreferencesKey("reader_accent_argb")

    fun flow(context: Context): Flow<ThemeConfig> =
        context.applicationContext.themeDataStore.data.map { prefs ->
            ThemeConfig(
                cornerRadiusDp = prefs[KEY_RADIUS] ?: 16,
                glassEnabled = prefs[KEY_GLASS] ?: true,
                uiBackground = prefs[KEY_UI_BG] ?: LumiColorTokens.UI_BACKGROUND,
                uiAccent = prefs[KEY_UI_ACCENT] ?: LumiColorTokens.UI_ACCENT,
                readerBackground = prefs[KEY_READER_BG] ?: LumiColorTokens.READER_BACKGROUND,
                readerAccent = prefs[KEY_READER_ACCENT] ?: LumiColorTokens.READER_ACCENT,
            )
        }

    /** 一次性写入全部 6 维，避免多次 edit 造成中间态闪烁或字段互相覆盖。 */
    suspend fun save(context: Context, config: ThemeConfig) {
        context.applicationContext.themeDataStore.edit {
            it[KEY_RADIUS] = config.cornerRadiusDp.coerceIn(0, 24)
            it[KEY_GLASS] = config.glassEnabled
            it[KEY_UI_BG] = config.uiBackground
            it[KEY_UI_ACCENT] = config.uiAccent
            it[KEY_READER_BG] = config.readerBackground
            it[KEY_READER_ACCENT] = config.readerAccent
        }
    }

    suspend fun setCornerRadius(context: Context, dp: Int) {
        context.applicationContext.themeDataStore.edit {
            it[KEY_RADIUS] = dp.coerceIn(0, 24)
        }
    }

    suspend fun setGlassEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.themeDataStore.edit { it[KEY_GLASS] = enabled }
    }

    suspend fun setUiBackground(context: Context, argb: Long) {
        context.applicationContext.themeDataStore.edit { it[KEY_UI_BG] = argb }
    }

    suspend fun setUiAccent(context: Context, argb: Long) {
        context.applicationContext.themeDataStore.edit { it[KEY_UI_ACCENT] = argb }
    }

    suspend fun setReaderBackground(context: Context, argb: Long) {
        context.applicationContext.themeDataStore.edit { it[KEY_READER_BG] = argb }
    }

    suspend fun setReaderAccent(context: Context, argb: Long) {
        context.applicationContext.themeDataStore.edit { it[KEY_READER_ACCENT] = argb }
    }
}
