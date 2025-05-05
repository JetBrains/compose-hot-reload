/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import org.jetbrains.compose.reload.test.gradle.GradleRunner
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.build
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import kotlin.test.assertEquals

class TasksSmokeTest {
    @GradleIntegrationTest
    @HotReloadTest
    @QuickTest
    fun `test - tasks`(fixture: HotReloadTestFixture) = fixture.runTest {
        assertEquals(GradleRunner.ExitCode.success, gradleRunner.build("tasks"))
    }
}
