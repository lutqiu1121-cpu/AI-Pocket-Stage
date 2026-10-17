package com.lutqiu.lumiread.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 本地书架存储（第三期最轻量实现）：把 [Book] 列表序列化成 JSON 落在 filesDir。
 *
 * 选择理由：第三期只要「本地存储能力」到位，不引 Room / KSP（会显著拖慢构建且暂无查询需求）。
 * 第四期接入书架分页 / 全文检索时再迁 Room，届时 [BookStore] 的公开 API 保持不变。
 */
object BookStore {

    private const val TAG = "BookStore"
    private const val FILE_NAME = "library.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(context: Context): List<Book> = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        json.decodeFromString<List<Book>>(file.readText())
    }.onFailure { Log.w(TAG, "读取书架失败，按空书架处理", it) }
        .getOrDefault(emptyList())

    fun save(context: Context, books: List<Book>) {
        runCatching {
            File(context.filesDir, FILE_NAME).writeText(json.encodeToString(books))
        }.onFailure { Log.w(TAG, "写入书架失败", it) }
    }
}
