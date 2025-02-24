/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
    `publishing-conventions`
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        this.artifactId = "test-gradle-plugin"
    }
}

gradlePlugin {
    plugins.create("hot-reload-test") {
        id = "org.jetbrains.compose.hot-reload.test"
        implementationClass = "org.jetbrains.compose.reload.test.HotReloadTestPlugin"
    }
}

dependencies {
    compileOnly(gradleApi())
    compileOnly(gradleKotlinDsl())
    compileOnly(kotlin("gradle-plugin"))
    compileOnly(deps.compose.gradlePlugin)
    implementation(project(":hot-reload-gradle-core"))
    implementation(project(":hot-reload-core"))
    implementation(project(":hot-reload-orchestration"))
    implementation(deps.asm)
    implementation(deps.asm.tree)
    implementation(deps.logback)
}
