/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import kotlinx.coroutines.flow.toList
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.assertSuccessful
import org.jetbrains.compose.reload.test.gradle.buildFlow
import org.jetbrains.compose.reload.test.gradle.getDefaultMainKtSourceFile
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals

class CliTest {
    @QuickTest
    @GradleIntegrationTest
    @HotReloadTest
    fun `test - --auto option`(fixture: HotReloadTestFixture) = fixture.runTest {
        System.getProperties()
        projectDir.resolve(getDefaultMainKtSourceFile()).createParentDirectories().writeText(
            """
            import org.jetbrains.compose.reload.test.*
                
            fun main() {
               sendTestEventBlocking(System.getProperties())
            }
        """.trimIndent()
        )

        suspend fun launchAndAwaitProperties(vararg command: String): Map<String, String> {
            @Suppress("UNCHECKED_CAST")
            return runTransaction {
                fixture.gradleRunner.buildFlow(*command).toList().assertSuccessful()
                skipToMessage<OrchestrationMessage.TestEvent> { it.payload is Map<*, *> }.payload
            } as Map<String, String>
        }

        val runTask = when (fixture.projectMode) {
            ProjectMode.Kmp -> ":hotRunJvm"
            ProjectMode.Jvm -> ":hotRun"
        }

        /* Test '--auto' */
        run {
            val properties = launchAndAwaitProperties(runTask, "--mainClass", "MainKt", "--auto")
            assertEquals("true", properties[HotReloadProperty.GradleBuildContinuous.key])
        }

        /* Test '--no-auto' */
        run {
            val properties = launchAndAwaitProperties(runTask, "--mainClass", "MainKt", "--no-auto")
            assertEquals("false", properties[HotReloadProperty.GradleBuildContinuous.key])
        }

        /* Test '--autoReload' */
        run {
            val properties = launchAndAwaitProperties(runTask, "--mainClass", "MainKt", "--autoReload")
            assertEquals("true", properties[HotReloadProperty.GradleBuildContinuous.key])
        }

        /* Test '--no-autoReload' */
        run {
            val properties = launchAndAwaitProperties(runTask, "--mainClass", "MainKt", "--no-autoReload")
            assertEquals("false", properties[HotReloadProperty.GradleBuildContinuous.key])
        }

        val asyncRunTask = when (fixture.projectMode) {
            ProjectMode.Kmp -> ":hotRunJvmAsync"
            ProjectMode.Jvm -> ":hotRunAsync"
        }

        /* ASYNC: Test '--auto' */
        run {
            val properties = launchAndAwaitProperties(asyncRunTask, "--mainClass", "MainKt", "--auto")
            assertEquals("true", properties[HotReloadProperty.GradleBuildContinuous.key])
        }

        /* ASYNC: Test '--no-auto' */
        run {
            val properties = launchAndAwaitProperties(asyncRunTask, "--mainClass", "MainKt", "--no-auto")
            assertEquals("false", properties[HotReloadProperty.GradleBuildContinuous.key])
        }

        /* ASYNC: Test '--autoReload' */
        run {
            val properties = launchAndAwaitProperties(asyncRunTask, "--mainClass", "MainKt", "--autoReload")
            assertEquals("true", properties[HotReloadProperty.GradleBuildContinuous.key])
        }

        /* ASYNC: Test '--no-autoReload' */
        run {
            val properties = launchAndAwaitProperties(asyncRunTask, "--mainClass", "MainKt", "--no-autoReload")
            assertEquals("false", properties[HotReloadProperty.GradleBuildContinuous.key])
        }
    }
}
