/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis.tests

import org.jetbrains.compose.reload.analysis.ClassFlags
import org.junit.jupiter.api.Assertions.assertFalse
import kotlin.test.Test
import kotlin.test.assertTrue

class ClassFlagsTest {
    @Test
    fun `test - empty flags`() {
        val flags = ClassFlags.empty
        assertFalse(flags.isSynthetic)
        assertFalse(flags.isFinal)
        assertFalse(flags.isInterface)
        assertFalse(flags.isAbstract)
        assertFalse(flags.isAnnotation)
        assertFalse(flags.isEnum)
        assertFalse(flags.isRecord)
        assertFalse(flags.isDeprecated)
        assertFalse(flags.isPublic)
        assertFalse(flags.isProtected)
    }

    @Test
    fun `test - switch on - switch off`() {
        val flags = ClassFlags.empty
            .withSynthetic()
            .withFinal()
            .withInterface()
            .withAbstract()
            .withAnnotation()
            .withEnum()
            .withRecord()
            .withDeprecated()
            .withPublic()
            .withProtected()


        assertTrue(flags.isSynthetic)
        assertTrue(flags.isFinal)
        assertTrue(flags.isInterface)
        assertTrue(flags.isAbstract)
        assertTrue(flags.isAnnotation)
        assertTrue(flags.isEnum)
        assertTrue(flags.isRecord)
        assertTrue(flags.isDeprecated)
        assertTrue(flags.isPublic)
        assertTrue(flags.isProtected)

        assertFalse(flags.withSynthetic(false).isSynthetic)
        assertFalse(flags.withFinal(false).isFinal)
        assertFalse(flags.withInterface(false).isInterface)
        assertFalse(flags.withAbstract(false).isAbstract)
        assertFalse(flags.withAnnotation(false).isAnnotation)
        assertFalse(flags.withEnum(false).isEnum)
        assertFalse(flags.withRecord(false).isRecord)
        assertFalse(flags.withDeprecated(false).isDeprecated)
        assertFalse(flags.withPublic(false).isPublic)
        assertFalse(flags.withProtected(false).isProtected)

        assertTrue(flags.withSynthetic(true).isFinal)
    }
}
