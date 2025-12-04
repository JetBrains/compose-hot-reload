/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.tests

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.isActive
import org.jetbrains.compose.devtools.api.ReloadState
import org.jetbrains.compose.reload.core.asChannel
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.core.withAsyncTrace
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import org.jetbrains.compose.reload.test.gradle.BuildGradleKtsExtension
import org.jetbrains.compose.reload.test.gradle.BuildMode
import org.jetbrains.compose.reload.test.gradle.Headless
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestDimensionExtension
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.HotReloadTestInvocationContext
import org.jetbrains.compose.reload.test.gradle.TestedBuildMode
import org.jetbrains.compose.reload.test.gradle.TransactionScope
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.utils.HostIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class DevtoolsIntegrationTests {
    private val logger = createLogger()

    /**
     * This test will launch an empty application, but in *non-headless* mode:
     * Because the test is started non-headless, we can expect the devtools process to be launched
     *  successfully. The test will assert that a client with [OrchestrationClientRole.Tooling] connects
     * to our [org.jetbrains.compose.reload.orchestration.OrchestrationServer].
     */
    @HotReloadTest
    @QuickTest
    @HostIntegrationTest
    @Headless(false)
    fun `test - devtools - is launched`(fixture: HotReloadTestFixture) = fixture.runTest {
        runTransaction {
            /* Empty application: We're not testing the application, but the launch of the devtools */
            fixture.initialSourceCode(
                """
                import org.jetbrains.compose.reload.test.*
                
                fun main() {
                    screenshotTestApplication {
                    }
                }
                """.trimIndent()
            )

            /* The devtools are expected to be alive very quickly. The 30s timeout is generous. */
            skipToMessage<ClientConnected>(
                title = "Waiting for devtools to connect",
                timeout = 30.seconds
            ) { message ->
                logger.info("client connected: ${message.clientRole}")
                message.clientRole == OrchestrationClientRole.Tooling
            }
        }
    }

    @HotReloadTest
    @TestedBuildMode(BuildMode.Continuous)
    @TestedBuildMode(BuildMode.Explicit)
    fun `test - reload state`(fixture: HotReloadTestFixture) = fixture.runTest {
        val reloadState = orchestration.states.get(ReloadState)
        val reloadStateChannel = reloadState.asChannel()

        runTransaction {
            fixture initialSourceCode """
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {
                    // Foo
                }
            }
            """.trimIndent()

            if (fixture.buildMode == BuildMode.Explicit) {
                assertIs<ReloadState.Ok>(reloadState.value)
            } else {
                awaitState<ReloadState.Ok>(reloadStateChannel)
            }

            replaceSourceCode("// Foo", "TestText(\"Foo\")")
            if (fixture.buildMode == BuildMode.Explicit) {
                requestReload()
            }

            withAsyncTrace("Waiting for ReloadState: 'Reloading'") {
                awaitState<ReloadState.Reloading>(reloadStateChannel)
            }

            withAsyncTrace("Waiting for ReloadState: 'Ok'") {
                awaitState<ReloadState.Ok>(reloadStateChannel)
            }
        }
    }

    @HotReloadTest
    fun `test - shutdown`(fixture: HotReloadTestFixture) = fixture.runTest {
        val devtoolsClient = runTransaction {
            fixture initialSourceCode """
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {
                    TestText("0")
                }
            }
            """
            skipToMessage<ClientConnected> { client -> client.clientRole == OrchestrationClientRole.Tooling }
        }

        val devtoolsPid = devtoolsClient.clientPid ?: fail("Missing pid in ClientConnected message")
        val devtoolsProcess = ProcessHandle.of(devtoolsPid).getOrNull() ?: fail("devtools process not found")

        val shutdownLog = projectDir.path.resolve("build/run/jvmMain/shutdown.log")
        assertFalse(shutdownLog.exists())

        runTransaction { ShutdownRequest("Requested by test").send() }
        devtoolsProcess.onExit().asDeferred().await()

        assertTrue(shutdownLog.exists(), "Expected shutdown log to be created")
        val reportLineRegex = Regex("""Shutdown actions completed: (?<completed>\d)+, failed: (?<failed>\d)+""")

        val reportLineMatches = shutdownLog.readLines().mapNotNull { line -> reportLineRegex.matchEntire(line) }
        if (reportLineMatches.size != 1) fail("Expected exactly one line matching the shutdown report line")

        val reportLineMatch = reportLineMatches.single()
        val completed = reportLineMatch.groups["completed"]?.value?.toIntOrNull()
            ?: fail("Expected 'completed' group to be present")

        val failed = reportLineMatch.groups["failed"]?.value?.toIntOrNull()
            ?: fail("Expected 'failed' group to be present")

        assertEquals(0, failed, "Expected no failed actions")
        assertTrue(completed > 0, "Expected at least one completed action. Found: $completed")

        val durationLineRegex = Regex("""Shutdown duration: (?<duration>.*)""")

        val durationLineMatch = shutdownLog.readLines()
            .mapNotNull { line -> durationLineRegex.matchEntire(line) }
            .singleOrNull() ?: fail("Expected exactly one line matching the shutdown duration line")

        val duration = Duration.parse(
            durationLineMatch.groups["duration"]?.value ?: fail("Expected 'duration' group to be present")
        )

        if (duration > 5.seconds) {
            fail("Expected shutdown duration to be less than 5 seconds, but was $duration")
        }
    }

    private suspend inline fun <reified T : ReloadState> TransactionScope.awaitState(channel: Channel<ReloadState>) {
        while (isActive) {
            if (channel.receive() is T) break
        }
    }
}

/**
 * Generic extension which will only run against tests declared in [DevtoolsIntegrationTests]
 */
internal class DevtoolsIntegrationTestsExtension : BuildGradleKtsExtension, HotReloadTestDimensionExtension {
    override fun javaExecConfigure(context: ExtensionContext): String? {
        if (context.testClass.getOrNull() != DevtoolsIntegrationTests::class.java) return null

        /*
        We're launching the application in 'non-headless' mode, but we still would prevent
        the system from showing an icon in the taskbar.
         */
        return """
            systemProperty("apple.awt.UIElement", true)
        """.trimIndent()
    }

    override fun transform(
        context: ExtensionContext,
        tests: List<HotReloadTestInvocationContext>
    ): List<HotReloadTestInvocationContext> {
        if (context.testClass.getOrNull() != DevtoolsIntegrationTests::class.java) return tests

        /*
        Actually, we do not really care about test-dimensions. We only care about the different build modes.
        So let's just pick the last dimension for each build mode from the ones available in the transformation chain.
         */
        return tests.groupBy { it.buildMode }.map { it.value.last() }
    }
}
