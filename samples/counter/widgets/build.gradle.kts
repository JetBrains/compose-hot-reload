/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.compose.hot-reload")
}

kotlin {
    jvmToolchain(21)
    jvm()
    androidTarget()

    sourceSets.commonMain.dependencies {
        implementation("io.sellmair:evas:1.2.0")
        implementation("io.sellmair:evas-compose:1.2.0")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
        implementation(compose.foundation)
        implementation(compose.material3)
    }

    sourceSets.jvmMain.dependencies {
        implementation(compose.desktop.currentOs)
    }
}

android {
    compileSdk = 35
    namespace = "org.jetbrains.compose.reload.sample.widgets"

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
