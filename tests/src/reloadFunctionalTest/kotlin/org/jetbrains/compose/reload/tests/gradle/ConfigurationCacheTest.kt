/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.compose.reload.core.Environment
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.await
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.debug
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.orchestration.OrchestrationClient
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Application
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Unknown
import org.jetbrains.compose.reload.orchestration.OrchestrationConnectionsState
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.UIRendered
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.LogMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ShutdownRequest
import org.jetbrains.compose.reload.orchestration.asChannel
import org.jetbrains.compose.reload.orchestration.sendBlocking
import org.jetbrains.compose.reload.orchestration.startOrchestrationListener
import org.jetbrains.compose.reload.orchestration.toJvmArg
import org.jetbrains.compose.reload.test.gradle.ApplicationLaunchMode
import org.jetbrains.compose.reload.test.gradle.JbrProvisioning
import org.jetbrains.compose.reload.test.gradle.ExtendGradleProperties
import org.jetbrains.compose.reload.test.gradle.ExtendProjectSetup
import org.jetbrains.compose.reload.test.gradle.GradleBuildEvent
import org.jetbrains.compose.reload.test.gradle.GradlePropertiesExtension
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.ProjectSetupExtension
import org.jetbrains.compose.reload.test.gradle.TestedLaunchMode
import org.jetbrains.compose.reload.test.gradle.assertSuccessful
import org.jetbrains.compose.reload.test.gradle.buildFlow
import org.jetbrains.compose.reload.test.gradle.getDefaultMainKtSourceFile
import org.jetbrains.compose.reload.test.gradle.launchApplicationAndWait
import org.jetbrains.compose.reload.test.gradle.startLoggerDispatch
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.TestOnlyDefaultComposeVersion
import org.jetbrains.compose.reload.utils.TestOnlyDefaultKotlinVersion
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.fail
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals

@GradleIntegrationTest
@TestOnlyDefaultComposeVersion
@TestOnlyDefaultKotlinVersion
@ExtendProjectSetup(ConfigurationCacheTest.Extension::class)
@ExtendGradleProperties(ConfigurationCacheTest.Extension::class)
@TestedLaunchMode(ApplicationLaunchMode.Detached) // We manually launch the application
class ConfigurationCacheTest {

    @HotReloadTest
    fun `test - start app`(fixture: HotReloadTestFixture) = fixture.runTest {
        /* Start application */
        fixture.runTransaction {
            launchChildTransaction {
                fixture.gradleRunner.buildFlow(fixture.runTask, "--mainClass", "MainKt").toList()
                    .assertSuccessful()
                    .assertConfigurationCacheStored()
            }

            skipToMessage<UIRendered>()
            ShutdownRequest("Explicitly requested by the test").send()
        }

        /* Start the application again: Expect gcc reused! */
        fixture.runTransaction {
            launchChildTransaction {
                fixture.gradleRunner.buildFlow(fixture.runTask, "--mainClass", "MainKt").toList()
                    .assertSuccessful()
                    .assertConfigurationCacheReused()
            }

            skipToMessage<UIRendered>()
            ShutdownRequest("Explicitly requested by the test").send()
        }
    }

    @HotReloadTest
    fun `test - reload task`(fixture: HotReloadTestFixture) = fixture.runTest {
        /* Start application */
        fixture.launchApplicationAndWait()

        /* Run reload task */
        gradleRunner.buildFlow("reload").toList().assertSuccessful()
            .assertConfigurationCacheStored()

        /* Run the reload task again */
        gradleRunner.buildFlow("reload").toList().assertSuccessful()
            .assertConfigurationCacheReused()

        /* Shutdown the application, restart it again and expect the reload task to be reused */
        fixture.runTransaction {
            ShutdownRequest("Explicitly requested by the test").send()
            fixture.orchestration.states.get(OrchestrationConnectionsState).await { state ->
                state.connections.isEmpty()
            }
        }
        fixture.launchApplicationAndWait()
        gradleRunner.buildFlow("reload").toList().assertSuccessful()
            .assertConfigurationCacheReused()
    }


    @HotReloadTest
    fun `test - recompiler`(fixture: HotReloadTestFixture) = fixture.runTest {
        val logger = createLogger(name = "Test", dispatch = listOf(fixture.orchestration.startLoggerDispatch()))
        val listener = startOrchestrationListener(Unknown)
        val runner = gradleRunner.copy(arguments = listOf(listener.toJvmArg()))


        suspend fun CoroutineScope.startApplication(): OrchestrationClient {
            launch { runner.buildFlow(fixture.runTask, "--mainClass", "MainKt").toList().assertSuccessful() }
            val connection = listener.connections.receive().getOrThrow()

            /* Forward messages from our run to the test orchestration */
            connection.subtask {
                connection.messages.collect { message ->
                    fixture.orchestration.send(message)
                }
            }

            currentCoroutineContext().job.invokeOnCompletion {
                connection.sendBlocking(ShutdownRequest("Explicitly requested by the test"))
                connection.close()
            }

            /* Await application to be connected */
            connection.states.get(OrchestrationConnectionsState).await { state ->
                Application in state.connections.map { it.clientRole }
            }

            return connection
        }

        suspend fun OrchestrationClient.sendRecompileRequestAndCollectGCCReports(): List<String> {
            val messages = this.asChannel()
            val request = RecompileRequest()
            logger.debug("Sending $request")
            send(request)

            val gccReports = mutableListOf<String>()
            while (isActive) {
                val message = messages.receive()
                if (message is LogMessage &&
                    message.environment == Environment.build &&
                    message.message.contains("configuration cache", true)
                ) gccReports.add(message.message)

                if (message is RecompileResult) {
                    assertEquals(request.messageId, message.recompileRequestId)
                    assertEquals(0, message.exitCode)
                    break
                }
            }

            return gccReports.toList()
        }

        /* First run: Start application and send recompile request */
        logger.info("First run: Start application and send recompile request")
        coroutineScope {
            val connection = startApplication()
            val gccReports = connection.sendRecompileRequestAndCollectGCCReports()

            if (gccReports.none { it == "Configuration cache entry stored." }) {
                fail { "Expected 'Configuration cache entry stored.' in:\n${gccReports.joinToString("\n") { it }}" }
            }

            connection.send(ShutdownRequest("Requested by the test"))
            connection.await()
        }

        /*
        Second run:
        We start the application again and expect the first recompile request to re-use the gcc stored
        previously!
         */
        logger.info("Second run: Start application and send recompile request: Expect gcc reused")
        coroutineScope {
            val connection = startApplication()
            val gccReports = connection.sendRecompileRequestAndCollectGCCReports()
            if (gccReports.none { it == "Configuration cache entry reused." }) {
                fail { "Expected 'Configuration cache entry reused.' in:\n${gccReports.joinToString("\n") { it }}" }
            }

            connection.send(ShutdownRequest("Requested by the test"))
            connection.await()
        }
    }

    @HotReloadTest
    @JbrProvisioning(gradleProvisioningEnabled = false, autoProvisioningEnabled = true)
    fun `test - start app - auto jbr provisioning`(fixture: HotReloadTestFixture) = fixture.runTest {
        /* Start application: initialise JBR if necessary */
        fixture.runTransaction {
            launchChildTransaction {
                fixture.gradleRunner.buildFlow(fixture.runTask, "--mainClass", "MainKt").toList()
                    .assertSuccessful()
            }

            skipToMessage<UIRendered>()
            ShutdownRequest("Explicitly requested by the test").send()
        }

        /* Start application */
        fixture.runTransaction {
            launchChildTransaction {
                fixture.gradleRunner.buildFlow(fixture.runTask, "--mainClass", "MainKt").toList()
                    .assertSuccessful()
            }

            skipToMessage<UIRendered>()
            ShutdownRequest("Explicitly requested by the test").send()
        }

        /* Start the application again: Expect gcc reused! */
        fixture.runTransaction {
            launchChildTransaction {
                fixture.gradleRunner.buildFlow(fixture.runTask, "--mainClass", "MainKt").toList()
                    .assertSuccessful()
                    .assertConfigurationCacheReused()
            }

            skipToMessage<UIRendered>()
            ShutdownRequest("Explicitly requested by the test").send()
        }
    }

    @HotReloadTest
    @JbrProvisioning(gradleProvisioningEnabled = false, autoProvisioningEnabled = true)
    fun `test - reload task - auto jbr provisioning`(fixture: HotReloadTestFixture) = fixture.runTest {
        /* Start application: initialise JBR if necessary */
        fixture.runTransaction {
            launchChildTransaction {
                fixture.gradleRunner.buildFlow(fixture.runTask, "--mainClass", "MainKt").toList()
                    .assertSuccessful()
            }

            skipToMessage<UIRendered>()
            ShutdownRequest("Explicitly requested by the test").send()
        }

        /* Start application */
        fixture.launchApplicationAndWait()

        /* Run reload task */
        gradleRunner.buildFlow("reload").toList().assertSuccessful()
            .assertConfigurationCacheStored()

        /* Run the reload task again */
        gradleRunner.buildFlow("reload").toList().assertSuccessful()
            .assertConfigurationCacheReused()

        /* Shutdown the application, restart it again and expect the reload task to be reused */
        fixture.runTransaction {
            ShutdownRequest("Explicitly requested by the test").send()
            fixture.orchestration.states.get(OrchestrationConnectionsState).await { state ->
                state.connections.isEmpty()
            }
        }
        fixture.launchApplicationAndWait()
        gradleRunner.buildFlow("reload").toList().assertSuccessful()
            .assertConfigurationCacheReused()
    }

    private fun Iterable<GradleBuildEvent>.assertConfigurationCacheStored() {
        val gccReports = filterIsInstance<GradleBuildEvent.Output.Stdout>()
            .filter { it.line.contains("configuration cache", ignoreCase = true) }

        if (gccReports.none { it.line == "Configuration cache entry stored." }) {
            fail { "Expected 'Configuration cache entry stored.' in:\n${gccReports.joinToString("\n") { it.line }}" }
        }
    }

    private fun Iterable<GradleBuildEvent>.assertConfigurationCacheReused() {
        val gccReports = filterIsInstance<GradleBuildEvent.Output.Stdout>()
            .filter { it.line.contains("configuration cache", ignoreCase = true) }

        if (gccReports.none { it.line == "Configuration cache entry reused." }) {
            fail { "Expected 'Configuration cache entry reused.' in:\n${gccReports.joinToString("\n") { it.line }}" }
        }
    }

    private val HotReloadTestFixture.runTask
        get() = when (projectMode) {
            ProjectMode.Kmp -> ":hotRunJvm"
            ProjectMode.Jvm -> "hotRun"
        }

    class Extension : ProjectSetupExtension, GradlePropertiesExtension {
        override fun setupProject(
            fixture: HotReloadTestFixture, context: ExtensionContext
        ) {

            fixture.projectDir.resolve(fixture.getDefaultMainKtSourceFile())
                .createParentDirectories()
                .writeText(
                    """
                    import org.jetbrains.compose.reload.test.*
                    
                    fun main() {
                        screenshotTestApplication {
                            // Content
                        }
                    }
                """.trimIndent()
                )
        }

        override fun properties(context: ExtensionContext): List<String> {
            return listOf(
                "org.gradle.configuration-cache=true",
                "org.gradle.configuration-cache.parallel=true",
                "org.gradle.configuration-cache.problems=fail",
                "${HotReloadProperty.IsHeadless.key}=true",
            )
        }
    }
}
