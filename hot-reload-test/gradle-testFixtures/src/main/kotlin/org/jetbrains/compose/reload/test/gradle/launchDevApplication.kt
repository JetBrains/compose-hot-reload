package org.jetbrains.compose.reload.test.gradle

import kotlinx.coroutines.launch
import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi

@InternalHotReloadTestApi
public suspend fun HotReloadTestFixture.launchDevApplicationAndWait(
    projectPath: String = ":",
    className: String,
    funName: String
) = runTransaction { launchDevApplicationAndWait(projectPath, className, funName) }

@InternalHotReloadTestApi
public fun HotReloadTestFixture.launchDevApplication(
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
