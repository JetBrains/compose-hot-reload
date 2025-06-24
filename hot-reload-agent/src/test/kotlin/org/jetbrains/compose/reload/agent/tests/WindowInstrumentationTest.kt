/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent.tests

import org.jetbrains.compose.reload.agent.WindowInstrumentation
import org.jetbrains.compose.reload.analysis.ClassId
import org.jetbrains.compose.reload.analysis.ClassNode
import org.jetbrains.compose.reload.analysis.Ids
import org.jetbrains.compose.reload.analysis.MethodId
import org.objectweb.asm.tree.MethodInsnNode
import java.io.File
import java.net.URLClassLoader
import java.util.zip.ZipFile
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class WindowInstrumentationTest {
    @Test
    fun `test - transform WindowKt`() {
        val composeRuntimePath = (System.getProperty("compose.runtime.path") ?: error("Missing 'compose.runtime.path'"))
            .split(File.pathSeparator).map { Path(it) }

        val hotReloadRuntimePath = (System.getProperty("hot.reload.runtime.path")
            ?: error("Missing 'hot.reload.runtime.path'"))
            .split(File.pathSeparator).map { Path(it) }

        val runtimePath = composeRuntimePath + hotReloadRuntimePath

        val composeClassLoader = URLClassLoader(runtimePath.map { it.toUri().toURL() }.toTypedArray(), null)

        val transformedClasses = composeRuntimePath.flatMap { jar ->
            ZipFile(jar.toFile()).use { zip ->
                zip.entries().toList().filter { it.name.endsWith(".class") }.mapNotNull { classEntry ->
                    val bytecode = zip.getInputStream(classEntry).readAllBytes()
                    val classNode = ClassNode(bytecode)

                    classNode to ClassNode(
                        (WindowInstrumentation.transform(
                            loader = composeClassLoader,
                            className = classEntry.name,
                            classBeingRedefined = null,
                            protectionDomain = null,
                            classfileBuffer = bytecode
                        ) ?: return@mapNotNull null)
                    )
                }
            }
        }

        assertEquals(2, transformedClasses.size, "Expected only one class to be transformed. Found $transformedClasses")

        val originalMethods = setOf(
            Ids.ComposeWindow.setContent_3,
            Ids.ComposeDialog.setContent_3,
        )
        transformedClasses.forEach { (original, transformed) ->
            val originalCallFound = original.methods.any { originalMethod ->
                originalMethod.instructions.any { originalInsn ->
                    originalInsn is MethodInsnNode && originalInsn.methodId in originalMethods
                }
            }
            if (!originalCallFound) {
                fail("Expected original Window.setContent call to be found in ${original.name}")
            }

            transformed.methods.forEach { transformedMethod ->
                transformedMethod.instructions.forEach { transformedInsn ->
                    if (transformedInsn is MethodInsnNode && transformedInsn.methodId in originalMethods) {
                        fail("Unexpected Window.setContent call in ${transformed.name}#${transformedMethod.name}")
                    }
                }
            }

            val interceptedCallFound = transformed.methods.any { transformedMethod ->
                transformedMethod.instructions.any { transformedInsn ->
                    transformedInsn is MethodInsnNode && transformedInsn.name == "setContent" &&
                        transformedInsn.owner == "org/jetbrains/compose/reload/jvm/JvmDevelopmentEntryPoint"
                }
            }

            if (!interceptedCallFound) {
                fail("Expected intercepted Window.setContent call to be found in ${transformed.name}")
            }
        }
    }
}

private val MethodInsnNode.methodId: MethodId
    get() = MethodId(
        classId = ClassId(owner),
        methodName = name,
        methodDescriptor = desc,
    )
