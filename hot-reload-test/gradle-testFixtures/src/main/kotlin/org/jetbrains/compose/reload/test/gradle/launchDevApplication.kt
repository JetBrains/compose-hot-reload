/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import kotlinx.coroutines.awaitCancellation
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
            append(
                when (launchMode) {
                    ApplicationLaunchMode.GradleBlocking -> "jvmRunDev"
                    ApplicationLaunchMode.Detached -> "jvmRunDevAsync"
                }
            )
        }

        gradleRunner.build(runTaskPath, "--className", className, "--funName", funName).assertSuccess()
        if (launchMode == ApplicationLaunchMode.Detached) {
            awaitCancellation()
        }
    }
}
