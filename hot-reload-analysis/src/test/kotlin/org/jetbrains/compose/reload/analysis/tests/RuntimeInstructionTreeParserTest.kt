package org.jetbrains.compose.reload.analysis.tests

import org.jetbrains.compose.reload.analysis.plusAssign
import org.jetbrains.compose.reload.analysis.renderRuntimeInstructionTree
import org.jetbrains.compose.reload.core.testFixtures.Compiler
import org.jetbrains.compose.reload.core.testFixtures.TestEnvironment
import org.jetbrains.compose.reload.core.testFixtures.WithCompiler
import org.jetbrains.compose.reload.core.testFixtures.asFileName
import org.jetbrains.compose.reload.core.testFixtures.sanitized
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import kotlin.io.path.*

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
