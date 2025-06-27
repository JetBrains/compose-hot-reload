/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests

import org.gradle.api.Project
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.compose.reload.gradle.lazyProjectProperty
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LazyPropertyTest {
    val lazyProjectPathInvocations = AtomicInteger(0)

    val Project.lazyProjectPath by lazyProjectProperty {
        lazyProjectPathInvocations.incrementAndGet()
        "lazy $path"
    }

    val lazyNullInvocations = AtomicInteger(0)
    val Project.lazyNull by lazyProjectProperty {
        lazyNullInvocations.incrementAndGet()
        null
    }

    @Test
    fun `test - lazy project property`() {
        val rootProject = ProjectBuilder.builder().build()
        val projectA = ProjectBuilder.builder().withParent(rootProject).withName("a").build()
        val projectB = ProjectBuilder.builder().withParent(rootProject).withName("b").build()

        assertEquals("lazy :a", projectA.lazyProjectPath)
        assertEquals(1, lazyProjectPathInvocations.get())
        assertEquals("lazy :a", projectA.lazyProjectPath)
        assertEquals(1, lazyProjectPathInvocations.get())


        assertEquals("lazy :b", projectB.lazyProjectPath)
        assertEquals(2, lazyProjectPathInvocations.get())
        assertEquals("lazy :b", projectB.lazyProjectPath)
        assertEquals(2, lazyProjectPathInvocations.get())
    }

    @Test
    fun `test - lazy null property`() {
        val rootProject = ProjectBuilder.builder().build()
        val projectA = ProjectBuilder.builder().withParent(rootProject).withName("a").build()
        assertNull(projectA.lazyNull)
        assertNull(projectA.lazyNull)
        assertEquals(1, lazyNullInvocations.get())

        assertNull(rootProject.lazyNull)
        assertEquals(2, lazyNullInvocations.get())
    }
}
