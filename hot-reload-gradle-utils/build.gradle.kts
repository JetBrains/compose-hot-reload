/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    kotlin("jvm")
    `maven-publish`
    `publishing-conventions`
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly(gradleApi())
    compileOnly(gradleKotlinDsl())
    compileOnly(kotlin("gradle-plugin"))
    compileOnly(deps.compose.gradlePlugin)
    compileOnly(deps.compose.compiler.gradlePlugin)
    implementation(project(":hot-reload-core"))
}

publishing {
    publications.create("maven", MavenPublication::class) {
        from(components["java"])
    }
}
