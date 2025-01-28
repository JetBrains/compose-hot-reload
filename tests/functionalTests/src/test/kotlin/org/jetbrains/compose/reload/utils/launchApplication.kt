package org.jetbrains.compose.reload.utils

import kotlinx.coroutines.launch


fun HotReloadTestFixture.launchApplication(
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
