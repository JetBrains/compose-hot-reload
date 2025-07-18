/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.withType
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.withClosure
import org.jetbrains.compose.reload.gradle.idea.IdeaComposeHotReloadModel
import org.jetbrains.compose.reload.gradle.idea.IdeaComposeHotRunTask
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

internal fun Project.configureComposeHotReloadModelBuilder() = launch {
    PluginStage.EagerConfiguration.await()
    serviceOf<ToolingModelBuilderRegistry>().register(IdeaComposeHotReloadModelBuilder())
}

internal class IdeaComposeHotReloadModelBuilder : ToolingModelBuilder {
    override fun canBuild(modelName: String): Boolean = modelName == IdeaComposeHotReloadModel::class.java.name

    override fun buildAll(modelName: String, project: Project): IdeaComposeHotReloadModel {
        return IdeaComposeHotReloadModel(
            version = HOT_RELOAD_VERSION,
            runTasks = project.tasks.withType<AbstractComposeHotRun>().mapNotNull { task ->
                task.toIdeaModel()
            }.flatMap { runTaskModel ->
                listOfNotNull(runTaskModel, runTaskModel.compatVersion())
            })
    }
}

private fun AbstractComposeHotRun.toIdeaModel(): IdeaComposeHotRunTask? {
    val compilation = compilation.orNull ?: return null

    return IdeaComposeHotRunTask(
        taskName = name,
        taskClass = when (this) {
            is ComposeHotDevRun -> ComposeHotDevRun::class.java.name
            is ComposeHotRun -> ComposeHotRun::class.java.name
        },
        targetName = compilation.target.name,
        compilationName = compilation.name,
        sourceSets = compilation.allKotlinSourceSets.map { it.name } +
            compilation.associatedCompilations.withClosure<KotlinCompilation<*>> { it.associatedCompilations }
                .flatMap { it.allKotlinSourceSets }.map { it.name },
        argFile = argFile.orNull?.asFile?.toPath(),
        argFileTaskName = this@toIdeaModel.argFileTaskName.orNull,
    )
}

/**
 * Version for older IDE plugins which do not yet handle the new package (compose.reload.gradle)
 */
private fun IdeaComposeHotRunTask.compatVersion(): IdeaComposeHotRunTask? {
    val compatTaskClass = taskClass?.replace("org.jetbrains.compose.reload.gradle", "org.jetbrains.compose.reload")
    if (compatTaskClass == taskClass) return null

    return IdeaComposeHotRunTask(
        taskName = taskName,
        taskClass = compatTaskClass,
        targetName = taskName,
        compilationName = compilationName,
        sourceSets = sourceSets,
        argFile = argFile,
        argFileTaskName = argFileTaskName,
    )
}
