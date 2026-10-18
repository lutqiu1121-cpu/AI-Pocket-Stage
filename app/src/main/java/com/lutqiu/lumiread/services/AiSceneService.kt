package com.lutqiu.lumiread.services

import com.lutqiu.lumiread.data.ApiConfig

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import com.lutqiu.lumiread.ui.theme.LumiColors
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** AI 场景结果。 */
data class AiScene(
    val changed: Boolean,
    val description: String,
    val prompt: String,
    val imagePath: String?,
)

/**
 * 真实 AI 链路：智谱 GLM 语义分析 → Pollinations 生图。
 *
 * 所有网络请求都经过 [RequestQueue]（全局单队列 / 并发 1 / 任务间 3s），
 * 因此本类内部不再做并发控制。
 *
 * Key 填 `MOCK` 时走本地 Mock 分支（不联网），方便没有 Key 的设备做闭环验收。
 */
object AiSceneService {

    private const val TAG = "AiSceneService"

    private val ZHIPU_URL = ApiConfig.ZHIPU_BASE_URL.trimEnd('/') + "/chat/completions"
    private const val POLLINATIONS_URL = "https://image.pollinations.ai/prompt/"

    private const val READ_TIMEOUT_S = 60L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private const val SYSTEM_PROMPT =
        "你是视觉小说导演。根据给定的上下文判断场景是否变化，并输出严格 JSON。" +
            "格式：{\"sceneChanged\":true|false,\"sceneDesc\":\"一句中文场景描述\",\"imagePrompt\":\"English image prompt\"}。" +
            "不要输出任何额外文字、不要加 markdown 代码块。"

    /**
     * 长按 → 语义分析。
     *
     * @throws java.io.IOException 网络异常
     * @throws HttpStatusException 401 / 429 等
     */
    suspend fun analyze(context: Context, contextText: String): AiScene {
        val key = ApiConfigRepository.getZhipuKey(context)

        if (key == "MOCK") {
            delay(1200)
            return AiScene(
                changed = true,
                description = "雨夜的旧书店，暖黄灯光落在积灰的书脊上，门口的风铃刚停。",
                prompt = "cozy old bookstore at rainy night, warm lamplight, anime style, cinematic",
                imagePath = null,
            )
        }
        if (key.isBlank() || key == "请填入真实Key") {
            throw HttpStatusException(401, "请先在 ApiConfig.kt 填入真实智谱 API Key")
        }

        val userPrompt = "以下是小说上下文，请判断并给出场景：\n$contextText"
        val body = buildString {
            append("""{"model":""").append(jsonString(ApiConfig.ZHIPU_MODEL))
                .append(""","temperature":0.7,"messages":[""")
            append("""{"role":"system","content":""").append(jsonString(SYSTEM_PROMPT)).append("},")
            append("""{"role":"user","content":""").append(jsonString(userPrompt)).append("}")
            append("]}")
        }

        val request = Request.Builder()
            .url(ZHIPU_URL)
            .addHeader("Authorization", "Bearer $key")
            .post(body.toRequestBody(JSON_TYPE))
            .build()

        val raw = client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.w(TAG, "智谱 HTTP ${response.code}: $text")
                throw HttpStatusException(response.code, "智谱接口返回 HTTP ${response.code}")
            }
            text
        }

        val content = extractMessageContent(raw)
        val sceneJson = parseSceneJson(content)

        return AiScene(
            changed = sceneJson.changed,
            description = sceneJson.description,
            prompt = sceneJson.imagePrompt,
            imagePath = null,
        )
    }

    /** Pollinations 生图并落盘；返回本地文件路径。失败抛异常。 */
    suspend fun generateImage(context: Context, prompt: String, seed: Long): String? {
        val key = ApiConfigRepository.getZhipuKey(context)
        if (key == "MOCK") {
            delay(1500)
            return writeMockImage(context)
        }

        val encoded = URLEncoder.encode(prompt, "UTF-8")
        // v0.4.0：VN 画面按 1:1 出图，卡片区再 Crop，保证构图居中不拉伸。
        val url = "$POLLINATIONS_URL$encoded?width=1024&height=1024&seed=$seed&nologo=true&model=flux"

        val request = Request.Builder().url(url).get().build()
        val bytes = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpStatusException(response.code, "生图接口返回 HTTP ${response.code}")
            }
            response.body?.bytes()
        } ?: throw IllegalStateException("生图返回空内容")

        val dir = File(context.filesDir, "scenes").apply { mkdirs() }
        val file = File(dir, "scene_${System.currentTimeMillis()}.jpg")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    // ── 解析 ─────────────────────────────────────────────────────────────

    private fun extractMessageContent(raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: throw IllegalStateException("智谱返回不是合法 JSON")
        val content = root["choices"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")
            ?.jsonObject?.get("content")
            ?.jsonPrimitive?.content
        return content?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("智谱返回里没有 choices[0].message.content")
    }

    private data class SceneJson(val changed: Boolean, val description: String, val imagePrompt: String)

    private fun parseSceneJson(content: String): SceneJson {
        // 模型偶尔会套 ```json ```，先剥围栏再取第一个 { ... }
        val cleaned = content.replace("```json", " ").replace("```", " ").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) {
            // 模型没按 JSON 回：把整段当描述，默认场景已变化
            return SceneJson(true, cleaned.take(200), cleaned.take(300))
        }
        val obj = runCatching {
            json.parseToJsonElement(cleaned.substring(start, end + 1)).jsonObject
        }.getOrNull() ?: return SceneJson(true, cleaned.take(200), cleaned.take(300))

        val changed = obj.booleanOr("sceneChanged", true)
        val desc = obj.stringOr("sceneDesc").ifBlank { "场景变化" }
        val prompt = obj.stringOr("imagePrompt").ifBlank { desc }
        return SceneJson(changed, desc, prompt)
    }

    private fun JsonObject.booleanOr(key: String, fallback: Boolean): Boolean =
        runCatching { this[key]?.jsonPrimitive?.content?.toBooleanStrict() }.getOrNull() ?: fallback

    private fun JsonObject.stringOr(key: String): String =
        runCatching { this[key]?.jsonPrimitive?.content.orEmpty() }.getOrDefault("")

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }

    // ── Mock 图 ──────────────────────────────────────────────────────────

    private fun writeMockImage(context: Context): String {
        val width = 1024
        val height = 576
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(LumiColors.MockGradientStart.toArgb(), LumiColors.MockGradientEnd.toArgb()),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LumiColors.White.toArgb()
            textSize = 56f
        }
        canvas.drawText("MOCK SCENE", 64f, height - 96f, textPaint)

        val dir = File(context.filesDir, "scenes").apply { mkdirs() }
        val file = File(dir, "mock_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return file.absolutePath
    }
}

/** 带 HTTP 状态码的异常，UI 据此给出 401 / 429 的明确提示。 */
class HttpStatusException(val code: Int, message: String) : Exception(message)
