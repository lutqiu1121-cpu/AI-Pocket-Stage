package com.lutqiu.lumiread.data

/**
 * 全局 AI 配置（v0.4.0）。
 *
 * 按产品要求：API Key 不再暴露在设置页，统一在此处硬编码。
 * 发布前把 [ZHIPU_API_KEY] 换成真实 Key 即可，其余调用方（AiSceneService）无需改动。
 *
 * 离线验收：把 Key 临时设为 `"MOCK"`，AiSceneService 会走本地 Mock 分支，不发网络请求。
 */
object ApiConfig {
    const val ZHIPU_API_KEY = "请填入真实Key"
    const val ZHIPU_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
    const val ZHIPU_MODEL = "glm-4-flash"
}
