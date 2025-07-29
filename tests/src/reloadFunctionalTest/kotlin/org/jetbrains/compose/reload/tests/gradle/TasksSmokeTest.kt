/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.tests.gradle

import kotlinx.coroutines.flow.toList
import org.jetbrains.compose.reload.test.gradle.AndroidHotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTest
import org.jetbrains.compose.reload.test.gradle.HotReloadTestFixture
import org.jetbrains.compose.reload.test.gradle.assertSuccessful
import org.jetbrains.compose.reload.test.gradle.buildFlow
import org.jetbrains.compose.reload.utils.GradleIntegrationTest
import org.jetbrains.compose.reload.utils.QuickTest
import kotlin.io.path.appendText

@AndroidHotReloadTest
@GradleIntegrationTest
@QuickTest
class TasksSmokeTest {
    @HotReloadTest
    fun `test - tasks`(fixture: HotReloadTestFixture) = fixture.runTest {
        gradleRunner.buildFlow("tasks").toList().assertSuccessful()
    }

    @HotReloadTest
    fun `test - tasks - with 'run' conflict - #174`(fixture: HotReloadTestFixture) = fixture.runTest {
        projectDir.resolve("build.gradle.kts").appendText(
            """
            |
            | tasks.register("run") {}
        """.trimMargin()
        )

        gradleRunner.buildFlow("tasks").toList().assertSuccessful()
    }
}
