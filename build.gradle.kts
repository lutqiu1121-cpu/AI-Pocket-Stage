// 底座：Marlon0066/AI-Pocket-Chat（纯原生 Kotlin/Compose）。
// AGP 9 自带 Kotlin（built-in Kotlin）：不再 apply org.jetbrains.kotlin.android，
// 只用官方 buildscript 机制把 built-in KGP 抬到 catalog 锁定的版本。
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
