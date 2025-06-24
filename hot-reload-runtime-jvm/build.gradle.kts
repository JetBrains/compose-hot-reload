/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalComposeLibrary::class)

import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.reload.gradle.HotReloadUsage
import org.jetbrains.compose.reload.gradle.HotReloadUsageType

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    `maven-publish`
    `publishing-conventions`
    `api-validation-conventions`
}

kotlin {
    compilerOptions {
        explicitApi()
    }
}

configurations.runtimeElements.configure {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(HotReloadUsage.COMPOSE_DEV_RUNTIME_USAGE))
        attribute(HotReloadUsageType.attribute, HotReloadUsageType.Dev)
    }
}

dependencies {
    compileOnly(compose.runtime)
    compileOnly(compose.desktop.common)

    compileOnly(project(":hot-reload-core"))
    compileOnly(project(":hot-reload-agent"))
    compileOnly(project(":hot-reload-orchestration"))

    testImplementation(kotlin("test"))
    testImplementation(deps.junit.jupiter)
    testImplementation(deps.junit.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
