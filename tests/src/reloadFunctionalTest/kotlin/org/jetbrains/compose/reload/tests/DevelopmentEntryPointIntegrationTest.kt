/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import kotlinx.coroutines.channels.consume
import org.jetbrains.compose.devtools.api.WindowsState
import org.jetbrains.compose.reload.core.asChannel
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.withAsyncTrace
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Tooling
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.test.gradle.Headless
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.utils.HostIntegrationTest
import kotlin.jvm.optionals.getOrNull
import kotlin.test.fail

class DevelopmentEntryPointIntegrationTest {

    private val logger = createLogger()

    /**
     * These tests, annoyingly, run non-headless. This might also lead to CI agents not being
     * able to execute this test. A better test alternative would be nice.
     */

    @Headless(false)
    @HostIntegrationTest
    @HotReloadTest
    fun `test - singleWindowApplication`(
        fixture: HotReloadTestFixture
    ) = runDevelopmentEntryPointTest(
        fixture,
        """
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.unit.*
            import androidx.compose.ui.window.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
               singleWindowApplication {
                   Text("This is a *non headless* test window!")
               }
            }
            """.trimIndent()
    )

    @Headless(false)
    @HostIntegrationTest
    @HotReloadTest
    fun `test - Window`(
        fixture: HotReloadTestFixture
    ) = runDevelopmentEntryPointTest(
        fixture,
        """
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.unit.*
            import androidx.compose.ui.window.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                application {
                    Window(onCloseRequest = ::exitApplication) {
                        Text("This is a *non headless* test window!")
                    }
                }
            }
            """.trimIndent()
    )

    @Headless(false)
    @HostIntegrationTest
    @HotReloadTest
    fun `test - DialogWindow`(
        fixture: HotReloadTestFixture
    ) = runDevelopmentEntryPointTest(
        fixture,
        """
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.unit.*
            import androidx.compose.ui.window.*
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                application {
                    DialogWindow(onCloseRequest = ::exitApplication) {
                        Text("This is a *non headless* test window!")
                    }
                }
            }
            """.trimIndent()
    )

    private fun runDevelopmentEntryPointTest(
        fixture: HotReloadTestFixture,
        content: String,
    ) = fixture.runTest {
        val windowsState = fixture.orchestration.states.get(WindowsState)
        /*
        This test does not run the underlying screenshot test application.
        Therefore, we'll manually send back ACK messages
         */
        launchTask {
            orchestration.messages.collect { message ->
                if (message !is OrchestrationMessage.Ack) {
                    orchestration send OrchestrationMessage.Ack(message.messageId)
                }
            }
        }

        val devtools = fixture.initialSourceCode(content) {
            skipToMessage<ClientConnected> { client -> client.clientRole == Tooling }
        }

        withAsyncTrace("Await one window") {
            windowsState.asChannel().consume {
                while (true) {
                    val state = receive()
                    if (state.windows.size == 1) break
                    else logger.info("Waiting for exactly one window: ${state.windows.size}")
                }
            }
        }

        val devtoolsPid = devtools.clientPid ?: fail("Missing 'pid' for devtools'")
        ProcessHandle.of(devtoolsPid).getOrNull() ?: fail("devtools process not found")
    }
}
