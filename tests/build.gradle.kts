/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalComposeLibrary::class)

import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")

    id("org.jetbrains.compose")
    id("org.jetbrains.compose.hot-reload")
    id("org.jetbrains.compose.hot-reload.test")
    `bootstrap-conventions`
    `test-conventions`
}

kotlin {
    compilerOptions.optIn.add("org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi")
}

tasks.withType<AbstractTestTask> {
    dependsOn(":publishLocally")

    /* As both tests start many orchestration servers, we shall relax to await those tests */
    mustRunAfter(":hot-reload-orchestration:test")

    outputs.upToDateWhen { false }

    testLogging {
        events = setOf(
            TestLogEvent.STARTED,
            TestLogEvent.PASSED,
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_OUT,
            TestLogEvent.STANDARD_ERROR,
        )
    }
}

tasks.reloadFunctionalTest.configure {
    providers.environmentVariable("TESTED_BUCKET").orNull?.let { value ->
        environment("TESTED_BUCKET", value)
    }

    providers.environmentVariable("TESTED_BUCKETS_COUNT").orNull?.let { value ->
        environment("TESTED_BUCKETS_COUNT", value)
    }

    useJUnitPlatform {
        if (providers.gradleProperty("host-integration-tests").orNull == "true") {
            includeTags("HostIntegrationTest")
            systemProperty("hostIntegrationTests", "true")
        }
    }
}

kotlin.target.compilations.getByName("reloadFunctionalTestWarmup")
    .associateWith(kotlin.target.compilations.getByName("reloadFunctionalTest"))

dependencies {
    implementation(project(":hot-reload-runtime-api"))
    implementation(project(":hot-reload-test"))
    implementation(project(":hot-reload-core"))
    implementation(project(":hot-reload-orchestration"))
    implementation(kotlin("test"))
    implementation(compose.runtime)
    implementation(deps.kotlinxSerialization.json)

    reloadUnitTestImplementation(compose.uiTest)

    reloadFunctionalTestImplementation(kotlin("test"))
    reloadFunctionalTestImplementation(deps.junit.jupiter)
    reloadFunctionalTestImplementation(deps.junit.jupiter.engine)
    reloadFunctionalTestImplementation(deps.asm)
    reloadFunctionalTestImplementation(deps.asm.tree)

    reloadFunctionalTestImplementation(testFixtures(project(":hot-reload-core")))

    reloadFunctionalTestImplementation(project(":hot-reload-gradle-plugin")) {
        isTransitive = false
    }

    reloadFunctionalTestImplementation(project(":hot-reload-gradle-core")) {
        isTransitive = false
    }
}
