/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    kotlin("jvm")
    `maven-publish`
    `publishing-conventions`
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(project(":hot-reload-core"))
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "test-core"
    }
}
