/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(InternalKotlinGradlePluginApi::class)

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmRun

internal val Project.jvmRunHijackTasks: Future<Collection<TaskProvider<out JavaExec>>> by projectFuture {
    PluginStage.EagerConfiguration.await()
    forAllJvmTargets { target -> target.hijackJvmRunTask.await() }.filterNotNull()
}

@OptIn(InternalKotlinGradlePluginApi::class)
internal val KotlinTarget.hijackJvmRunTask: Future<TaskProvider<out KotlinJvmRun>?> by future {
    if (!project.isIdea.get()) return@future null

    /* Ide support is present, no need for hijacking */
    if (project.composeReloadIdeaComposeHotReload) return@future null
    val mainCompilation = project.provider { compilations.getByName("main") }
    if (this !is KotlinJvmTarget) return@future null

    val runTask = project.tasks.register<KotlinJvmRun>(camelCase(name, "run")) {
        mainClass.convention(project.mainClassConvention)
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
