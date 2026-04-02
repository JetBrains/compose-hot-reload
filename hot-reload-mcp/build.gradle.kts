/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    kotlin("jvm")
    `bootstrap-conventions`
    build.publish
}

dependencies {
    implementation(project(":hot-reload-core"))
    implementation(project(":hot-reload-orchestration"))

    implementation(deps.mcp.kotlin.sdk.server)

    testImplementation(kotlin("test"))
    testImplementation(deps.coroutines.test)
    testImplementation(deps.mcp.kotlin.sdk.client)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
