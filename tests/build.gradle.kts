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

    id("org.jetbrains.compose")
    id("org.jetbrains.compose.hot-reload")
    id("org.jetbrains.compose.hot-reload.test")

    `test-conventions`
}

kotlin {
    compilerOptions.optIn.add("org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi")
}

tasks.withType<AbstractTestTask> {
    dependsOn(":publishLocally")
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
    useJUnitPlatform {
        if (providers.gradleProperty("host-integration-tests").orNull == "true") {
            includeTags("HostIntegrationTest")
        }
    }
}

dependencies {
    implementation(project(":hot-reload-runtime-api"))
    implementation(project(":hot-reload-core"))
    implementation(project(":hot-reload-orchestration"))
    implementation(kotlin("test"))
    implementation(deps.slf4j.api)
    implementation(deps.logback)
    implementation(compose.runtime)

    reloadUnitTestImplementation(compose.uiTest)

    reloadFunctionalTestImplementation(kotlin("test"))
    reloadFunctionalTestImplementation(deps.junit.jupiter)
    reloadFunctionalTestImplementation(deps.junit.jupiter.engine)
    reloadFunctionalTestImplementation(testFixtures(project(":hot-reload-core")))
    reloadFunctionalTestImplementation(project(":hot-reload-test:gradle-testFixtures"))
    reloadFunctionalTestImplementation(deps.asm)
    reloadFunctionalTestImplementation(deps.asm.tree)

    /*
     Required to enforce no accidental resolve to the -api-jvm, published by the runtime api project.
     Usually such substitutions are made automatically by Gradle, but there seems to be an issue
     with the sub-publications of the KMP project
     */
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.jetbrains.compose.hot-reload:runtime-api-jvm"))
                .using(project(":hot-reload-runtime-api"))
        }
    }
}
