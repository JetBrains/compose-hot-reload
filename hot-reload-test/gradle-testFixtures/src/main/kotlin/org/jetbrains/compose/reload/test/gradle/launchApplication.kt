/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import kotlinx.coroutines.launch

public fun HotReloadTestFixture.launchApplication(
    projectPath: String = ":",
    mainClass: String = "MainKt"
) {
    daemonTestScope.launch {
        val runTask = when (projectMode) {
            ProjectMode.Kmp -> "jvmRun"
            ProjectMode.Jvm -> "run"
        }

        val additionalArguments = when (projectMode) {
            ProjectMode.Kmp -> arrayOf("-DmainClass=$mainClass")
            ProjectMode.Jvm -> arrayOf()
        }

        val runTaskPath = buildString {
            if (projectPath != ":") {
                append(projectPath)
            }

            append(":")
            append(runTask)
        }

        val result = gradleRunner.build(*additionalArguments, runTaskPath)
        if (result != GradleRunner.ExitCode.success) error("Application Failed: $result")
    }
}
