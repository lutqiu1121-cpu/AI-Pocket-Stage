plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.lutqiu.lumiread"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.lutqiu.lumiread"
        minSdk = 29
        targetSdk = 36
        versionCode = 4
        versionName = "0.2.1-alpha"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // RequestQueue 在异常分支会打 android.util.Log；纯 JVM 单测下返回默认值即可，不必引入 Robolectric。
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// 单测要能看到队列串行日志（时间戳由测试自己加），故把测试输出透传到控制台。
tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // 毛玻璃（底座自带 Haze 2.0.0）
    implementation(libs.haze)
    implementation(libs.haze.glass)

    // 第三期：文件解析 / 网络队列 / 图片加载 / 本地序列化
    implementation(libs.epublib.core) {
        // epublib 3.1 的传递依赖里 xmlpull 与 kxml2 各自打包了一份 org.xmlpull.v1.*，
        // 同时进包会 duplicate class；kxml2 已自带同包，故排除独立的 xmlpull。
        exclude(group = "xmlpull")
    }
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
