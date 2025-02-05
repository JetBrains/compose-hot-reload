/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    kotlin("jvm")
    `maven-publish`
    `publishing-conventions`
    `tests-with-compiler`
    `java-test-fixtures`
}

kotlin.compilerOptions {
    optIn.add("org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi")
}

dependencies {
    implementation(project(":hot-reload-core"))

    implementation(deps.slf4j.api)
    implementation(deps.asm)
    implementation(deps.asm.tree)

    testImplementation(deps.logback)

    testFixturesImplementation(kotlin("test"))
    testFixturesImplementation(deps.junit.jupiter)
    testFixturesImplementation(project(":hot-reload-core"))
    testFixturesImplementation(project(":hot-reload-analysis"))
}

publishing {
    publications.create("maven", MavenPublication::class) {
        from(components["java"])
    }
}
