/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalComposeLibrary::class)

import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose.hot-reload")
    org.jetbrains.compose
    `bootstrap-conventions`
    build.publish
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        this.jvmTarget.set(JvmTarget.JVM_17)
        this.optIn.add("kotlin.time.ExperimentalTime")
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

dependencies {
    implementation(compose.runtime)
    implementation(project(":hot-reload-devtools-api"))

    implementation(project(":hot-reload-core"))
    implementation(project(":hot-reload-orchestration"))
    implementation(project(":hot-reload-runtime-api"))

    implementation(compose.desktop.common)
    implementation(compose.material3)
    implementation(compose.components.resources)
    implementation(deps.compose.icons.core)
    implementation(deps.coroutines.swing)
    implementation(deps.kotlinxDatetime)

    implementation(deps.evas)
    implementation(deps.evas.compose)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
    testImplementation(compose.uiTest)
    testImplementation(compose.desktop.currentOs)

    devCompileOnly(project(":hot-reload-agent"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
