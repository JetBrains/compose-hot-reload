/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    kotlin("jvm")
    `maven-publish`
    `kotlin-conventions`
    `publishing-conventions`
}

kotlin {
    explicitApi()

    compilerOptions {
        optIn.add("org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi")
    }
}

dependencies {
    implementation(project(":hot-reload-core"))
    implementation(project(":hot-reload-test:core"))
    implementation(project(":hot-reload-orchestration"))
    api(kotlin("test"))
    api(kotlin("tooling-core"))
    api(deps.junit.jupiter)
    api(deps.coroutines.test)
    implementation(deps.junit.jupiter.engine)
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "test-gradle"
    }
}
