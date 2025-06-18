/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.tests

import com.sun.jdi.Bootstrap
import com.sun.jdi.VirtualMachine
import com.sun.jdi.connect.Connector
import com.sun.jdi.connect.ListeningConnector
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.test.gradle.BuildGradleKtsExtension
import org.jetbrains.compose.reload.test.gradle.BuildMode
import org.jetbrains.compose.reload.test.gradle.ExtendBuildGradleKts
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.TestedBuildMode
import org.jetbrains.compose.reload.test.gradle.checkScreenshot
import org.jetbrains.compose.reload.test.gradle.initialSourceCode
import org.jetbrains.compose.reload.utils.QuickTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.walk
import kotlin.test.DefaultAsserter.fail

/**
 * Tests Hot Reload using a debugger connection (jdwp).
 * Note: This test cannot be debugged easily, as we are using the debugger already for the test.
 *
 * This test will:
 * 1 ) Start a Debug Server
 * 2) Tell the 'test application' to connect to this debug server
 * 3) Check a regular 'Before' screenshot
 * 4) Manually transform the existing bytecode, replacing the "Before" String with "After"
 * 5) Manually issue a reload command using 'JDWP'
 * 6) Check an 'After' screenshot, asserting that the UI has been refreshed successfully.
 */
@ExtendBuildGradleKts(JdwpExtension::class)
@TestedBuildMode(BuildMode.Continuous)
@TestedBuildMode(BuildMode.Explicit)
@Execution(ExecutionMode.SAME_THREAD, reason = "Sharing the virtualMachineManager")
@QuickTest
class JdwpIntegrationTest {

    val connector: ListeningConnector = run {
        val vmManager = Bootstrap.virtualMachineManager()
        vmManager.listeningConnectors().first { it.transport().name() == "dt_socket" }
    }

    val arguments: Map<String, Connector.Argument> = connector.defaultArguments()

    lateinit var address: String

    @BeforeEach
    fun startListening() {
        address = connector.startListening(arguments)
    }

    @AfterEach
    fun stop() {
        connector.stopListening(arguments)
    }

    @HotReloadTest
    fun `test - reload with jdwp`(fixture: HotReloadTestFixture) = fixture.runTest {
        val virtualMachineReference = AtomicReference<VirtualMachine>(null)

        thread {
            virtualMachineReference.set(connector.accept(arguments))
            createLogger().info("VirtualMachine accepted")
            virtualMachineReference.get().resume()
        }

        fixture initialSourceCode """
            import org.jetbrains.compose.reload.test.*
            
            fun main() {
                screenshotTestApplication {
                    TestText("Before")
                }
            }
            """

        checkScreenshot("0-before")
        val virtualMachine = virtualMachineReference.get() ?: error("Missing VirtualMachine")

        if (!virtualMachine.canRedefineClasses()) {
            fail("VirtualMachine cannot redefine classes")
        }

        /* Replace the "Before" string manually with "After" */
        val classesDir = projectDir.path.resolve("build/classes/kotlin/jvm/main")
        val reloads = classesDir.walk().filter { it.extension == "class" }.associate { classFile ->
            val codeBefore = classFile.toFile().readBytes()
            val classNode = ClassNode().apply { ClassReader(codeBefore).accept(this, 0) }
            classNode.methods.forEach forEachMethod@{ methodNode ->
                methodNode.instructions.forEach forEachInstruction@{ insnNode ->
                    if (insnNode is LdcInsnNode && insnNode.cst == "Before") {
                        methodNode.instructions.insertBefore(insnNode, LdcInsnNode("After"))
                        methodNode.instructions.remove(insnNode)
                        return@forEachInstruction
                    }
                }
            }
            val codeAfter = ClassWriter(0).apply { classNode.accept(this) }.toByteArray()
            virtualMachine.classesByName(classFile.nameWithoutExtension).first() to codeAfter
        }

        /* Issue the reload request through the debugger and check the UI */
        createLogger().info("Using 'redefineClasses' through the debugger...")
        virtualMachine.redefineClasses(reloads)

        createLogger().info("Checking screenshot after the debugger command")
        checkScreenshot("1-after")
    }
}

internal object JdwpExtension : BuildGradleKtsExtension {
    override fun javaExecConfigure(context: ExtensionContext): String {
        val instance = context.testInstance.get() as JdwpIntegrationTest

        return """
            jvmArgs("-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=${instance.address}")
        """.trimIndent()
    }
}
