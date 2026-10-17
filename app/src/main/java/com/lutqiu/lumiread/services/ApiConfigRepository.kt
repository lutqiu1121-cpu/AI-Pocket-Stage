package com.lutqiu.lumiread.services

import android.content.Context
import androidx.core.content.edit

/**
 * API 配置仓库（v0.3.0）。
 *
 * 目前只存智谱 API Key。用 SharedPreferences 明文落盘 —— 本地单机、无账号体系；
 * 后续接多 provider 时再换 EncryptedSharedPreferences（需要引 security-crypto）。
 */
object ApiConfigRepository {

    private const val PREFS = "lumiread_api_config"
    private const val KEY_ZHIPU = "zhipu_api_key"

    /** 智谱开放平台 Key，形如 `xxxxxxxx.yyyyyyyy`；空串 = 未配置。 */
    fun getZhipuKey(context: Context): String =
        prefs(context).getString(KEY_ZHIPU, "").orEmpty()

    fun setZhipuKey(context: Context, key: String) {
        prefs(context).edit { putString(KEY_ZHIPU, key.trim()) }
    }

    fun hasZhipuKey(context: Context): Boolean = getZhipuKey(context).isNotBlank()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
