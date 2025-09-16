/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalComposeLibrary::class)

import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    kotlin("jvm")
    `maven-publish`
    `publishing-conventions`
    `api-validation-conventions`
    org.jetbrains.compose
    org.jetbrains.kotlin.plugin.compose
}

kotlin {
    compilerOptions {
        explicitApi()
    }
}

dependencies {
    api(project(":hot-reload-core"))
    api(project(":hot-reload-orchestration"))
    compileOnly(deps.compose.runtime)
    compileOnly(deps.compose.ui)

    testCompileOnly(deps.compose.runtime)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
