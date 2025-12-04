/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalPathApi::class)

package org.jetbrains.compose.reload.tests.gradle

import kotlinx.coroutines.flow.toList
import org.jetbrains.compose.reload.core.JavaHome
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Application
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Compiler
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ClientConnected
import org.jetbrains.compose.reload.test.gradle.ApplicationLaunchMode
import org.jetbrains.compose.reload.test.gradle.BuildMode
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.TestedBuildMode
import org.jetbrains.compose.reload.test.gradle.TestedLaunchMode
import org.jetbrains.compose.reload.test.gradle.TestedProjectMode
import org.jetbrains.compose.reload.test.gradle.assertSuccessful
import org.jetbrains.compose.reload.test.gradle.buildFlow
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.utils.Deflake
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.HostIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import org.jetbrains.compose.reload.utils.TestOnlyDefaultKotlinVersion
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.appendText
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.fail

@HotReloadTest
@HostIntegrationTest
@GradleIntegrationTest
@QuickTest
@TestedProjectMode(ProjectMode.Kmp)
@TestedBuildMode(BuildMode.Continuous)
class GradleRecompilerProcessTest {

    private val logger = createLogger()

    @HotReloadTest
    @TestedLaunchMode(ApplicationLaunchMode.GradleBlocking)
    @Deflake(100)
    fun `test - gradle recompiler process is stopped - ShutdownRequest`(fixture: HotReloadTestFixture) =
        fixture.runTest {
            val processes = startApplicationAndAwaitGradleProcess()

            fixture.runTransaction {
                OrchestrationMessage.ShutdownRequest("Explicitly requested by the test").send()
                processes.gradle.onExit().get(30, TimeUnit.SECONDS)
                processes.gradle.descendants().forEach { child ->
                    if (child.isAlive) fail(
                        "Expected all child processes to be terminated: ${child.info().command().orElse("null")}"
                    )
                }
            }
        }


    @HotReloadTest
    @TestedLaunchMode(ApplicationLaunchMode.Detached)
    @Deflake(100)
    fun `test - gradle recompiler process is stopped - application destroyed forcefully`(
        fixture: HotReloadTestFixture
    ) = fixture.runTest {
        val processes = startApplicationAndAwaitGradleProcess()

        logger.info("Destroying application process forcefully...")
        processes.app.destroyForcibly()
        processes.app.onExit().get(15, TimeUnit.SECONDS)

        logger.info("Checking if the recompiler Gradle Daemon (${processes.gradle.pid()}) is dead..")
        processes.gradle.onExit().get(30, TimeUnit.SECONDS)
        processes.gradle.descendants().forEach { child ->
            if (child.isAlive) fail(
                "Expected all child processes to be terminated: ${child.info().command().orElse("null")}"
            )
        }
    }


    @HotReloadTest
    @QuickTest
    @TestOnlyDefaultKotlinVersion
    @TestedProjectMode(ProjectMode.Kmp)
    fun `test - uses same java home as original Gradle invocation`(fixture: HotReloadTestFixture) = fixture.runTest {
        val myJavaHome = System.getProperty("java.home")?.let(::Path) ?: error("java.home is not set")
        val targetJavaHome = fixture.projectDir.resolve("test-java-home").absolute()
        myJavaHome.copyToRecursively(targetJavaHome, followLinks = false, overwrite = true)

        fixture.projectDir.buildGradleKts.appendText(
            """
            
        """.trimIndent()
        )

        fixture.projectDir.resolve("src/commonMain/kotlin/Main.kt").createParentDirectories().writeText(
            """
            import org.jetbrains.compose.reload.test.*
            fun main() = screenshotTestApplication {
            }
            """.trimIndent()
        )

        val recompilerConnected = fixture.runTransaction {
            fixture.launchTestDaemon {
                fixture.gradleRunner.buildFlow(
                    ":hotRunJvmAsync", "--mainClass", "MainKt", "--auto",
                    "-Dorg.gradle.java.home=${targetJavaHome.absolutePathString()}"
                ).toList().assertSuccessful()
            }
            skipToMessage<ClientConnected> { message -> message.clientRole == Compiler }
        }

        val recompilerPid = recompilerConnected.clientPid ?: fail("Missing 'clientPid'")
        val recompilerProcess = ProcessHandle.of(recompilerPid).getOrNull() ?: fail("Cannot find 'recompiler process'")
        val command = recompilerProcess.info().command().getOrNull() ?: error("Missing 'command' in recompiler process")

        assertEquals(
            JavaHome(targetJavaHome).javaExecutable.toRealPath(),
            Path(command).toRealPath(),
            "Expected 'java' command"
        )
    }

    private suspend fun HotReloadTestFixture.startApplicationAndAwaitGradleProcess(): Processes {
        val fixture = this

        val processes = fixture.initialSourceCode(
            """
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {
                }
            }
        """.trimIndent()
        ) {
            var application: ClientConnected? = null
            var recompiler: Long? = null
            val recompilerPidRegex = Regex("""'Recompiler': Started \((?<pid>\d+)\)""")

            skipToMessage<OrchestrationMessage>("Waiting for app & recompiler process") { event ->
                if (event is ClientConnected && event.clientRole == Application) {
                    application = event
                }

                if (event is OrchestrationMessage.LogMessage) {
                    recompilerPidRegex.find(event.message)?.let { match ->
                        recompiler = match.groups["pid"]?.value?.toLongOrNull() ?: error("Invalid message '$event")
                    }
                }

                application != null && recompiler != null
            }

            logger.info("Application available at '${application?.clientPid}'")
            logger.info("Compiler available at '${recompiler}'")

            Processes(
                app = ProcessHandle.of(application?.clientPid ?: error("Missing app pid")).get(),
                gradle = ProcessHandle.of(recompiler ?: error("Missing gradle pid")).get(),
            )
        }

        if (!processes.app.isAlive) fail("Application process with pid=${processes.app.pid()} is not alive")
        if (!processes.gradle.isAlive) fail("Gradle process with pid=${processes.gradle.pid()} is not alive")
        return processes
    }

    class Processes(val app: ProcessHandle, val gradle: ProcessHandle)
}
