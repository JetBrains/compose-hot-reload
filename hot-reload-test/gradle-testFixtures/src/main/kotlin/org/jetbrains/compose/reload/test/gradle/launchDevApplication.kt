/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi

@InternalHotReloadTestApi
public suspend fun HotReloadTestFixture.launchDevApplicationAndWait(
    projectPath: String = ":",
    className: String,
    funName: String
): Unit = runTransaction { launchDevApplicationAndWait(projectPath, className, funName) }

@InternalHotReloadTestApi
public fun HotReloadTestFixture.launchDevApplication(
    projectPath: String = ":",
    className: String,
    funName: String
) {
    daemonTestScope.launch {

        val runTask = when (projectMode) {
            ProjectMode.Kmp -> when (launchMode) {
                ApplicationLaunchMode.GradleBlocking -> "jvmRunDev"
                ApplicationLaunchMode.Detached -> "jvmRunDevAsync"
            }

            ProjectMode.Jvm -> when (launchMode) {
                ApplicationLaunchMode.GradleBlocking -> "runDev"
                ApplicationLaunchMode.Detached -> "runDevAsync"
            }
        }

        val runTaskPath = buildString {
            if (projectPath != ":") {
                append(projectPath)
            }

            append(":")
            append(runTask)
        }

        gradleRunner.buildFlow(runTaskPath, "--className", className, "--funName", funName).toList().assertSuccessful()
        if (launchMode == ApplicationLaunchMode.Detached) {
            awaitCancellation()
        }
    }
}
