/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis.tests

import org.jetbrains.compose.reload.analysis.plusAssign
import org.jetbrains.compose.reload.analysis.renderInstructionTree
import org.jetbrains.compose.reload.analysis.util.renderAsmTree
import org.jetbrains.compose.reload.analysis.util.renderSourceTree
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
class InstructionTreeParserTest {

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

    @Test
    fun `test - #311 startRestartGroup with LineNumberNode`(compiler: Compiler, testInfo: TestInfo) = doTest(
        compiler, testInfo, """
            import androidx.compose.runtime.*
            
            @Composable
            fun Foot(param: String): String {
                return key(param) {
                    if (param.isEmpty()) {
                        "Empty"
                    } else {
                        val state = remember { mutableStateOf("") }
                        state.value
                    }
                }
            }
        """.trimIndent(),
    )

    @Test
    fun `test - composable with switch control flow`(compiler: Compiler, testInfo: TestInfo) = doTest(
        compiler, testInfo, """
            import androidx.compose.runtime.*
            import androidx.compose.material3.Text
            
            @Composable
            fun Foo(value: Int) {
                when (value) {
                    0 -> {
                        Text("Hello")
                    }
                    10 -> {
                        Text("Hello 10")
                    }
                    else -> {
                        Text("Hello else")
                    }
                }
            }
    """.trimIndent()
    )

    @Test
    fun `test - composable with switch eager return`(compiler: Compiler, testInfo: TestInfo) = doTest(
        compiler, testInfo, """
            import androidx.compose.runtime.*
            import androidx.compose.material3.Text
            
            @Composable
            fun value(): Int? = 10
            
            @Composable
            fun Foo() {
                val value = value() ?: return
                when (value) {
                    0 -> {
                        Text("Hello")
                    }
                    10 -> {
                        Text("Hello 10")
                    }
                    else -> {
                        Text("Hello else")
                    }
                }
            }
        """.trimIndent()
    )

    @Test
    fun `test - composable with switch local return`(compiler: Compiler, testInfo: TestInfo) = doTest(
        compiler, testInfo, """
            import androidx.compose.runtime.*
            import androidx.compose.material3.Text
            
            @Composable
            fun Foo(a: Int, b: Int) {
                when (a) {
                    0 -> {
                        Text("Hello")
                    }
                    10 -> Bar {
                        Text("B")
                        if(b > 0) return@Bar
                        if(b > 10) return@Foo
                        Text("C")
                    }
                    else -> {
                        Text("Hello else")
                    }
                }
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
        val directory = Path("src/test/resources/instructionTree")
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
                this += renderInstructionTree(bytecode)
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

        val renderedSource = run {
            var current = code
            compiled.forEach { (_, bytecode) ->
                current = renderSourceTree(current, bytecode)
            }
            current
        }.sanitized()

        val expectTreeFile = directory.resolve("instructions-tree.txt")
        val expectAsmFile = directory.resolve("instructions-asm.txt")
        val expectSourceFile = directory.resolve("instructions-source.txt")

        val file2Render = mapOf(
            expectTreeFile to renderedTree,
            expectAsmFile to renderedAsm,
            expectSourceFile to renderedSource,
        )

        if (TestEnvironment.updateTestData) {
            file2Render.forEach { (file, render) ->
                file.createParentDirectories().writeText(render)
            }
            return
        }

        file2Render.forEach { (file, render) ->
            if (!file.exists()) {
                file.createParentDirectories().writeText(render)
                error("Render '${file.toUri()}' did not exist; Generated")
            }

            if (file.readText().sanitized() != render) {
                file.resolveSibling(file.nameWithoutExtension + "-actual.txt").writeText(render)
                error("Render '${file.toUri()}' did not match")
            }
        }
    }
}
