/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.destroyWithDescendants
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Application
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import org.jetbrains.compose.reload.test.gradle.ApplicationLaunchMode
import org.jetbrains.compose.reload.test.gradle.GradleRunner
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.TestedLaunchMode
import org.jetbrains.compose.reload.test.gradle.build
import org.jetbrains.compose.reload.test.gradle.getDefaultMainKtSourceFile
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.HostIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import java.io.IOException
import java.io.InputStream
import kotlin.io.path.createParentDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.fail

class ArgFileTest {
    private val logger = createLogger()

    @HotReloadTest
    @HostIntegrationTest
    @GradleIntegrationTest
    @TestedLaunchMode(ApplicationLaunchMode.Detached)
    @QuickTest
    fun `test - launch application using argfile`(fixture: HotReloadTestFixture) = fixture.runTest {
        val testJob = currentCoroutineContext().job

        fixture.projectDir.resolve(fixture.getDefaultMainKtSourceFile()).createParentDirectories().writeText(
            """
            import org.jetbrains.compose.reload.test.*
            fun main() {
                screenshotTestApplication {
                }
            }
            """.trimIndent()
        )

        val runTaskName = when (projectMode) {
            ProjectMode.Kmp -> "hotRunJvm"
            ProjectMode.Jvm -> "hotRun"
        }

        val argFileName = when (projectMode) {
            ProjectMode.Kmp -> "jvmMain.argfile"
            ProjectMode.Jvm -> "main.argfile"
        }

        val compilationClassifier = when (projectMode) {
            ProjectMode.Kmp -> "jvmMain"
            ProjectMode.Jvm -> "main"
        }

        assertEquals(GradleRunner.ExitCode.success, fixture.gradleRunner.build("${runTaskName}Argfile"))
        val argFile = fixture.projectDir.resolve("build/run/$compilationClassifier/$argFileName")
        if (!argFile.isRegularFile()) {
            fail("Expected argfile at '$argFile', but it does not exist")
        }

        val (applicationProcess, applicationConnectedMessage) = fixture.runTransaction {
            val applicationProcess = ProcessBuilder(
                ProcessHandle.current().info().command().orElseThrow(),
                "@${argFile.pathString}", "MainKt"
            ).directory(fixture.projectDir.path.toFile()).start()

            testJob.invokeOnCompletion {
                applicationProcess.destroyWithDescendants()
            }

            fixture.launchStreamReaderDaemon(applicationProcess.errorStream) {
                logger.error("Application stderr: $it")
            }

            fixture.launchStreamReaderDaemon(applicationProcess.inputStream) {
                logger.error("Application stdout: $it")
            }

            val applicationConnectedMessage = select {
                /* If the process dies before we get the 'connected' message, we can fail the test */
                applicationProcess.onExit().asDeferred().onAwait {
                    fail("Application process exited with ${applicationProcess.exitValue()}")
                }

                async { skipToMessage<ClientConnected> { it.clientRole == Application } }.onAwait { it }
            }

            applicationProcess to applicationConnectedMessage
        }

        assertEquals(applicationProcess.pid(), applicationConnectedMessage.clientPid)

        runTransaction { skipToMessage<OrchestrationMessage.UIRendered>() }
        orchestration.send(ShutdownRequest("Requested by test"))
        applicationProcess.onExit().asDeferred().await()

        if (applicationProcess.exitValue() !in listOf(0, 134)) fail(
            "Expected the test application to exit with 0 or 134 (SIGTERM), " +
                "but it exited with ${applicationProcess.exitValue()}"
        )

        if (applicationProcess.isAlive) fail("Expected the test application to be terminated")
    }
}


private fun HotReloadTestFixture.launchStreamReaderDaemon(
    stream: InputStream, onLine: (String) -> Unit
) {
    launchTestDaemon(Dispatchers.IO) {
        launch {
            currentCoroutineContext().job.invokeOnCompletion {
                stream.close()
            }
        }

        try {
            stream.bufferedReader().forEachLine(onLine)
        } catch (_: IOException) {
            currentCoroutineContext().job.cancelChildren()
        }
    }
}
