/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    kotlin("jvm")
    `maven-publish`
    `publishing-conventions`
    `java-test-fixtures`
}

kotlin {
    compilerOptions {
        explicitApi()
    }
}

dependencies {
    implementation(project(":hot-reload-core"))

    implementation(deps.slf4j.api)
    compileOnly(deps.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(deps.junit.jupiter)
    testImplementation(deps.junit.jupiter.engine)
    testImplementation(deps.coroutines.test)
    testImplementation(deps.logback)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
    }
}

publishing {
    publications.create("maven", MavenPublication::class) {
        from(components["java"])
    }
}
