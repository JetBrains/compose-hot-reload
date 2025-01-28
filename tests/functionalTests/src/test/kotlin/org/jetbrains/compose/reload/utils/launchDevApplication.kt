package org.jetbrains.compose.reload.utils

import kotlinx.coroutines.launch

suspend fun HotReloadTestFixture.launchDevApplicationAndWait(
    projectPath: String = ":",
    className: String,
    funName: String
) = runTransaction { launchDevApplicationAndWait(projectPath, className, funName) }

fun HotReloadTestFixture.launchDevApplication(
    projectPath: String = ":",
    className: String,
    funName: String
) {
    daemonTestScope.launch {
        val runTaskPath = buildString {
            if (projectPath != ":") {
                append(projectPath)
            }

            append(":")
            append("devRun")
        }

        gradleRunner.build(runTaskPath, "-DclassName=$className", "-DfunName=$funName")
    }
}
