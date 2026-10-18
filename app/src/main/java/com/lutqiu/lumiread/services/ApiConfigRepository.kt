package com.lutqiu.lumiread.services

import android.content.Context

/**
 * API 配置读取入口（v0.4.0 起极简化）。
 *
 * Key 不再由用户在设置页输入，也不落 SharedPreferences：
 * 一律读取 [ApiConfig] 里的编译期常量。保留 Context 形参只为让调用方签名稳定。
 */
object ApiConfigRepository {

    /** 编译期内置的智谱 Key；为空串表示未配置。 */
    fun getZhipuKey(@Suppress("UNUSED_PARAMETER") context: Context): String =
        ApiConfig.ZHIPU_API_KEY.trim()

    fun hasZhipuKey(context: Context): Boolean = getZhipuKey(context).isNotBlank()

    /** 本地 Mock 模式（不发网络请求，用于离线 UI 验收）。 */
    fun isMock(context: Context): Boolean = getZhipuKey(context) == "MOCK"
}
