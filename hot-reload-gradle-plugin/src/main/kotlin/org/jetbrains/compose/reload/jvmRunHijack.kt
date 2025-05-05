/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(InternalKotlinGradlePluginApi::class)

package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.register
import org.jetbrains.compose.reload.gradle.Future
import org.jetbrains.compose.reload.gradle.PluginStage
import org.jetbrains.compose.reload.gradle.await
import org.jetbrains.compose.reload.gradle.camelCase
import org.jetbrains.compose.reload.gradle.core.composeReloadIdeaComposeHotReload
import org.jetbrains.compose.reload.gradle.forAllJvmTargets
import org.jetbrains.compose.reload.gradle.future
import org.jetbrains.compose.reload.gradle.projectFuture
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmRun

internal val Project.jvmRunHijackTasks: Future<Collection<TaskProvider<out JavaExec>>> by projectFuture {
    PluginStage.EagerConfiguration.await()
    forAllJvmTargets { target -> target.hijackJvmRunTask.await() }.filterNotNull()
}

@OptIn(InternalKotlinGradlePluginApi::class)
internal val KotlinTarget.hijackJvmRunTask: Future<TaskProvider<out KotlinJvmRun>?> by future {
    /* Ide support is present, no need for hijacking */
    if (project.composeReloadIdeaComposeHotReload) return@future null
    val mainCompilation = project.provider { compilations.getByName("main") }

    val runTask = project.tasks.register<KotlinJvmRun>(camelCase(name, "run")) {
        configureJavaExecTaskForHotReload(mainCompilation)
    }

    val snapshotTask = mainCompilation.await()?.hotSnapshotTask?.await()
    runTask.configure { task ->
        if (snapshotTask != null) {
            task.dependsOn(snapshotTask)
        }
    }

    runTask
}
