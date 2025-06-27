/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.reload.InternalHotReloadApi
import org.jetbrains.compose.reload.gradle.ComposeHotTask.Companion.COMPOSE_HOT_RELOAD_OTHER_GROUP
import org.jetbrains.compose.reload.gradle.ComposeHotTask.Companion.COMPOSE_HOT_RELOAD_RUN_GROUP

sealed interface ComposeHotTask : Task {
    companion object Companion {
        @InternalHotReloadApi
        const val COMPOSE_HOT_RELOAD_RUN_GROUP = "Compose Hot Reload: Run"

        @InternalHotReloadApi
        const val COMPOSE_HOT_RELOAD_OTHER_GROUP = "Compose Hot Reload: Other"
    }
}

@OptIn(ExperimentalSubclassOptIn::class)
@SubclassOptInRequired(InternalHotReloadApi::class)
interface ComposeHotReloadRunTask : ComposeHotTask

@OptIn(ExperimentalSubclassOptIn::class)
@SubclassOptInRequired(InternalHotReloadApi::class)
interface ComposeHotReloadOtherTask : ComposeHotTask

internal fun Project.configureComposeHotReloadTasks() {
    tasks.withType<ComposeHotTask> {
        group = when (this) {
            is ComposeHotReloadOtherTask -> COMPOSE_HOT_RELOAD_OTHER_GROUP
            is ComposeHotReloadRunTask -> COMPOSE_HOT_RELOAD_RUN_GROUP
        }
    }
}
