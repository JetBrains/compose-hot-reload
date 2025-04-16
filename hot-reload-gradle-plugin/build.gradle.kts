/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
    `publishing-conventions`
    `api-validation-conventions`
}


tasks.withType<Test>().configureEach {
    maxParallelForks = 2
    dependsOn(":publishLocally")
}

gradlePlugin {
    plugins.create("hot-reload") {
        id = "org.jetbrains.compose.hot-reload"
        implementationClass = "org.jetbrains.compose.reload.ComposeHotReloadPlugin"
    }
}

dependencies {
    compileOnly(gradleApi())
    compileOnly(gradleKotlinDsl())
    compileOnly(kotlin("gradle-plugin"))
    compileOnly(deps.compose.gradlePlugin)
    compileOnly(deps.compose.compiler.gradlePlugin)

    implementation(project(":hot-reload-gradle-core"))
    implementation(project(":hot-reload-gradle-idea"))
    implementation(project(":hot-reload-core"))
    implementation(project(":hot-reload-orchestration"))

    testImplementation(kotlin("test"))
    testImplementation(gradleKotlinDsl())
    testImplementation(deps.junit.jupiter)
    testImplementation(deps.junit.jupiter.engine)
    testImplementation(kotlin("gradle-plugin"))
    testImplementation(deps.compose.gradlePlugin)
    testImplementation(deps.kotlinxSerialization.json)
    testImplementation("com.android.tools.build:gradle:8.6.1")
}
