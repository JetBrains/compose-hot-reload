/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.compose.reload.test.HotReloadUnitTestTask

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.compose.hot-reload")
    id("org.jetbrains.compose.hot-reload.test")
}

kotlin {
    jvm()

    sourceSets.jvmMain.dependencies {
        implementation("org.jetbrains.compose.hot-reload:runtime-api:1.0.0-alpha05")
        implementation(compose.runtime)
    }

    sourceSets.getByName("jvmReloadUnitTest").dependencies {
        implementation(kotlin("test"))
        implementation(deps.coroutines.test)
        implementation("ch.qos.logback:logback-classic:1.5.16")
    }
}

tasks.withType<HotReloadUnitTestTask>().configureEach {
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
    outputs.upToDateWhen { false }
}
