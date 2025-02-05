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
    id("org.jetbrains.compose")
    `maven-publish`
    `publishing-conventions`
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        this.jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

dependencies {
    implementation(compose.runtime)

    implementation(project(":hot-reload-core"))
    implementation(project(":hot-reload-orchestration"))

    implementation(compose.desktop.common)
    implementation(compose.material3)
    implementation(compose.components.resources)
    implementation(deps.coroutines.swing)
    implementation(deps.logback)
    implementation(deps.kotlinxDatetime)

    implementation(deps.evas)
    implementation(deps.evas.compose)

    testImplementation(kotlin("test"))
    testImplementation(deps.junit.jupiter)
    testImplementation(deps.junit.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

publishing {
    publications.create("maven", MavenPublication::class) {
        from(components["java"])
    }
}
