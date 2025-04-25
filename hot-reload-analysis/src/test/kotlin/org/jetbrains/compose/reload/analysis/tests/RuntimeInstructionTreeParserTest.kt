/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis.tests

import org.jetbrains.compose.reload.analysis.plusAssign
import org.jetbrains.compose.reload.analysis.renderRuntimeInstructionTree
import org.jetbrains.compose.reload.analysis.util.renderAsmTree
import org.jetbrains.compose.reload.core.asFileName
import org.jetbrains.compose.reload.core.testFixtures.Compiler
import org.jetbrains.compose.reload.core.testFixtures.WithCompiler
import org.jetbrains.compose.reload.core.testFixtures.sanitized
import org.jetbrains.compose.reload.test.core.TestEnvironment
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

@WithCompiler
class RuntimeInstructionTreeParserTest {

    @Test
    fun `test - simple composable`(compiler: Compiler, testInfo: TestInfo) = doTest(
        compiler, testInfo, """
            import androidx.compose.runtime.*
            import androidx.compose.material3.Text
            
            @Composable
            fun Foo() {
                Text("Hello")
            }
            """.trimIndent()
    )

    @Test
    fun `test - composable with control flow`(compiler: Compiler, testInfo: TestInfo) = doTest(
        compiler, testInfo, """
            import androidx.compose.runtime.*
            import androidx.compose.material3.Text
            
            @Composable
            fun Foo(value: Int) {
                if(value > 0) {
                    Text("Hello")
                } else {
                    if(value > 10) {
                        Text("Hello 10")
                    }
                    
                    Text("Hello else")
                }
            }
    """.trimIndent()
    )

    @Test
    fun `test - #123 - composable with local return`(compiler: Compiler, testInfo: TestInfo) = doTest(
        compiler, testInfo, """
            import androidx.compose.runtime.*
            import androidx.compose.material3.Text
             
            @Composable
            fun Foo(value: Int) {
                Text("A")
                if(value > -12) {
                    Bar {
                        Text("B")
                        if(value > 0) return@Bar
                        if(value > 10) return@Foo
                        Text("C")
                    }
                }
                
                Text("D")
            }
            
            @Composable
            inline fun Bar(content: @Composable () -> Unit) {
                Text("Bar A")
                content()
                Text("Bar B")
            }
    """.trimIndent()
    )

    @Test
    fun `test - #152 - composable with eager return`(compiler: Compiler, testInfo: TestInfo) = doTest(
        compiler, testInfo, """
            import androidx.compose.runtime.*
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.Text
                         
            @Composable
            fun Value(): String? {
                return "value: 0"
            }
          
            @Composable
            fun Foo() {
                val value = Value() ?: return
                Column {
                    Text(value)
                }
            }
    """.trimIndent()
    )

    private fun doTest(compiler: Compiler, testInfo: TestInfo, code: String) {
        val directory = Path("src/test/resources/runtimeInstructionTree")
            .resolve(testInfo.testClass.get().name.asFileName())
            .resolve(testInfo.testMethod.get().name.asFileName())

        val compiled = compiler.compile(mapOf("Test.kt" to code))
        compiled.forEach { (name, bytecode) ->
            directory.resolve("classes").resolve(name)
                .createParentDirectories().writeBytes(bytecode)
        }

        val renderedTree = buildString {
            appendLine(" /* Code */")
            appendLine(code)
            appendLine()

            appendLine(" /* Tree */ ")
            compiled.forEach { (_, bytecode) ->
                this += renderRuntimeInstructionTree(bytecode)
            }
        }.sanitized()

        val renderedAsm = buildString {
            appendLine(" /* Code */")
            appendLine(code)
            appendLine()

            appendLine(" /* Tree */ ")
            compiled.forEach { (_, bytecode) ->
                this += renderAsmTree(bytecode)
            }
        }.sanitized()

        val expectTreeFile = directory.resolve("runtime-instructions-tree.txt")
        val expectAsmFile = directory.resolve("runtime-instructions-asm.txt")
        if (TestEnvironment.updateTestData) {
            expectTreeFile.createParentDirectories().writeText(renderedTree)
            expectAsmFile.createParentDirectories().writeText(renderedAsm)
            return
        }

        if (!expectTreeFile.exists()) {
            expectTreeFile.createParentDirectories().writeText(renderedTree)
            error("Runtime Instruction Tree '${expectTreeFile.toUri()}' did not exist; Generated")
        }

        if (expectTreeFile.readText().sanitized() != renderedTree) {
            expectTreeFile.resolveSibling(expectTreeFile.nameWithoutExtension + "-actual.txt").writeText(renderedTree)
            error("Runtime Instruction Tree '${expectTreeFile.toUri()}' did not match")
        }

        if (!expectAsmFile.exists()) {
            expectAsmFile.createParentDirectories().writeText(renderedAsm)
            error("Runtime Asm Tree '${expectAsmFile.toUri()}' did not exist; Generated")
        }

        if (expectAsmFile.readText().sanitized() != renderedAsm) {
            expectAsmFile.resolveSibling(expectAsmFile.nameWithoutExtension + "-actual.txt").writeText(renderedAsm)
            error("Runtime Asm Tree '${expectAsmFile.toUri()}' did not match")
        }
    }
}
