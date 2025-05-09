/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.compose.reload.core.Os
import org.jetbrains.compose.reload.core.createLogger
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists

private val logger = createLogger()

public fun HotReloadTestFixture.launchApplication(
    projectPath: String = ":",
    mainClass: String = "MainKt"
) {
    daemonTestScope.launch {
        val job = currentCoroutineContext().job

        val runTask = when (projectMode) {
            ProjectMode.Kmp -> when (launchMode) {
                ApplicationLaunchMode.GradleBlocking -> "jvmRun"
                ApplicationLaunchMode.Detached -> "jvmRunHotAsync"
            }

            ProjectMode.Jvm -> when (launchMode) {
                ApplicationLaunchMode.GradleBlocking -> "runHot"
                ApplicationLaunchMode.Detached -> "runHotAsync"
            }
        }

        val additionalArguments = buildList {
            add("-DmainClass=$mainClass")

            /* Detached launches will create one more process: We create a pipe file to foward the output */
            if (launchMode == ApplicationLaunchMode.Detached && Os.current() in listOf(Os.Linux, Os.MacOs)) {
                val socketFile = generateSequence(0) { it + 1 }
                    .map { projectDir.resolve("$it.sock") }
                    .first { !it.exists() }

                ProcessBuilder("mkfifo", socketFile.toString())
                    .inheritIO().start().waitFor(5, TimeUnit.SECONDS)

                add("--stdout")
                add(socketFile.absolutePathString())

                add("--stderr")
                add(socketFile.absolutePathString())

                val readerThread = thread(isDaemon = true, name = "App Output Reader") {
                    try {
                        socketFile.bufferedReader().forEachLine { line ->
                            logger.info(line)
                        }
                    } finally {
                        logger.info("App Output Reader finished")
                    }
                }

                job.invokeOnCompletion {
                    readerThread.interrupt()
                }
            }
        }


        val runTaskPath = buildString {
            if (projectPath != ":") {
                append(projectPath)
            }

            append(":")
            append(runTask)
        }

        val result = gradleRunner.buildFlow(runTaskPath, *additionalArguments.toTypedArray()).toList()
        result.assertSuccessful()

        if (launchMode == ApplicationLaunchMode.Detached) {
            awaitCancellation()
        }
    }
}
