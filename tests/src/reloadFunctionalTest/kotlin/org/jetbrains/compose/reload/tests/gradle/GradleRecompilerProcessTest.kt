/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Compiler
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.HostIntegrationTest
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull
import kotlin.test.fail

class GradleRecompilerProcessTest {

    @HotReloadTest
    @HostIntegrationTest
    @GradleIntegrationTest
    fun `test - gradle recompiler process is stopped`(fixture: HotReloadTestFixture) = fixture.runTest {
        val clientConnected = fixture.runTransaction {
            fixture initialSourceCode """
                import org.jetbrains.compose.reload.test.*
                
                fun main() {
                    screenshotTestApplication {
                    }
                }
            """.trimIndent()

            skipToMessage<OrchestrationMessage.ClientConnected> { message -> message.clientRole == Compiler }
        }

        val gradlePid = clientConnected.clientPid ?: fail("Missing pid in ClientConnected message")

        val gradleProcessHandle = ProcessHandle.of(gradlePid).getOrNull()
            ?: fail("Process with pid=$gradlePid not found")

        if (!gradleProcessHandle.isAlive) fail("Gradle process with pid=$gradlePid is not alive")

        fixture.runTransaction {
            OrchestrationMessage.ShutdownRequest("Explicitly requested by the test").send()
            gradleProcessHandle.onExit().get(30, TimeUnit.SECONDS)

            gradleProcessHandle.descendants().forEach { child ->
                if (child.isAlive) fail(
                    "Expected all child processes to be terminated: ${child.info().command().orElse("null")}"
                )
            }
        }
    }
}
