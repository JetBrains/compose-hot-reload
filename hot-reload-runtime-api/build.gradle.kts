/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.library")
    `maven-publish`
    `publishing-conventions`
    `api-validation-conventions`
}

kotlin {
    compilerOptions {
        explicitApi()
    }

    applyDefaultHierarchyTemplate {
        common {
            group("noop") {
                withCompilations { it !is KotlinJvmCompilation }
            }
        }
    }

    jvm()

    androidTarget {
        publishLibraryVariants("release")
    }

    macosArm64()
    macosX64()

    linuxX64()
    linuxArm64()

    iosSimulatorArm64()
    iosArm64()
    iosX64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs()

    js()

    sourceSets.commonMain.dependencies {
        implementation(compose.runtime)
        implementation(deps.coroutines.core)
    }

    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }

    sourceSets.jvmMain.dependencies {
        implementation(project(":hot-reload-runtime-jvm"))
    }

    sourceSets.jvmTest.dependencies {
        implementation(deps.junit.jupiter)
        implementation(deps.junit.jupiter.engine)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<KotlinNativeTest>().configureEach {
    enabled = false
}

tasks.withType<KotlinJsTest>().configureEach {
    enabled = false
}

android {
    compileSdk = 34
    namespace = "org.jetbrains.compose.reload"

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        this.sourceCompatibility = JavaVersion.VERSION_11
        this.targetCompatibility = JavaVersion.VERSION_11
    }
}
