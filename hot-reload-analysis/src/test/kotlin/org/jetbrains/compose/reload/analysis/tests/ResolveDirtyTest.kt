/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis.tests

import org.intellij.lang.annotations.Language
import org.jetbrains.compose.reload.analysis.ClassInfo
import org.jetbrains.compose.reload.analysis.ResolvedDirtyScopes
import org.jetbrains.compose.reload.analysis.MutableApplicationInfo
import org.jetbrains.compose.reload.analysis.resolveDirtyScopes
import org.jetbrains.compose.reload.core.Context
import org.jetbrains.compose.reload.core.testFixtures.Compiler
import org.jetbrains.compose.reload.core.testFixtures.CompilerProvider
import org.jetbrains.compose.reload.core.testFixtures.WithCompiler
import org.jetbrains.compose.reload.core.testFixtures.compile
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveDirtyTests {
    @ResolveDirtyTest
    fun `test - Composable depending on a static function`(fixture: ResolveDirtyTestFixture) {
        fixture.withCode(
            "Foo.kt", """
                import androidx.compose.runtime.*
                fun bar() = 42
                
                @Composable
                fun Foo() {
                   bar()
                }
        """.trimIndent()
        )

        fixture.withRedefinedCode(
            "Foo.kt", """
               import androidx.compose.runtime.*
               fun bar() = 43
               
               @Composable
               fun Foo() {
                   bar()
               }
           """
        )

        val dirty = fixture.resolveDirty()
        dirty.assertDirtyMethodNames("bar", "Foo")
    }

    @ResolveDirtyTest
    fun `test - Composable depending on Composable function`(fixture: ResolveDirtyTestFixture) {
        fixture.withCode(
            "Foo.kt", """
                import androidx.compose.runtime.*
                
                @Composable
                fun Bar() {
                
                }
                
                @Composable
                fun Foo() {
                   Bar()
                }
        """.trimIndent()
        )

        fixture.withRedefinedCode(
            "Foo.kt", """
                import androidx.compose.runtime.*
                
                @Composable
                fun Bar() {
                    println("Servus Weini!")
                }
                
                @Composable
                fun Foo() {
                   Bar()
                }
           """
        )

        val dirty = fixture.resolveDirty()
        dirty.assertDirtyMethodNames("Bar")
    }

    @ResolveDirtyTest
    fun `test - Composable depending on Composable function - which gets transitively dirty`(fixture: ResolveDirtyTestFixture) {
        fixture.withCode(
            "Foo.kt", """
                import androidx.compose.runtime.*
                
                fun x() = 42

                @Composable
                fun Bar() {
                    x()
                }
                
                @Composable
                fun Foo() {
                   Bar()
                }
        """.trimIndent()
        )

        fixture.withRedefinedCode(
            "Foo.kt", """
                import androidx.compose.runtime.*
                
                fun x() = 43

                @Composable
                fun Bar() {
                    x()
                }
                
                @Composable
                fun Foo() {
                   Bar()
                }
           """
        )

        val dirty = fixture.resolveDirty()
        dirty.assertDirtyMethodNames("Bar", "x")
    }

    @ResolveDirtyTest
    fun `test - Composable depending on static field`(fixture: ResolveDirtyTestFixture) {
        fixture.withCode(
            "Foo.kt", """
                import androidx.compose.runtime.*
                
                val bar = 42
                
                @Composable
                fun Foo() {
                   bar
                }

                @Composable
                fun Decoy() {
                    Foo()
                }
            """
        )

        fixture.withRedefinedCode(
            "Foo.kt", """
                import androidx.compose.runtime.*
                
                val bar = 43
                
                @Composable
                fun Foo() {
                   bar
                }

                @Composable
                fun Decoy() {
                    Foo()
                }
            """
        )

        fixture.resolveDirty()
            .assertDirtyMethodNames("<clinit>", "getBar", "Foo")
    }

    @ResolveDirtyTest
    fun `test - Composable depending on static const`(fixture: ResolveDirtyTestFixture) {
        fixture.withCode(
            "Foo.kt", """
                import androidx.compose.runtime.*
                
                const val bar = 42
                
                @Composable
                fun Foo() {
                   println(bar)
                }

                @Composable
                fun Decoy() {
                    Foo()
                }
            """
        )

        fixture.withRedefinedCode(
            "Foo.kt", """
                import androidx.compose.runtime.*
                
                const val bar = 43
                
                @Composable
                fun Foo() {
                   println(bar)
                }

                @Composable
                fun Decoy() {
                    Foo()
                }
            """
        )

        fixture.resolveDirty()
            .assertDirtyMethodNames("Foo")
    }

    @ResolveDirtyTest
    fun `test - chain - 1`(fixture: ResolveDirtyTestFixture) {
        fixture.withCode(
            "Foo.kt", """
                import androidx.compose.runtime.*
                
                fun xInitial() = 42
                var xField = xInitial()
                fun x() = xField + 2
                
                @Composable
                fun Foo() {
                    x()
                }
                
                @Composable
                fun Decoy() {
                    Foo()
                }
            """
        )

        fixture.withRedefinedCode(
            "Foo.kt", """
                import androidx.compose.runtime.*
                
                fun xInitial() = 420
                var xField = xInitial()
                fun x() = xField + 2
                
                @Composable
                fun Foo() {
                    x()
                }
                
                @Composable
                fun Decoy() {
                    Foo()
                }
            """
        )

        fixture.resolveDirty()
            .assertDirtyMethodNames("Foo", "getXField", "setXField", "xInitial", "x", "<clinit>")
    }

    private fun ResolvedDirtyScopes.assertDirtyMethodNames(vararg methodNames: String) {
        assertEquals(
            methodNames.toSet(), dirtyMethodIds.keys.map { it.methodName }.toSet(),
            "Dirty Method Names",
        )
    }
}

@Test
@ExtendWith(ResolveDirtyTestFixtureProvider::class)
@WithCompiler
annotation class ResolveDirtyTest

class ResolveDirtyTestFixtureProvider : ParameterResolver {
    override fun supportsParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Boolean {
        return parameterContext?.parameter?.type == ResolveDirtyTestFixture::class.java
    }

    override fun resolveParameter(
        parameterContext: ParameterContext, extensionContext: ExtensionContext
    ): ResolveDirtyTestFixture? {
        val compiler = CompilerProvider().resolveParameter(parameterContext, extensionContext)
        return ResolveDirtyTestFixture(compiler)
    }
}

class ResolveDirtyTestFixture(
    private val compiler: Compiler
) {
    private val application = MutableApplicationInfo()
    private val redefinition = MutableApplicationInfo()

    fun withCode(fileName: String, @Language("kotlin") code: String) {
        compiler.compile(fileName to code).forEach { (name, bytecode) ->
            ClassInfo(bytecode) ?: error("Failed to parse class info: $name")
            application.add(ClassInfo(bytecode) ?: error("Failed to parse class info: $name"))
        }
    }

    fun withRedefinedCode(fileName: String, @Language("kotlin") code: String) {
        compiler.compile(fileName to code).forEach { (name, bytecode) ->
            ClassInfo(bytecode) ?: error("Failed to parse class info: $name")
            redefinition.add(ClassInfo(bytecode) ?: error("Failed to parse class info: $name"))
        }
    }

    fun resolveDirty(): ResolvedDirtyScopes {
        return Context().resolveDirtyScopes(application, redefinition)
    }
}
