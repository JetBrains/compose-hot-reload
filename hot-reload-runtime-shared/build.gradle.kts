@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

/*
Code shared between the hot reload runtimes ('hot-reload-agent' + 'hot-reload-runtime-jvm' on the
desktop, 'hot-reload-runtime-android' on Android) and consumed by 'hot-reload-runtime-api'.
 */

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    build.publish
}

kotlin {
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withJvm()
                withAndroidTarget()
            }
        }
    }

    jvm()

    androidTarget {
        publishLibraryVariants("release")
    }

    sourceSets.getByName("jvmAndAndroidMain").dependencies {
        compileOnly(project(":hot-reload-annotations"))
        api(project(":hot-reload-core"))
        api(project(":hot-reload-orchestration"))
    }
}

android {
    compileSdk = 34
    namespace = "org.jetbrains.compose.reload.runtime.shared"

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        this.sourceCompatibility = JavaVersion.VERSION_11
        this.targetCompatibility = JavaVersion.VERSION_11
    }
}
