/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    id("com.android.application")
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.compose.hot-reload")
}

compose.desktop {
    application {
        mainClass = "MainKt"
    }
}

kotlin {
    jvm()
    androidTarget()
    jvmToolchain(25)

    sourceSets.commonMain.dependencies {
        implementation("io.sellmair:evas:1.2.0")
        implementation("io.sellmair:evas-compose:1.2.0")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        implementation(compose.foundation)
        implementation(compose.material3)

        implementation(project(":widgets"))
    }

    sourceSets.jvmMain.dependencies {
        implementation("ch.qos.logback:logback-classic:1.5.9")
        implementation(compose.desktop.currentOs)
    }

    sourceSets.androidMain.dependencies {
        implementation("androidx.activity:activity-compose:1.9.3")
    }
}

android {
    compileSdk = 35
    namespace = "org.jetbrains.compose.reload.sample.counter"

    defaultConfig {
        applicationId = "org.jetbrains.compose.reload.sample.counter"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
