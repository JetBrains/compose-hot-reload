import java.util.Properties

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */


plugins {
    `kotlin-dsl`
    kotlin("jvm") version deps.versions.kotlin
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.compose.reload.InternalHotReloadApi")
        optIn.add("org.jetbrains.compose.reload.DelicateHotReloadApi")
    }
}

repositories {
    maven(file("../build/bootstrap"))

    maven("https://packages.jetbrains.team/maven/p/firework/dev") {
        mavenContent {
            @Suppress("UnstableApiUsage")
            includeGroupAndSubgroups("org.jetbrains.compose.hot-reload")
        }
    }

    gradlePluginPortal {
        content {
            includeModuleByRegex("org.jetbrains.kotlinx", "kotlinx-benchmark-plugin")
            includeModuleByRegex("com\\.gradle.*", ".*")
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

val bootstrapVersion = providers.fileContents(layout.projectDirectory.file("../gradle.properties")).asBytes
    .map { content ->
        val properties = Properties()
        properties.load(content.inputStream())
        properties.getProperty("bootstrap.version") ?: error("missing 'bootstrap.version'")
    }

dependencies {
    implementation("org.jetbrains.compose.hot-reload:hot-reload-core:${bootstrapVersion.get()}")
    implementation("org.jetbrains.compose.hot-reload:hot-reload-gradle-core:${bootstrapVersion.get()}")
    implementation("org.jetbrains.compose.hot-reload:hot-reload-gradle-plugin:${bootstrapVersion.get()}")
    implementation("org.jetbrains.compose.hot-reload:hot-reload-test-gradle-plugin:${bootstrapVersion.get()}")
    implementation(kotlin("gradle-plugin:${deps.versions.kotlin.get()}"))
    implementation("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:${deps.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:${deps.versions.kotlin.get()}")
    implementation("org.jetbrains.compose:org.jetbrains.compose.gradle.plugin:${deps.versions.compose.get()}")
    implementation("com.android.tools.build:gradle:${deps.versions.androidGradlePlugin.get()}")
    implementation(deps.benchmark.gradlePlugin)
    implementation(deps.binaryCompatibilityValidator.gradlePlugin)
    implementation(deps.shadow.gradlePlugin)
    implementation(deps.pluginPublish.gradlePlugin)

    implementation(deps.kotlinxSerialization.json)
    implementation(deps.kotlinxSerialization.kaml)

    implementation(deps.ktor.client.core)
    implementation(deps.ktor.client.cio)
    implementation(deps.asm)
    implementation(deps.asm.tree)
}
