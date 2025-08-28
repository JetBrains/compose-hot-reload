/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration

import org.jetbrains.compose.reload.analysis.ClassId
import org.jetbrains.compose.reload.analysis.ClassNode
import org.jetbrains.compose.reload.core.testFixtures.sanitized
import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import org.jetbrains.compose.reload.test.core.TestEnvironment
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.io.Serializable
import java.lang.reflect.Modifier
import java.util.zip.ZipInputStream
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.kotlinProperty
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.fail

class SerialVersionUIDTest {

    @OptIn(InternalHotReloadTestApi::class)
    @Test
    fun `test - each serializable class has a serialVersionUID`() {

        val violations = mutableListOf<String>()
        val serialVersionUIDs = mutableMapOf<String, Long>()

        fun ClassNode.check() {
            if (!name.startsWith("org/jetbrains/compose/reload/orchestration")) return
            val thisClass = Class.forName(ClassId(this).toFqn()).kotlin
            if (!thisClass.isSubclassOf(Serializable::class)) return
            val serialVersionUID = thisClass.java.fields.find { it.name == "serialVersionUID" }
            if (serialVersionUID == null) {
                violations.add("$thisClass: Missing 'serialVersionUID'")
                return
            }

            if (serialVersionUID.modifiers and Modifier.STATIC == 0) {
                violations.add("$thisClass: 'serialVersionUID' should be static")
            }

            if (!serialVersionUID.kotlinProperty!!.isConst) {
                violations.add("$thisClass: 'serialVersionUID' should be a const")
            }

            if (!serialVersionUID.kotlinProperty!!.isConst) {
                violations.add("$thisClass: 'serialVersionUID' should be a const")
            }

            if (serialVersionUID.kotlinProperty!!.returnType != typeOf<Long>()) {
                violations.add("$thisClass: 'serialVersionUID' should be a Long")
            }

            serialVersionUIDs.put(name, serialVersionUID.kotlinProperty!!.call(null) as Long)
        }

        val classpath = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)
        classpath.forEach { file ->
            if (file.isDirectory) {
                file.walk().forEach { file ->
                    if (file.extension == "class") {
                        ClassNode(file.readBytes()).check()

                    }
                }
            }

            if (file.isFile && file.extension == "jar") {
                ZipInputStream(file.inputStream()).use {
                    while (true) {
                        val entry = it.nextEntry ?: break
                        if (entry.name.endsWith(".class")) {
                            ClassNode(it.readBytes()).check()
                        }
                    }
                }
            }
        }

        if (violations.isNotEmpty()) fail(violations.joinToString("\n"))

        val expectFile = Path("src/test/resources/testData/serialVersionUIDs.txt")
        expectFile.createParentDirectories()

        val actualText = serialVersionUIDs.entries.sortedBy { it.key }
            .joinToString("\n") { "${it.key} = ${it.value}" }
            .sanitized()


        if (!expectFile.exists() || TestEnvironment.updateTestData) {
            expectFile.writeText(actualText)
            if (!TestEnvironment.updateTestData) fail("${expectFile.toUri()} did not exist; Generated")
        }

        if (expectFile.readText().sanitized() != actualText) {
            val actualFile = expectFile.resolveSibling(
                expectFile.nameWithoutExtension + "-actual." + expectFile.extension
            )
            actualFile.writeText(actualText)
            fail("${expectFile.toUri()} did not match\n${actualFile.toUri()}")
        }
    }


}
