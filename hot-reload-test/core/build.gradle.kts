/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    kotlin("jvm")
    build.publish
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(project(":hot-reload-core"))
}

publishingConventions {
    artifactId = "hot-reload-test-core"
}
