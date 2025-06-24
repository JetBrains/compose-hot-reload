/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.devtools.api.Recompiler
import org.jetbrains.compose.devtools.api.RecompilerContext
import org.jetbrains.compose.devtools.api.RecompilerExtension
import org.jetbrains.compose.reload.core.ExitCode
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.awaitOrThrow
import org.jetbrains.compose.reload.core.copyRecursivelyToZip
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.RecompileRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.TestEvent
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.getDefaultMainKtSourceFile
import org.jetbrains.compose.reload.test.gradle.launchApplicationAndWait
import org.jetbrains.compose.reload.utils.QuickTest
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import kotlin.reflect.KClass
import kotlin.test.assertEquals

class RecompilerExtensionTest {
    /**
     * Test procedure:
     *
     * 1) We compile the [TestRecompiler] and [TestRecompilerExtension] together with this test.
     * 2) We package 'test-recompiler.jar' jar file when running the test, by bundling only necessary parts.
     * 3) We configure the build to add the 'test-recompiler.jar' to our devtools classpath
     * 4) We configure the 'BuildSystem' property to something other than "Gradle"
     * 5) We launch the app and test the integration with the extension by communicating with it by
     * sending a [RecompileRequest], expecting [TestEvent]s as response.
     */
    @HotReloadTest
    @QuickTest
    fun `test - custom recompiler`(fixture: HotReloadTestFixture) = fixture.runTest {
        val extensionJar = createTestRecompilerExtensionJar()
        projectDir.buildGradleKts.appendText(
            """
            
            /* Using 'Test' as BuildSystem will prevent the Gradle Recompiler from getting picked */
            tasks.withType<JavaExec> {
                systemProperty("${HotReloadProperty.BuildSystem.key}", "Test")
            }
            
            /* Add our extension to the devtools */
            dependencies {
                "composeHotReloadDevTools"(files("${extensionJar.invariantSeparatorsPathString}"))
            }
        """.trimIndent()
        )

        /*
        Create a simple empty application
         */
        fixture.projectDir.resolve(fixture.getDefaultMainKtSourceFile()).createParentDirectories().writeText(
            """
            import org.jetbrains.compose.reload.test.*
            fun main() = screenshotTestApplication {}
            """.trimIndent()
        )

        launchApplicationAndWait()
        val orchestrationPort = orchestration.port.awaitOrThrow()

        runTransaction {
            /* Sending the recompile request, which is supposed to be handled by our extension */
            val request = RecompileRequest()
            request.send()

            /* The extension answers with the orchestration port */
            skipToMessage<TestEvent>("Wait for 'orchestration.port' message") { event ->
                val payload = event.payload
                if (payload is String && payload.startsWith("orchestration.port=")) {
                    assertEquals(orchestrationPort, payload.substringAfter("orchestration.port=").toInt())
                    return@skipToMessage true
                }
                false
            }

            /* The extension answers mirroring back the original request */
            skipToMessage<TestEvent>("Wait for 'requests' messages") { event ->
                val payload = event.payload
                if (payload is List<*>) {
                    assertEquals(request, payload.firstOrNull())
                    return@skipToMessage true
                }

                false
            }
        }
    }

    /**
     * Packages the [TestRecompiler] and [TestRecompilerExtension] into a small .jar file inside
     * the test project. The jar will also contain the configured service in the 'META-INF'
     */
    private fun HotReloadTestFixture.createTestRecompilerExtensionJar(): Path {
        val extensionJarContent = projectDir.resolve("build/test-recompiler").createDirectories()
        val extensionJarFile = projectDir.resolve("test-recompiler.jar")

        fun copyClass(clazz: KClass<*>) {
            val relativeFilePath = clazz.java.name.replace(".", "/") + ".class"
            val targetPath = extensionJarContent.resolve(relativeFilePath)
            targetPath.createParentDirectories().outputStream().use { target ->
                ClassLoader.getSystemClassLoader().getResourceAsStream(relativeFilePath).copyTo(target)
            }

        }

        /* Package all necessary classes for this extension to run */
        copyClass(TestRecompilerExtension::class)
        copyClass(TestRecompiler::class)
        copyClass(Class.forName("org.jetbrains.compose.reload.tests.TestRecompiler\$buildAndReload$1").kotlin)

        /* Create the entry for the ServiceLoader */
        extensionJarContent.resolve("META-INF/services/${RecompilerExtension::class.java.name}")
            .createParentDirectories()
            .writeText(TestRecompilerExtension::class.java.name)

        extensionJarContent.copyRecursivelyToZip(extensionJarFile)
        return extensionJarFile
    }
}

internal class TestRecompilerExtension : RecompilerExtension {
    override fun createRecompiler(): Recompiler {
        return TestRecompiler()
    }
}

private class TestRecompiler : Recompiler {
    override val name: String = "Test Recompiler"

    override suspend fun buildAndReload(context: RecompilerContext): ExitCode? {
        context.orchestration.send(TestEvent("orchestration.port=${context.orchestration.port.awaitOrThrow()}"))
        context.orchestration.send(TestEvent(context.requests))
        return null
    }
}
