/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.toList
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Application
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.BuildStarted
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.BuildTaskResult
import org.jetbrains.compose.reload.orchestration.asChannel
import org.jetbrains.compose.reload.test.gradle.ApplicationLaunchMode
import org.jetbrains.compose.reload.test.gradle.BuildGradleKts
import org.jetbrains.compose.reload.test.gradle.GradleRunner.ExitCode.Companion.failure
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.TestedLaunchMode
import org.jetbrains.compose.reload.test.gradle.TestedProjectMode
import org.jetbrains.compose.reload.test.gradle.assertExitCode
import org.jetbrains.compose.reload.test.gradle.assertSuccessful
import org.jetbrains.compose.reload.test.gradle.buildFlow
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.getDefaultMainKtSourceFile
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.test.gradle.launchApplicationAndWait
import org.jetbrains.compose.reload.test.gradle.replaceSourceCode
import org.jetbrains.compose.reload.test.gradle.replaceText
import org.jetbrains.compose.reload.utils.BuildMode
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

@GradleIntegrationTest
@QuickTest
@TestedLaunchMode(ApplicationLaunchMode.Detached)
@TestedProjectMode(ProjectMode.Kmp)
@BuildMode(isContinuous = false)
class BuildEventTest {
    @HotReloadTest
    fun `test - build started`(fixture: HotReloadTestFixture) = fixture.runTest {
        val channel = orchestration.asChannel()

        fixture initialSourceCode """
            import org.jetbrains.compose.reload.test.*
            fun main() {
                screenshotTestApplication {
                   // A
                }
            }
        """.trimIndent()

        assertEquals(
            0, channel.pullMessages().filterIsInstance<BuildStarted>().size,
            "Expected no '${BuildStarted::class}' message"
        )

        /* No change: UP-TO-DATE, repeat this three times */
        repeat(3) {
            fixture.gradleRunner.buildFlow("reload").toList().assertSuccessful()
            assertEquals(
                1, channel.pullMessages().filterIsInstance<BuildStarted>().size,
                "Expected single '${BuildStarted::class}' message"
            )
        }

        replaceSourceCode("// A", """println("foo")""")
        fixture.gradleRunner.buildFlow("reload").toList().assertSuccessful()
        assertEquals(
            1, channel.pullMessages().filterIsInstance<BuildStarted>().size,
            "Expected single '${BuildStarted::class}' message"
        )
    }

    @HotReloadTest
    fun `test - build task result`(fixture: HotReloadTestFixture) = fixture.runTest {
        val channel = orchestration.asChannel()

        fixture initialSourceCode """
            import org.jetbrains.compose.reload.test.*
            fun main() {
                screenshotTestApplication {
                   // A
                }
            }
           """

        /* Provoke compilation failure */
        replaceSourceCode("// A", """{{error}}""")
        gradleRunner.buildFlow("reload").toList().assertExitCode(failure)
        val taskResults = channel.pullMessages().filterIsInstance<BuildTaskResult>()

        val compileTaskResult = taskResults.find { result -> result.taskId == ":compileKotlinJvm" }
            ?: fail("Cannot find ':compileKotlinJvm' task result")

        assertFalse(compileTaskResult.isSuccess, "Expected ':compileKotlinJvm' to be non successful")
        if (compileTaskResult.failures.isEmpty()) fail("Missing 'failures' in ':compileKotlinJvm' task result")

        /* Fix compilation */
        replaceSourceCode("""{{error}}""", """println("A")""")
        gradleRunner.buildFlow("reload").toList().assertSuccessful()
        val compileTaskResult2 = channel.pullMessages().filterIsInstance<BuildTaskResult>().find { result ->
            result.taskId == ":compileKotlinJvm"
        } ?: fail("Cannot find ':compileKotlinJvm' task result")
        assertTrue(compileTaskResult2.isSuccess, "Expected ':compileKotlinJvm' to be successful")
        if (compileTaskResult2.failures.isNotEmpty()) fail("Unexpected 'failures' in ':compileKotlinJvm' task result")
    }

    @HotReloadTest
    @BuildGradleKts("foo")
    @BuildGradleKts("bar")
    fun `test - two applications`(fixture: HotReloadTestFixture) = fixture.runTest {
        val fooDir = projectDir.subproject("foo")
        val barDir = projectDir.subproject("bar")

        val fooMain = fooDir.path.resolve(getDefaultMainKtSourceFile())
        fooMain.createParentDirectories().writeText(
            """
            import org.jetbrains.compose.reload.test.*
            fun main() {
                screenshotTestApplication {
                   TestText("Foo")
                }
            }
        """.trimIndent()
        )

        val barMain = barDir.path.resolve(getDefaultMainKtSourceFile())
        barMain.createParentDirectories().writeText(
            """
            import org.jetbrains.compose.reload.test.*
            fun main() {
                screenshotTestApplication {
                   TestText("Bar")
                }
            }
        """.trimIndent()
        )

        /*
        Start 'foo' and issue a reload
         */
        launchApplicationAndWait(":foo")
        fooMain.replaceText("Foo", "Foo2")
        runTransaction {
            fixture.gradleRunner.buildFlow("reload").toList().assertSuccessful()
            assertEquals(
                1, pullMessages().count { it is BuildStarted },
                "Expected a single '${BuildStarted::class}' message"
            )
        }

        /* Stop 'foo*' and start 'bar' */
        sendMessage(OrchestrationMessage.ShutdownRequest("Requested by the test")) {
            skipToMessage<OrchestrationMessage.ClientDisconnected> { message -> message.clientRole == Application }
        }
        launchApplicationAndWait(":bar")
        checkScreenshot("bar-0")

        /* Update and issue a reload */
        barMain.replaceText("Bar", "Bar1")
        runTransaction {
            fixture.gradleRunner.buildFlow("reload").toList().assertSuccessful()
            fixture.checkScreenshot("bar-1")
            assertEquals(
                1, pullMessages().count { it is BuildStarted },
                "Expected a single '${BuildStarted::class}' message"
            )
        }
    }
}

private fun <T> ReceiveChannel<T>.pullMessages(): List<T> = buildList {
    while (true) {
        add(tryReceive().getOrNull() ?: break)
    }
}
