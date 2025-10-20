/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.await
import kotlinx.coroutines.job
import org.jetbrains.compose.reload.core.await
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.withAsyncTrace
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Application
import org.jetbrains.compose.reload.orchestration.OrchestrationConnectionsState
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.orchestration.asChannel
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
            val runner = fixture.gradleRunner.copy(arguments = emptyList())
            runner.buildFlow(
                ":hotRunJvm", "--mainClass", "MainKt", orchestrationListener.toJvmArg()
            ).toList().assertSuccessful()
        }

        val client = orchestrationListener.connections.receive().getOrThrow()
        currentCoroutineContext().job.invokeOnCompletion {
            client.close()
        }

        val messages = client.asChannel()

        withAsyncTrace("Await deferred client connection") {
            client.states.get(OrchestrationConnectionsState).await { state ->
                client.clientId in state.connections.map { it.clientId }
            }
        }

        val application = withAsyncTrace("Await application to connect") {
            messages.consumeAsFlow().filterIsInstance<ClientConnected>().first {
                it.clientRole == Application
            }
        }

        val applicationProcessHandle = ProcessHandle.of(application.clientPid!!).get()
        currentCoroutineContext().job.invokeOnCompletion {
            applicationProcessHandle.destroy()
        }

        client.sendAsync(OrchestrationMessage.ShutdownRequest("Requested by test"))
        applicationProcessHandle.onExit().await()
    }
}
