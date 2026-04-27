/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import kotlinx.coroutines.delay
import org.jetbrains.compose.devtools.api.WindowsState
import org.jetbrains.compose.reload.core.launchTask
import org.jetbrains.compose.reload.core.State
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Tooling
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.test.gradle.Headless
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ReloadEffects
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.test.gradle.sendTestEvent
import org.jetbrains.compose.reload.utils.HostIntegrationTest
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class WindowIntegrationTest {

    /**
     * These tests, annoyingly, run non-headless. This might also lead to CI agents not being
     * able to execute this test. A better test alternative would be nice.
     */

    @Headless(false)
    @ReloadEffects
    @HostIntegrationTest
    @HotReloadTest
    fun `test - singleWindowApplication`(
        fixture: HotReloadTestFixture
    ) = runWindowStateIntegrationTest(
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
    ) {
        val windowsState = fixture.orchestration.states.get(WindowsState)
        awaitOneWindow(windowsState)
    }

    @Headless(false)
    @ReloadEffects
    @HostIntegrationTest
    @HotReloadTest
    fun `test - Window`(
        fixture: HotReloadTestFixture
    ) = runWindowStateIntegrationTest(
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
    ) {
        val windowsState = fixture.orchestration.states.get(WindowsState)
        awaitOneWindow(windowsState)
    }

    @Headless(false)
    @ReloadEffects
    @HostIntegrationTest
    @HotReloadTest
    fun `test - DialogWindow`(
        fixture: HotReloadTestFixture
    ) = runWindowStateIntegrationTest(
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
    ) {
        val windowsState = fixture.orchestration.states.get(WindowsState)
        awaitOneWindow(windowsState)
    }

    @Headless(false)
    @ReloadEffects
    @HostIntegrationTest
    @HotReloadTest
    fun `test - Window with late activation`(
        fixture: HotReloadTestFixture
    ) = runWindowStateIntegrationTest(
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
                    Window(onCloseRequest = ::exitApplication, visible = false) {
                        onTestEvent {
                            window.isVisible = true
                        }
                        Text("This is a *non headless* test window!")
                    }
                }
            }
            """.trimIndent()
    ) {
        val windowsState = fixture.orchestration.states.get(WindowsState)
        // launch a task to ensure a window appears eventually
        launchTask { awaitOneWindow(windowsState) }

        // ensure no windows appear until we send a test event
        ensureNoWindows(windowsState)
        fixture.sendTestEvent()
    }

    private fun runWindowStateIntegrationTest(
        fixture: HotReloadTestFixture,
        content: String,
        body: suspend HotReloadTestFixture.() -> Unit,
    ) = fixture.runTest {
        fixture.launchAckSender()

        val devtools = fixture.initialSourceCode(content) {
            skipToMessage<ClientConnected> { client -> client.clientRole == Tooling }
        }

        body()

        val devtoolsPid = devtools.clientPid ?: fail("Missing 'pid' for devtools'")
        ProcessHandle.of(devtoolsPid).getOrNull() ?: fail("devtools process not found")
    }

    @OptIn(ExperimentalTime::class)
    suspend fun ensureNoWindows(
        windowsState: State<WindowsState>,
        awaitTime: Duration = 5.seconds,
        timeStep: Duration = 1.seconds,
    ) {
        val start = Clock.System.now()
        while (start + awaitTime > Clock.System.now()) {
            assertTrue { windowsState.value.windows.isEmpty() }
            delay(timeStep)
        }
    }
}
