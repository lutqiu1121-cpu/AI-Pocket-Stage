# AI Pocket Stage

原生 Kotlin / Jetpack Compose 互动故事「舞台层」重构工程。

新底座 fork 自 [Marlon0066/AI-Pocket-Chat](https://github.com/Marlon0066/AI-Pocket-Chat)（纯原生 Kotlin/Compose），
本期只保留 **16:9 叙事舞台** 这一件事，以及底座自带的 **Haze 毛玻璃库**。

---

## 第一期边界（很硬）

> 第一期只画皮。不看旧项目代码，不接 API，不发任何网络请求。

| 项 | 第一期 |
| --- | --- |
| 网络请求 | ❌ 无。`AndroidManifest.xml` 里连 `INTERNET` 权限都没声明 |
| API / 鉴权 / 生图 / 队列 | ❌ 全部不在本期范围，也未从旧项目搬运任何代码 |
| 数据层（Room / DataStore / Hilt / WorkManager） | ❌ 已从底座剔除 |
| 状态管理 | ❌ 无 ViewModel、无 StateFlow；页面参数全是常量 |
| UI | ✅ 静态 16:9 舞台骨架（单页，一个 Compose 文件画完） |
| 毛玻璃 | ✅ 使用底座自带 Haze 2.0.0（`dev.chrisbanes.haze` + `haze-glass`） |

## 从底座裁剪掉什么

`AI-Pocket-Chat` 原工程是一套 3000+ Kotlin 文件的完整产品（聊天 / 故事 / 角色 / 朋友圈 / 宠物 / 语音 / 日历 …）。
本期只取「工程骨架 + Haze + Compose 主题能力」，其余全部移除：

- 业务模块：`moments`（朋友圈）、`pet`（宠物）、`gift`、`redpacket`、`meeting`、`ourdays`、`promise`、`economy`、
  `relationship`、`worldbook`、`stt`/`tts`/`voice`（语音）、`sticker`、`notification`、`widget`、`proactive` 等
- 基础设施：`data`（Room）、`network`（OkHttp/SSE）、`di`（Hilt）、`work`（WorkManager）、`offline`、
  `diagnostics`、`security`（EncryptedSharedPreferences）
- 依赖：Hilt / KSP / Room / Datastore / Media3 / CameraX / ZXing / ONNX Runtime / sherpa / OkHttp 全部不引
- 模块：`baselineprofile`（macrobenchmark）不引
- 资源：原工程的字体、贴图、模型资产（assets 内数百 MB）全部不引

保留下来的是：Gradle 工程骨架（AGP 9.4.1 / Kotlin 2.4.20 / Gradle 9.7.1 / version catalog）、
Compose 编译链，以及 Haze 毛玻璃依赖。APK 里现在只有 2 个 Kotlin 文件。

## 工程结构

```
app/src/main/
├── AndroidManifest.xml                 # 零权限；单 Activity
├── java/com/situ/aichat/
│   ├── MainActivity.kt                 # 只做 setContent { StageScreen() }
│   └── ui/stage/StageScreen.kt         # ← 第一期全部 UI 都在这一个文件里
└── res/                                # 主题、字符串、启动图标（无图片资源）
```

## 16:9 舞台布局

外层浅灰画布（`#F7F8FA`）铺满全屏，正中央是 16:9 舞台：

```kotlin
Modifier
    .fillMaxWidth(0.9f)
    .aspectRatio(16f / 9f)
    .shadow(24.dp, RoundedCornerShape(12.dp))
    .clip(RoundedCornerShape(12.dp))
    .background(Color.Black)
```

舞台内部五层：

| 层 | 内容 |
| --- | --- |
| ① 背景占位 | 深灰渐变 Block 铺满，同时作为 Haze 的取景源（`hazeSource`） |
| ② 立绘占位 | 左侧，高 80%，底部对齐，半透明蓝（`#3B82F6` @ 45%） |
| ③ 顶部信息栏 | 左上角胶囊：「第1章 正文 · 场景地点」，距边 16dp |
| ④ 悬浮按钮 | 左中「上一句 / 菜单」、右中「下一句 / 设置」，胶囊按钮，距边 16dp |
| ⑤ 对话框 | 底部，高 150dp，铺满宽度，Haze 毛玻璃，假文字行高 1.6、首行缩进 2 字符 |

## 构建

```bash
./gradlew :app:assembleDebug
```

环境：JDK 17、Android SDK `compileSdk 37` / `buildToolsVersion 37.0.0`。

## 下一期（不在本期）

第二期才谈业务：LLM 接入、生图、全局单队列、超时熔断等。
届时按新的接口契约重写，**不读取、不移植旧 WebView 项目 `txt2webgal-android` 的任何代码**。

## License

继承底座仓库的 LICENSE。
