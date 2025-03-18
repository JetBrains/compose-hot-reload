/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis.tests

import org.jetbrains.compose.reload.analysis.ClassId
import org.jetbrains.compose.reload.analysis.ClassInfo
import org.jetbrains.compose.reload.analysis.TrackingRuntimeInfo
import org.jetbrains.compose.reload.core.testFixtures.Compiler
import org.jetbrains.compose.reload.core.testFixtures.WithCompiler
import org.jetbrains.compose.reload.core.testFixtures.compile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.fail

@WithCompiler
class TrackingRuntimeInfoTest {

    @Test
    fun `test - simple add`(compiler: Compiler) {
        val runtime = TrackingRuntimeInfo()
        val code = compiler.compile(
            "Foo.kt" to """
            import androidx.compose.runtime.*
            import androidx.compose.material3.Text
            
            @Composable
            fun Foo() {
                Text("Hello")
            }
            """.trimIndent()
        ).getValue("FooKt.class")

        runtime.add(ClassInfo(code) ?: error("Failed to parse class info"))

        if (ClassId("FooKt") !in runtime.classIndex) {
            fail("Class FooKt not found in 'classIndex'")
        }

        if (runtime.methodIndex.none { (methodId, _) ->
                methodId.classId == ClassId("FooKt") && methodId.methodName == "Foo"
            }) {
            fail("Method FooKt.Foo not found in 'methodIndex'")
        }
    }

    @Test
    fun `test - add and remove`(compiler: Compiler) {
        val runtime = TrackingRuntimeInfo()
        val runtimeHashEmpty = runtime.hashCode()

        val code = compiler.compile(
            "Foo.kt" to """
            import androidx.compose.runtime.*
            import androidx.compose.material3.Text
            
            @Composable
            fun Foo() {
                Text("Hello")
            }
            """.trimIndent()
        ).getValue("FooKt.class")

        val classInfo = ClassInfo(code) ?: error("Failed to parse class info")

        runtime.add(classInfo)
        assertNotEquals(runtimeHashEmpty, runtime.hashCode(), "Runtime hash before and after add should differ")

        runtime.remove(classInfo.classId)
        assertEquals(runtimeHashEmpty, runtime.hashCode(), "Runtime hash after and before remove should be the same")
    }

    /**
     * We'll generate a lot of sources, and compile them.
     * There will be two instances of 'runtimeInfo' which we will work with:
     * runtime1: Will get all classInfo's added straight away
     * runtime2: Will get only a subset of classInfo's added
     *
     * We will check that runtime1 and runtime2 will be different.
     * Then we will remove the classInfos from runtime1 which we have not added to runtime2.
     * We then check if the the runtimeInfos match (which they now should)
     */
    @Test
    fun `test - smoke`(compiler: Compiler) {
        val sourceFiles = 256
        val runtime1 = TrackingRuntimeInfo()
        val runtime2 = TrackingRuntimeInfo()
        assertEquals(runtime1, runtime2)

        val sources = buildMap {
            repeat(sourceFiles) { index ->
                put(
                    "Foo$index.kt", """
                    import androidx.compose.runtime.*
                    import androidx.compose.material3.Text
                    
                    open class FooBase$index${if (index > 0) " : FooBase${index - 1}()" else ""}
            
                    @Composable
                    fun Foo$index() {
                        Text("Hello")
                        ${if (index > 0) "Foo${index - 1}()" else ""}
                    }
                """.trimIndent()
                )
            }
        }

        val compiled = compiler.compile(sources)

        /*
        Step: Add all classes to runtime1 and only a subset of classes to runtime2
         */
        compiled.entries.forEachIndexed { index, (name, bytecode) ->
            val classInfo = ClassInfo(bytecode) ?: error("Failed to parse class info: $name")
            runtime1.add(classInfo)

            if (index % 2 == 0) {
                runtime2.add(classInfo)
            }
        }

        /* runtime1 and runtime2 differ as they have seen different sets of classes */
        assertNotEquals(runtime1, runtime2)

        /*
        Step: Remove classes from runtime1, which runtime2 has not received
         */
        compiled.entries.forEachIndexed { index, (name, bytecode) ->
            val classInfo = ClassInfo(bytecode) ?: error("Failed to parse class info: $name")
            if (index % 2 != 0) {
                runtime1.remove(classInfo.classId)
            }
        }
        assertEquals(runtime1, runtime2)
    }

}
