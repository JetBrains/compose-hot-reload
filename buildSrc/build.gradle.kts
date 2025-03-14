/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */


plugins {
    `kotlin-dsl`
}

repositories {
    maven(file("../build/repo"))

    maven("https://packages.jetbrains.team/maven/p/firework/dev") {
        mavenContent {
            includeModuleByRegex("org.jetbrains.compose", "hot-reload.*")
        }
    }

    gradlePluginPortal {
        content {
            includeModuleByRegex("org.jetbrains.kotlinx", "kotlinx-benchmark-plugin")
        }
    }

    google {
        mavenContent {
            includeGroupByRegex(".*google.*")
            includeGroupByRegex(".*android.*")
        }
    }


    mavenCentral()
}


dependencies {
    implementation(kotlin("gradle-plugin:${deps.versions.kotlin.get()}"))
    implementation("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:${deps.versions.kotlin.get()}")
    implementation("org.jetbrains.compose:org.jetbrains.compose.gradle.plugin:${deps.versions.compose.get()}")
    implementation("com.android.tools.build:gradle:${deps.versions.androidGradlePlugin.get()}")
    implementation(deps.benchmark.gradlePlugin)
    implementation(deps.binaryCompatibilityValidator.gradlePlugin)
    implementation(deps.shadow.gradlePlugin)
    implementation(deps.kotlinxSerialization.json)

    implementation(deps.ktor.client.core)
    implementation(deps.ktor.client.cio)
    implementation(deps.asm)
    implementation(deps.asm.tree)
}
