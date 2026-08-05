/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.mcp.HeadlessLaunchSpec
import org.jetbrains.compose.reload.mcp.HeadlessSessionManager
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ScreenshotResult
import org.jetbrains.compose.reload.orchestration.asChannel
import org.jetbrains.compose.reload.test.gradle.GradleRunner
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.build
import org.jetbrains.compose.reload.test.gradle.getDefaultMainKtSourceFile
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for [HeadlessSessionManager] + `hotMcpHeadlessArgfile` Gradle task used by headless MCP tools.
 */
@OptIn(InternalHotReloadApi::class)
class HeadlessMcpToolsIntegrationTest {
    private val logger = createLogger()

    @HotReloadTest
    @GradleIntegrationTest
    @QuickTest
    fun `test - run headless app, renders a screenshot, and closes`(
        fixture: HotReloadTestFixture,
    ) = fixture.runTest {
        fixture.projectDir.resolve(fixture.getDefaultMainKtSourceFile()).createParentDirectories().writeText(
            """
            import androidx.compose.foundation.background
            import androidx.compose.foundation.layout.Box
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.graphics.Color
            import org.jetbrains.compose.reload.test.TestText

            @Composable
            fun App() {
                Box(Modifier.background(Color.White)) {
                    TestText("Headless MCP test")
                }
            }
            """.trimIndent()
        )

        val (argFileTask, argFileRelativePath) = when (projectMode) {
            ProjectMode.Kmp -> "hotMcpHeadlessJvmArgfile" to "build/run/hotMcpHeadlessJvm.argfile"
            ProjectMode.Jvm -> "hotMcpHeadlessArgfile" to "build/run/hotMcpHeadless.argfile"
        }

        assertEquals(GradleRunner.ExitCode.success, fixture.gradleRunner.build(argFileTask))
        val generatedArgFile = fixture.projectDir.resolve(argFileRelativePath)
        if (!generatedArgFile.isRegularFile()) {
            fail("Expected headless argfile at '$generatedArgFile', but it does not exist")
        }

        // Filter out orchestration port passed by functional test harness as HeadlessSessionManager injects its own
        val argFile = createTempFile("headless-mcp-it-", ".argfile")
        argFile.toFile().deleteOnExit()
        argFile.writeText(
            generatedArgFile.readText().lineSequence()
                .filterNot { it.contains(HotReloadProperty.OrchestrationPort.key) }
                .joinToString("\n")
        )

        val spec = HeadlessLaunchSpec(
            javaBinary = Path.of(ProcessHandle.current().info().command().orElseThrow()),
            argFile = argFile,
        )
        val sessions = HeadlessSessionManager(spec)

        withContext(Dispatchers.IO) {
            val session = sessions.start(className = "MainKt", funName = "App", width = 400, height = 300)
            try {
                assertTrue(session.isAlive, "forked headless application should be alive after start()")

                val request = ScreenshotRequest()
                val responses = session.orchestration.asChannel().consumeAsFlow()
                session.orchestration.send(request)
                val screenshot = withTimeoutOrNull(30.seconds) {
                    responses.firstOrNull {
                        it is ScreenshotResult && it.screenshotRequestId == request.messageId
                    } as? ScreenshotResult
                }

                assertNotNull(screenshot, "expected a ScreenshotResult from the headless application")
                assertTrue(screenshot.isSuccess, "screenshot failed: ${screenshot.errorMessage}")
                assertTrue(screenshot.data.isNotEmpty(), "screenshot image data should not be empty")
            } finally {
                sessions.close(session.id)
            }
            assertTrue(!session.isAlive, "process should be terminated after close()")
        }
    }
}
