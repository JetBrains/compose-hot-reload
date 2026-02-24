/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.await
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.JavaHome
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.test.gradle.JbrProvisioning
import org.jetbrains.compose.reload.test.gradle.BuildGradleKtsExtension
import org.jetbrains.compose.reload.test.gradle.ExtendBuildGradleKts
import org.jetbrains.compose.reload.test.gradle.ExtendSettingsGradleKts
import org.jetbrains.compose.reload.test.gradle.GradleRunner
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.SettingsGradleKtsExtension
import org.jetbrains.compose.reload.test.gradle.TestedProjectMode
import org.jetbrains.compose.reload.test.gradle.assertExit
import org.jetbrains.compose.reload.test.gradle.assertSuccessful
import org.jetbrains.compose.reload.test.gradle.buildFlow
import org.jetbrains.compose.reload.test.gradle.findAnnotation
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.utils.TestOnlyDefaultCompilerOptions
import org.jetbrains.compose.reload.utils.TestOnlyDefaultComposeVersion
import org.jetbrains.compose.reload.utils.TestOnlyDefaultKotlinVersion
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.appendLines
import kotlin.test.assertEquals

@TestOnlyDefaultComposeVersion
@TestOnlyDefaultKotlinVersion
@TestOnlyDefaultCompilerOptions
@TestedProjectMode(ProjectMode.Kmp)
class JetBrainsRuntimeProvisioningTest {

    annotation class RequestToolchain(val version: String, val vendor: String = "JETBRAINS")

    @HotReloadTest
    @RequestToolchain("25")
    @ExtendBuildGradleKts(CustomLauncherSetup::class)
    fun `test - use custom launcher - jbr25`(fixture: HotReloadTestFixture) =
        fixture.`test - starts with expected jvm version`("25")

    @HotReloadTest
    @RequestToolchain("21")
    @ExtendBuildGradleKts(CustomLauncherSetup::class)
    fun `test - use custom launcher - jbr21`(fixture: HotReloadTestFixture) =
        fixture.`test - starts with expected jvm version`("21")

    @HotReloadTest
    @RequestToolchain("21")
    @ExtendBuildGradleKts(TopLevelToolchain::class)
    fun `test - project level jvmToolchain - 21`(fixture: HotReloadTestFixture) =
        fixture.`test - starts with expected jvm version`("21")

    @HotReloadTest
    @RequestToolchain("25")
    @ExtendBuildGradleKts(TopLevelToolchain::class)
    fun `test - project level jvmToolchain - 25`(fixture: HotReloadTestFixture) =
        fixture.`test - starts with expected jvm version`("25")


    /**
     * We need to create a scenario where JBR is provisioned automatically via [HotReloadProperty.AutoJetBrainsRuntimeProvisioningEnabled].
     * To create this, we need to ensure that the test project:
     * 1. Does not use JBR as a toolchain (otherwise our jbr-resolver-plugin would provision it)
     * 2. Uses the foojay resolver plugin (to provision the necessary JDK for other Gradle tasks)
     * 3. Does not use foojay resolver plugin for JBR provisioning
     */
    @HotReloadTest
    @JbrProvisioning(gradleProvisioningEnabled = false, autoProvisioningEnabled = true)
    @RequestToolchain("25", vendor = "AMAZON")
    @ExtendBuildGradleKts(TopLevelToolchain::class)
    @ExtendSettingsGradleKts(FoojayResolverPlugin::class)
    fun `test - automatic jbr provisioning 25`(fixture: HotReloadTestFixture) =
        fixture.`test - starts with expected jvm version`("25")

    @HotReloadTest
    @JbrProvisioning(gradleProvisioningEnabled = false, autoProvisioningEnabled = true)
    @RequestToolchain("21", vendor = "AMAZON")
    @ExtendBuildGradleKts(TopLevelToolchain::class)
    @ExtendSettingsGradleKts(FoojayResolverPlugin::class)
    fun `test - automatic jbr provisioning 21`(fixture: HotReloadTestFixture) =
        fixture.`test - starts with expected jvm version`("21")

    private fun HotReloadTestFixture.`test - starts with expected jvm version`(version: String) = runTest {
        val client = initialSourceCode(
            """
            import org.jetbrains.compose.reload.test.*
            fun main() = screenshotTestApplication {}
        """.trimIndent()
        ) {
            var clientConnected: OrchestrationMessage.ClientConnected? = null
            var uiRendered = false

            skipToMessage<OrchestrationMessage> { message ->
                if (message is OrchestrationMessage.ClientConnected && message.clientRole == OrchestrationClientRole.Application) {
                    clientConnected = message
                } else if (message is OrchestrationMessage.UIRendered) {
                    uiRendered = true
                }
                clientConnected != null && uiRendered
            }

            clientConnected!!
        }

        val clientPid = client.clientPid ?: error("Missing 'clientPid'")
        val javaBinary = ProcessHandle.of(clientPid).get().info().command().get()
        val javaHome = JavaHome.fromExecutable(Path(javaBinary))
        val releaseFileContent = javaHome.readReleaseFile()
        assertEquals(version, releaseFileContent.javaVersion?.split(".")?.first())
        assertEquals("JetBrains s.r.o.", releaseFileContent.implementor)
    }

    @HotReloadTest
    @JbrProvisioning(gradleProvisioningEnabled = false, autoProvisioningEnabled = false)
    fun `test - use intellij fallback`(fixture: HotReloadTestFixture) = fixture.runTest {
        /* Provide garbage value, to trigger fallback */
        projectDir.gradleProperties.appendLines(
            listOf(
                "${HotReloadProperty.JetBrainsRuntimeBinary.key}=xyz.garbage",
            )
        )

        runTransaction {
            writeCode(
                source = """
                    import org.jetbrains.compose.reload.test.*
                    fun main() = screenshotTestApplication {}
                    """.trimIndent()
            )
        }

        /* Run the project without providing the fallback from the IDE and expect failure*/
        fixture.launchTestDaemon {
            val buildEvents = gradleRunner.buildFlow("hotRunJvm", "--mainClass", "MainKt").toList()
            assertEquals(GradleRunner.ExitCode.failure, buildEvents.assertExit().code)
        }

        /* Run with the intellij fallback property */
        val client = runTransaction {
            fixture.launchTestDaemon {
                val buildEvents = fixture.gradleRunner.buildFlow(
                    "hotRunJvm",
                    "-D${HotReloadProperty.IdeaJetBrainsRuntimeBinary.key}=${JavaHome.current().javaExecutable.absolutePathString()}",
                    "--mainClass", "MainKt"
                ).toList()
                buildEvents.assertSuccessful()
            }

            skipToMessage<OrchestrationMessage.ClientConnected> { client ->
                client.clientRole == OrchestrationClientRole.Application
            }
        }

        val clientProcessHandle = ProcessHandle.of(client.clientPid ?: error("Missing 'clientPid'")).get()

        assertEquals(
            JavaHome.current().javaExecutable.absolutePathString(),
            clientProcessHandle.info().command().get()
        )

        fixture.sendMessage(OrchestrationMessage.ShutdownRequest("Requested by test")) {
            clientProcessHandle.onExit().await()
        }
    }

    class CustomLauncherSetup : BuildGradleKtsExtension {
        override fun javaExecConfigure(context: ExtensionContext): String {
            val request = context.findAnnotation<RequestToolchain>() ?: error("Missing 'RequestToolchain' annotation")
            val version = request.version
            val vendor = request.vendor

            return """
                javaLauncher.set(javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of($version))
                    vendor.set(JvmVendorSpec.$vendor)
                })
            """.trimIndent()
        }
    }

    class TopLevelToolchain : BuildGradleKtsExtension {

        override fun kotlin(context: ExtensionContext): String? {
            val annotation = context.findAnnotation<RequestToolchain>() ?: return null

            return """
                jvmToolchain {
                    languageVersion.set(JavaLanguageVersion.of(${annotation.version}))
                    vendor.set(JvmVendorSpec.${annotation.vendor})
                }
            """.trimIndent()
        }
    }

    class FoojayResolverPlugin : SettingsGradleKtsExtension {
        override fun plugins(context: ExtensionContext): String {
            return """id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0""""
        }
    }

}
