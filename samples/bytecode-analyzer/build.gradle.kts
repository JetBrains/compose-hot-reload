/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.compose.hot-reload")
}

kotlin {
    jvm()
    jvmToolchain(21)

    composeCompiler {
        featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)
    }

    sourceSets.commonMain.dependencies {
        implementation("org.jetbrains.compose.hot-reload:core:1.0.0-alpha05")
        implementation("org.jetbrains.compose.hot-reload:analysis:1.0.0-alpha05")

        implementation("io.sellmair:evas:1.2.0")
        implementation("io.sellmair:evas-compose:1.2.0")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.0")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.0")
        implementation("ch.qos.logback:logback-classic:1.5.9")
        implementation(compose.desktop.currentOs)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.components.resources)
    }
}

tasks.withType<JavaExec> {
    workingDir(layout.buildDirectory.dir("classes/kotlin/jvm/main"))
}
