/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    kotlin("jvm")
    `maven-publish`
    `publishing-conventions`
    `tests-with-compiler`
}

tasks.withType<Jar>().named(kotlin.target.artifactsTaskName).configure {
    manifest.attributes(
        "Premain-Class" to "org.jetbrains.compose.reload.agent.ComposeHotReloadAgent",
        "Can-Redefine-Classes" to "true",
        "Can-Retransform-Classes" to "true",
    )
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}

dependencies {
    implementation(project(":hot-reload-core"))
    implementation(project(":hot-reload-orchestration"))
    implementation(project(":hot-reload-analysis"))

    implementation(deps.slf4j.api)
    implementation(deps.javassist)

    testImplementation(testFixtures(project(":hot-reload-analysis")))
    testImplementation(deps.logback)
}

publishing {
    publications.create("maven", MavenPublication::class) {
        from(components["java"])
    }
}
