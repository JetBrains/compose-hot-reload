/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    kotlin("android")
    build.publish
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        optIn.add("org.jetbrains.compose.reload.InternalHotReloadApi")
    }
}

dependencies {
    compileOnly(project(":hot-reload-annotations"))
    implementation(project(":hot-reload-core"))
    implementation(project(":hot-reload-orchestration"))
    implementation(project(":hot-reload-runtime-shared"))
    implementation(deps.coroutines.core)
}

android {
    compileSdk = 35
    namespace = "org.jetbrains.compose.reload.runtime.android"
    // NDK used to build the native JVMTI agent.
    ndkVersion = "27.0.12077973"

    defaultConfig {
        // JVMTI attach (Debug.attachJvmtiAgent) requires API 28+.
        // TODO: use composeReloadHotReloadAndroidRuntimeMinSdk when bootstrap is bumped
        minSdk = 28
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        // The NDK does not ship jvmti.h.
        // Take it from a JDK toolchain at build time. The JVMTI 1.2 API is used.
        externalNativeBuild {
            cmake {
                val jdkHome = javaToolchains.compilerFor {
                    languageVersion.set(JavaLanguageVersion.of(21))
                }.get().metadata.installationPath.asFile
                arguments += "-DJDK_INCLUDE_DIR=${jdkHome.resolve("include").invariantSeparatorsPath}"
                /* Align the agent to 16 KB pages (NDK r27): 4 KB-aligned ARM64 libraries do not load
                   on 16 KB-page devices. 4 KB-page devices load 16 KB-aligned libraries fine. */
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}
