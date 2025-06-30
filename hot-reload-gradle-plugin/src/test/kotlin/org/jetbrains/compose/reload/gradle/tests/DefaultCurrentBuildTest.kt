/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle.tests

import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.compose.reload.gradle.CurrentBuild
import org.jetbrains.compose.reload.gradle.currentBuild
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultCurrentBuildTest {
    @Test
    fun `test - default current build`() {
        val project = ProjectBuilder.builder().withName("test").build()
        assertEquals(CurrentBuild.default, project.currentBuild)
    }
}
