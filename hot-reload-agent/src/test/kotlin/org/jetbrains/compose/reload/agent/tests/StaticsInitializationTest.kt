/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.agent.tests

import javassist.ClassPool
import javassist.Modifier
import org.jetbrains.compose.reload.agent.transformForStaticsInitialization
import org.jetbrains.compose.reload.analysis.ClassInfo
import org.jetbrains.compose.reload.analysis.RuntimeInfo
import org.jetbrains.compose.reload.analysis.classInitializerMethodId
import org.jetbrains.compose.reload.analysis.resolveInvalidationKey
import org.jetbrains.compose.reload.analysis.testFixtures.checkJavap
import org.jetbrains.compose.reload.core.testFixtures.Compiler
import org.jetbrains.compose.reload.core.testFixtures.WithCompiler
import org.jetbrains.compose.reload.core.testFixtures.compile
import org.junit.jupiter.api.TestInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@WithCompiler
class StaticsInitializationTest() {

    private val pool = ClassPool(true)
    private val loader = javassist.Loader.Simple()

    @Test
    fun `test - adding top level val`(compiler: Compiler, info: TestInfo) {
        val beforeCode = compiler.compile(
            "Test.kt" to """
             val empty = 42
            """.trimIndent()
        ).getValue("TestKt.class")
        val beforeCtClazz = pool.makeClass(beforeCode.inputStream())
        val beforeClazz = beforeCtClazz.toClass(loader, javaClass.protectionDomain)
        beforeCtClazz.defrost()

        val afterBytecode = compiler.compile(
            "Test.kt" to """
            val empty = 42
            val foo = 42
            """.trimIndent()
        )

        val afterCtClazz = pool.makeClass(afterBytecode.getValue("TestKt.class").inputStream())
        afterCtClazz.transformForStaticsInitialization(beforeClazz)

        assertEquals(
            0, afterCtClazz.getDeclaredField("foo").modifiers and Modifier.FINAL,
            "Expected 'val foo' to not be static anymore"
        )
        checkJavap(info, code = mapOf("FooKt.class" to afterCtClazz.toBytecode()))
    }

    @Test
    fun `test - no field added - same runtime invalidation key`(compiler: Compiler, info: TestInfo) {
        val beforeCode = compiler.compile(
            "Test.kt" to """
             val empty = 42
            """.trimIndent()
        ).getValue("TestKt.class")

        val beforeClassInfo = ClassInfo(beforeCode) ?: fail("Missing 'ClassInfo' for 'TestKt'")


        val afterBytecode = compiler.compile(
            "Test.kt" to """
            val empty = 42
            fun foo() = Unit
            """.trimIndent()
        )

        val afterClassInfo = ClassInfo(afterBytecode.getValue("TestKt.class"))
            ?: fail("Missing 'ClassInfo' for 'TestKt'")


        val beforeClinit = beforeClassInfo.methods.getValue(beforeClassInfo.classId.classInitializerMethodId)
        val afterClinit = afterClassInfo.methods.getValue(afterClassInfo.classId.classInitializerMethodId)

        val beforeRuntimeInfo = RuntimeInfo(mapOf(beforeClassInfo.classId to beforeClassInfo))
        val afterRuntimeInfo = RuntimeInfo(mapOf(afterClassInfo.classId to afterClassInfo))

        val beforeInvalidationKey = beforeRuntimeInfo.resolveInvalidationKey(beforeClinit.methodId)
            ?: fail("Missing 'InvalidationKey' for ${beforeClinit.methodId}")

        val afterInvalidationKey = afterRuntimeInfo.resolveInvalidationKey(afterClinit.methodId)
            ?: fail("Missing 'InvalidationKey' for '${afterClinit.methodId}'")

        assertEquals(
            beforeInvalidationKey, afterInvalidationKey,
        )
    }
}
