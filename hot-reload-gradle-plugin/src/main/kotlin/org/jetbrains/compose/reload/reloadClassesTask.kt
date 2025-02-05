/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.withType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.jetbrains.compose.reload.gradle.capitalized
import org.jetbrains.compose.reload.gradle.kotlinJvmOrNull
import org.jetbrains.compose.reload.gradle.kotlinMultiplatformOrNull
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Compiler
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType
import org.jetbrains.compose.reload.orchestration.connectOrchestrationClient
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import java.io.File
import kotlin.system.exitProcess


internal fun Project.setupComposeReloadHotClasspathTasks() {
    kotlinMultiplatformOrNull?.targets?.all { target ->
        target.compilations.all { compilation -> setupComposeReloadHotClasspathTask(compilation) }
    }

    kotlinJvmOrNull?.target?.compilations?.all { compilation -> setupComposeReloadHotClasspathTask(compilation) }

    tasks.withType<ComposeReloadHotClasspathTask>().configureEach { task ->
        task.outputs.upToDateWhen { true }
        task.group = "compose"
        task.agentPort.set(project.orchestrationPort)
    }
}

internal fun Project.setupComposeReloadHotClasspathTask(compilation: KotlinCompilation<*>): TaskProvider<ComposeReloadHotClasspathTask> {
    val name = composeReloadHotClasspathTaskName(compilation)
    if (name in tasks.names) return tasks.named(name, ComposeReloadHotClasspathTask::class.java)
    val hotApplicationClasses = compilation.hotApplicationClasspath

    return tasks.register(name, ComposeReloadHotClasspathTask::class.java) { task ->
        task.classpath.from(hotApplicationClasses)
        task.dependsOn(hotApplicationClasses)
    }
}

internal fun composeReloadHotClasspathTaskName(compilation: KotlinCompilation<*>): String {
    return buildString {
        append("reload")
        append(compilation.target.name.capitalized)
        append(compilation.name.capitalized)
        append("Classpath")
    }
}

internal open class ComposeReloadHotClasspathTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    val classpath: ConfigurableFileCollection = project.objects.fileCollection()

    @get:Internal
    val agentPort = project.objects.property<Int>()

    @TaskAction
    fun execute(inputs: InputChanges) {
        val client = runCatching { connectOrchestrationClient(Compiler, agentPort.get()) }.getOrNull() ?: run {
            logger.quiet("Failed to create 'OrchestrationClient'!")
            exitProcess(-1)
        }

        client.use {
            client.sendMessage(OrchestrationMessage.RecompilerReady())

            if (!inputs.isIncremental) {
                logger.debug("Non-Incremental compile: Rejecting")
                return
            }

            logger.quiet("Incremental run")
            val changedClassFiles = mutableMapOf<File, ChangeType>()
            inputs.getFileChanges(classpath).forEach { change ->
                val changeType = when (change.changeType) {
                    org.gradle.work.ChangeType.ADDED -> ChangeType.Added
                    org.gradle.work.ChangeType.MODIFIED -> ChangeType.Modified
                    org.gradle.work.ChangeType.REMOVED -> ChangeType.Removed
                }

                changedClassFiles[change.file.absoluteFile] = changeType
                logger.trace("[${change.changeType}] ${change.file}")
            }

            client.sendMessage(ReloadClassesRequest(changedClassFiles))
        }
    }
}
