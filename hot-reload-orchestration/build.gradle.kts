@file:OptIn(InternalHotReloadApi::class)

import org.jetbrains.compose.reload.InternalHotReloadApi

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    kotlin("jvm")
    `java-test-fixtures`
    org.jetbrains.kotlinx.benchmark
    build.publish
    build.apiValidation
    build.orchestrationCompatibilityTests
}

kotlin {
    explicitApi()
}

benchmark {
    targets.create("test")
}

dependencies {
    implementation(project(":hot-reload-core"))

    compileOnly(deps.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(deps.coroutines.test)
    testImplementation(deps.asm)
    testImplementation(deps.asm.tree)
    testImplementation(project(":hot-reload-analysis"))
    testImplementation(kotlin("reflect"))
    testImplementation(deps.benchmark.runtime)
    testImplementation(deps.lincheck)

    compatibilityTestImplementation(deps.coroutines.test)
}
