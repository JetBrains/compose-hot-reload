package org.jetbrains.compose.reload.analysis.tests

import org.jetbrains.compose.reload.analysis.RuntimeInfo
import org.jetbrains.compose.reload.analysis.RuntimeScopeInfo
import org.jetbrains.compose.reload.analysis.SpecialComposeGroupKeys
import org.jetbrains.compose.reload.analysis.javap
import org.jetbrains.compose.reload.analysis.render
import org.jetbrains.compose.reload.core.testFixtures.*
import org.jetbrains.compose.reload.core.withClosure
import org.jetbrains.kotlin.util.prefixIfNot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import kotlin.io.path.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.fail

@WithCompiler
class RuntimeInfoTest {

    @Test
    fun `test - simple composable`(compiler: Compiler, testInfo: TestInfo) {
        checkRuntimeInfo(
            testInfo, compiler, mapOf(
                "Foo.kt" to """
                import androidx.compose.runtime.*
                
                @Composable 
                fun Foo() {
                    print("foo")
                }
            """.trimIndent()
            )
        )
    }

    @Test
    fun `test - higher order composable`(compiler: Compiler, testInfo: TestInfo) {
        checkRuntimeInfo(
            testInfo, compiler, mapOf(
                "Foo.kt" to """
                import androidx.compose.runtime.*
                
                @Composable
                fun Bar(child: @Composable () -> Unit) {
                    child()
                }
                
                @Composable 
                fun Foo() {
                    Bar {
                        print("foo")
                    }
                }
            """.trimIndent()
            )
        )
    }


    @Test
    fun `test - inlined nesting`(compiler: Compiler, testInfo: TestInfo) {
        checkRuntimeInfo(
            testInfo, compiler, mapOf(
                "Foo.kt" to """
                import androidx.compose.runtime.*
                import androidx.compose.material3.Text
                
                @Composable
                inline fun Bar(child: @Composable () -> Unit) {
                    print("Bar")
                    child()
                }
                
                @Composable 
                fun Foo() {
                    Bar {
                        Bar {
                            Text("First Text")
                        }
                    }
                    
                    Bar {
                        Text("Second Text")
                    }
                }
            """.trimIndent()
            )
        )
    }


    @Test
    fun `test - canvas`(compiler: Compiler, testInfo: TestInfo) {
        val code = """
                import androidx.compose.foundation.Canvas
                import androidx.compose.foundation.layout.Box
                import androidx.compose.foundation.layout.fillMaxSize
                import androidx.compose.foundation.layout.size
                import androidx.compose.runtime.Composable
                import androidx.compose.ui.Alignment
                import androidx.compose.ui.Modifier
                import androidx.compose.ui.geometry.Offset
                import androidx.compose.ui.graphics.Color
                import androidx.compose.ui.unit.dp

                @Composable
                fun App() {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Canvas(Modifier.size(200.dp, 200.dp)) {

                            drawLine(
                                color = Color.Blue,
                                Offset(0f, 0f),
                                Offset(300f, 200f),
                                10f,
                            )
                        }
                    }
                }
            """.trimIndent()

        val info10f = checkRuntimeInfo(testInfo, compiler, mapOf("Foo.kt" to code), name = "10f")
        val info11f = checkRuntimeInfo(testInfo, compiler, mapOf("Foo.kt" to code.replace("10f", "11f")), name = "11f")
        assertNotEquals(info10f, info11f)
    }


    @Test
    fun `test - column row`(compiler: Compiler, testInfo: TestInfo) {
        checkRuntimeInfo(
            testInfo, compiler, mapOf(
                "Foo.kt" to """
                import androidx.compose.foundation.Canvas
                import androidx.compose.foundation.layout.*
                import androidx.compose.runtime.Composable
                import androidx.compose.material3.Text

                @Composable
                fun Foo() {
                    Column {
                        Text("First Text")
                        Text("Second Text")
                        Row {
                            Text("Row A")
                            Text("Row B")
                        }
                    }
                }
            """.trimIndent()
            )
        )
    }


    @Test
    fun `test - nested card`(compiler: Compiler, testInfo: TestInfo) {
        checkRuntimeInfo(
            testInfo, compiler, mapOf(
                "Foo.kt" to """
                import androidx.compose.foundation.layout.*
                import androidx.compose.material3.Card
                import androidx.compose.material3.Text
                import androidx.compose.runtime.Composable

                @Composable
                fun Foo() {
                    Card {
                        Text("First Text")
                        Text("Second Text")
                        Card {
                            Text("Inner A")
                            Text("Inner B")
                        }
                    }
                }
            """.trimIndent()
            )
        )
    }

    @Test
    fun `test - remember`(compiler: Compiler, testInfo: TestInfo) {
        val runtimeInfo = checkRuntimeInfo(
            testInfo, compiler.withOptions(CompilerOption.OptimizeNonSkippingGroups to false), mapOf(
                "Foo.kt" to """
                import androidx.compose.runtime.*
                
                @Composable
                fun Foo() {
                    remember { "Hello" }
                    remember { 1902 }
                }
            """.trimIndent()
            )
        ) ?: fail("Missing 'runtimeInfo'")

        val (_, scopes) = runtimeInfo.methods.entries.find { (key, _) -> key.methodName == "Foo" }
            ?: fail("Missing method 'Foo'")

        /* Method entry point scope */
        val root = scopes.single().children.single()
        assertEquals(2, root.children.size, "Expected 2 remember groups in 'Foo'. Found ${root.render()}")

        root.children.forEach { scope ->
            assertEquals(
                SpecialComposeGroupKeys.remember, scope.tree.group,
                "Expected remember group key to equal ${SpecialComposeGroupKeys.remember.key}"
            )
        }
    }

    @Test
    fun `test - whitespace do not affect code hashes`(compiler: Compiler, testInfo: TestInfo) {
        val code = """
            import androidx.compose.runtime.*
            import androidx.compose.material3.Text
                
            @Composable
            fun Foo() {
                //<foo>
                Text("Foo")
                Bar()
            }
            
            @Composable
            fun Bar() {
                //<bar>
                Text("Bar")
            }
        """.trimIndent()

        val runtimeInfoBefore = checkRuntimeInfo(
            testInfo, compiler.withOptions(CompilerOption.OptimizeNonSkippingGroups to false),
            mapOf("Foo.kt" to code), name = "before"
        ) ?: fail("Missing 'runtimeInfo'")


        val codeAfter = code.replace("//<foo>", "\n\n\n\n")
            .replace("//<bar>", "\n\n\n\n")

        val runtimeInfoAfter = checkRuntimeInfo(
            testInfo, compiler.withOptions(CompilerOption.OptimizeNonSkippingGroups to false),
            mapOf("Foo.kt" to codeAfter), name = "after"
        ) ?: fail("Missing 'runtimeInfo'")

        assertNotEquals(code, codeAfter)

        val beforeScopes = runtimeInfoBefore.scopes.withClosure<RuntimeScopeInfo> { scope -> scope.children }.toList()
        val afterScopes = runtimeInfoAfter.scopes.withClosure<RuntimeScopeInfo> { scope -> scope.children }.toList()

        beforeScopes.forEachIndexed { index, beforeScope ->
            val afterScope = afterScopes[index]
            assertEquals(beforeScope.tree.group, afterScope.tree.group)
            assertEquals(beforeScope.tree.type, afterScope.tree.type)
            assertEquals(beforeScope.dependencies, afterScope.dependencies)
            assertEquals(beforeScope.hash, afterScope.hash)
        }
    }
}

private fun checkRuntimeInfo(
    testInfo: TestInfo, compiler: Compiler, code: Map<String, String>, name: String? = null
): RuntimeInfo? {
    val output = compiler.compile(code)
    val runtimeInfo = output.values
        .map { bytecode -> RuntimeInfo(bytecode) }
        .reduce { info, next -> RuntimeInfo(info?.scopes.orEmpty() + next?.scopes.orEmpty()) }

    fun String.asFileName(): String = replace("""\\W+""", "_")

    val actualContent = buildString {
        appendLine("/*")
        appendLine(" Original Code:")
        appendLine("*/")
        appendLine()

        code.forEach { (path, code) ->
            appendLine("// $path")
            appendLine(code)

        }
        appendLine()

        appendLine("/*")
        appendLine(" Runtime Info:")
        appendLine("*/")
        appendLine()
        appendLine(runtimeInfo?.render()?.escapeWhiteSpace())
    }.escapeWhiteSpace()

    val directory = Path("src/test/resources/runtimeInfo")
        .resolve(testInfo.testClass.get().name.asFileName().replace(".", "/"))
        .resolve(testInfo.testMethod.get().name.asFileName())

    javap(output).forEach { (name, javap) ->
        directory.resolve("javap").resolve(name.asFileName() + "-javap.txt").createParentDirectories().writeText(javap)
    }

    val expectFile = directory.resolve("runtime-info${name?.prefixIfNot("-").orEmpty()}.txt")
    if (TestEnvironment.updateTestData) {
        expectFile.writeText(actualContent)
        return runtimeInfo
    }

    if (!expectFile.exists()) {
        expectFile.createParentDirectories().writeText(actualContent)
        fail("Runtime Info '${expectFile.toUri()}' did not exist; Generated")
    }

    val expectContent = expectFile.readText().escapeWhiteSpace()
    if (expectContent != actualContent) {
        expectFile.resolveSibling(expectFile.nameWithoutExtension + "-actual.txt").writeText(actualContent)
        fail("Runtime Info '${expectFile.toUri()}' did not match")
    }

    return runtimeInfo
}


private fun String.escapeWhiteSpace(): String {
    return lines().joinToString("\n").trim()
}