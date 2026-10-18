package com.lutqiu.lumiread.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 全局 App 主题模式。 */
enum class AppThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

/** 双层主题：全局 App 主题 + 阅读器独立主题。 */
data class ThemeConfig(
    val appTheme: AppThemeMode = AppThemeMode.SYSTEM,
    val readerTheme: ReaderTheme = ReaderTheme.PAPER,
)

private val Context.themeDataStore by preferencesDataStore(name = "lumiread_theme")

/**
 * 主题持久化（DataStore Preferences）。
 *
 * 全局主题与阅读器主题分别存储、互不覆盖 —— 切换 App 深色不会影响阅读器米黄主题。
 */
object ThemeRepository {

    private val KEY_APP = stringPreferencesKey("app_theme")
    private val KEY_READER = stringPreferencesKey("reader_theme")

    fun flow(context: Context): Flow<ThemeConfig> =
        context.applicationContext.themeDataStore.data.map { prefs ->
            ThemeConfig(
                appTheme = prefs[KEY_APP]
                    ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                    ?: AppThemeMode.SYSTEM,
                readerTheme = prefs[KEY_READER]
                    ?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() }
                    ?: ReaderTheme.PAPER,
            )
        }

    suspend fun setAppTheme(context: Context, mode: AppThemeMode) {
        context.applicationContext.themeDataStore.edit { it[KEY_APP] = mode.name }
    }

    suspend fun setReaderTheme(context: Context, theme: ReaderTheme) {
        context.applicationContext.themeDataStore.edit { it[KEY_READER] = theme.name }
    }
}
