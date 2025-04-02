/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis.tests

import org.jetbrains.compose.reload.analysis.plusAssign
import org.jetbrains.compose.reload.analysis.renderRuntimeInstructionTree
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

    private fun doTest(compiler: Compiler, testInfo: TestInfo, code: String) {
        val directory = Path("src/test/resources/runtimeInstructionTree")
            .resolve(testInfo.testClass.get().name.asFileName())
            .resolve(testInfo.testMethod.get().name.asFileName())

        val compiled = compiler.compile(mapOf("Test.kt" to code))
        compiled.forEach { (name, bytecode) ->
            directory.resolve("classes").resolve(name)
                .createParentDirectories().writeBytes(bytecode)
        }

        val rendered = buildString {
            appendLine(" /* Code */")
            appendLine(code)
            appendLine()

            appendLine(" /* Tree */ ")
            compiled.forEach { (_, bytecode) ->
                this += renderRuntimeInstructionTree(bytecode)
            }
        }.sanitized()

        val expectFile = directory.resolve("runtime-instructions-tree.txt")
        if (TestEnvironment.updateTestData) {
            expectFile.createParentDirectories().writeText(rendered)
            return
        }

        if (!expectFile.exists()) {
            expectFile.createParentDirectories().writeText(rendered)
            error("Runtime Instruction Tree '${expectFile.toUri()}' did not exist; Generated")
        }

        if (expectFile.readText().sanitized() != rendered) {
            expectFile.resolveSibling(expectFile.nameWithoutExtension + "-actual.txt").writeText(rendered)
            error("Runtime Instruction Tree '${expectFile.toUri()}' did not match")
        }
    }
}
