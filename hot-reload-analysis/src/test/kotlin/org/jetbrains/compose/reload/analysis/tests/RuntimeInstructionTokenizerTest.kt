/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis.tests

import org.jetbrains.compose.reload.analysis.ClassNode
import org.jetbrains.compose.reload.analysis.render
import org.jetbrains.compose.reload.analysis.tokenizeRuntimeInstructions
import org.jetbrains.compose.reload.analysis.withIndent
import org.jetbrains.compose.reload.core.asFileName
import org.jetbrains.compose.reload.core.leftOr
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
class RuntimeInstructionTokenizerTest {

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
    fun `test - composable with eager return`(compiler: Compiler, testInfo: TestInfo) = doTest(
        compiler, testInfo, """
            import androidx.compose.runtime.*
            import androidx.compose.material3.Text
            
            @Composable
            fun value(): Boolean? = false
            
            @Composable
            fun Foo() {
                val value = value() ?: return
                if(value) {
                    Text("Hello")
                }
            }
        """.trimIndent()
    )

    private fun doTest(compiler: Compiler, testInfo: TestInfo, code: String) {
        val directory = Path("src/test/resources/runtimeInstructionTokens")
            .resolve(testInfo.testClass.get().name.asFileName())
            .resolve(testInfo.testMethod.get().name.asFileName())

        val compiled = compiler.compile(mapOf("Foo.kt" to code))

        compiled.forEach { (name, bytecode) ->
            val target = directory.resolve("classes").resolve(name)
            target.createParentDirectories().writeBytes(bytecode)
        }

        val rendered = buildString {
            appendLine(" /* Code */")
            appendLine(code)
            appendLine()


            appendLine(" /* Tokens */")
            compiled.toSortedMap().forEach { (_, bytecode) ->
                val classNode = ClassNode(bytecode)
                appendLine("class ${classNode.name} {")
                withIndent {
                    classNode.methods.sortedBy { node -> node.name + node.desc }.forEach { methodNode ->
                        val tokens = tokenizeRuntimeInstructions(methodNode.instructions.toList())
                            .leftOr { right -> error("Failed to tokenize instructions: $right") }
                        appendLine(methodNode.render(tokens))
                    }
                }
                appendLine("}")
            }
        }.sanitized()

        val actualFile = directory.resolve("runtime-instructions-tokens.txt")

        if (TestEnvironment.updateTestData) {
            actualFile.createParentDirectories().writeText(rendered)
            return
        }

        if (!actualFile.exists()) {
            actualFile.createParentDirectories().writeText(rendered)
            error("Runtime Instruction Tokens '${actualFile.toUri()}' did not exist; Generated")
        }

        val actualContent = actualFile.readText().sanitized()
        if (actualContent != rendered) {
            actualFile.resolveSibling(actualFile.nameWithoutExtension + "-actual.txt").writeText(rendered)
            error("Runtime Instruction Tokens '${actualFile.toUri()}' did not match")
        }
    }
}
