/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.await
import kotlinx.coroutines.job
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.await
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.withAsyncTrace
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Application
import org.jetbrains.compose.reload.orchestration.OrchestrationConnectionsState
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.sendAsync
import org.jetbrains.compose.reload.orchestration.startOrchestrationListener
import org.jetbrains.compose.reload.orchestration.toJvmArg
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.assertSuccessful
import org.jetbrains.compose.reload.test.gradle.buildFlow
import org.jetbrains.compose.reload.test.gradle.getDefaultMainKtSourceFile
import org.jetbrains.compose.reload.utils.QuickTest
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

class OrchestrationListenerIntegrationTest {

    private val logger = createLogger()

    @HotReloadTest
    @QuickTest
    fun `test - connection`(fixture: HotReloadTestFixture) = fixture.runTest {
        val orchestrationListener = startOrchestrationListener(OrchestrationClientRole.Unknown)
        currentCoroutineContext().job.invokeOnCompletion {
            orchestrationListener.close()
        }

        fixture.projectDir.resolve(fixture.getDefaultMainKtSourceFile()).createParentDirectories().writeText(
            """
                import org.jetbrains.compose.reload.test.*
                
                fun main() {
                    screenshotTestApplication {
                        TestText("Hello")
                    }
                }
            """.trimIndent()
        )

        fixture.launchTestDaemon {

            /* Clear arguments from the Gradle runner, to allow the application to host the server */
            val runner = fixture.gradleRunner.copy(
                arguments = listOf("-D${HotReloadProperty.IsHeadless.key}=true")
            )

            runner.buildFlow(
                ":hotRunJvm", "--mainClass", "MainKt", orchestrationListener.toJvmArg()
            ).toList().assertSuccessful()
        }

        val client = orchestrationListener.connections.receive().getOrThrow()
        currentCoroutineContext().job.invokeOnCompletion {
            client.close()
        }

        logger.info("Waiting for client connection")
        withAsyncTrace("Await deferred client connection") {
            client.states.get(OrchestrationConnectionsState).await { state ->
                client.clientId in state.connections.map { it.clientId }
            }
        }

        logger.info("Waiting for application to connect")
        val application = withAsyncTrace("Await application to connect") {
            client.states.get(OrchestrationConnectionsState).await { state ->
                Application in state.connections.map { it.clientRole }
            }.connections.first { it.clientRole == Application }
        }

        val applicationProcessHandle = ProcessHandle.of(application.clientPid!!).get()
        currentCoroutineContext().job.invokeOnCompletion {
            applicationProcessHandle.destroy()
        }

        logger.info("Sending shutdown request to the application")
        client.sendAsync(OrchestrationMessage.ShutdownRequest("Requested by test"))

        logger.info("Waiting for the application to exit")
        applicationProcessHandle.onExit().await()

        logger.info("Application has exited")
    }
}
