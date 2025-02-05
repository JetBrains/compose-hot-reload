/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.compose.reload.gradle.withKotlinPlugin

@Suppress("unused")
class HotReloadTestPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.withKotlinPlugin {
            target.configureHotReloadUnitTestTasks()
            target.configureGradleTestTasks()
        }
    }
}
