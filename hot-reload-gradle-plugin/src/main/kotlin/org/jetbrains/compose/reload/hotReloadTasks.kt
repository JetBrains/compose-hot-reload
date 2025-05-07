/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.initialization.BuildCancellationToken
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.withType
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.compose.reload.core.asTemplateOrThrow
import org.jetbrains.compose.reload.core.renderOrThrow
import org.jetbrains.compose.reload.gradle.Future
import org.jetbrains.compose.reload.gradle.InternalHotReloadGradleApi
import org.jetbrains.compose.reload.gradle.PluginStage
import org.jetbrains.compose.reload.gradle.await
import org.jetbrains.compose.reload.gradle.forAllJvmCompilations
import org.jetbrains.compose.reload.gradle.future
import org.jetbrains.compose.reload.gradle.projectFuture
import org.jetbrains.compose.reload.gradle.readObject
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole.Compiler
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType.Added
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType.Modified
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType.Removed
import org.jetbrains.compose.reload.orchestration.connectOrchestrationClient
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import javax.inject.Inject
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

internal val Project.hotReloadLifecycleTask: Future<TaskProvider<HotReloadLifecycleTask>?> by projectFuture {
    PluginStage.EagerConfiguration.await()
    val name = "reload"
    if (name in tasks.names) {
        logger.error("Conflicting '$name' task detected")
        return@projectFuture null
    } else tasks.register(name, HotReloadLifecycleTask::class.java) { task ->
        task.group = "compose"
        task.description = "Hot Reloads code for all running applications"
    }
}

internal val Project.hotReloadTasks: Future<Collection<Provider<HotReloadTask>>> by projectFuture {
    PluginStage.EagerConfiguration.await()

    tasks.withType<HotReloadTask>().configureEach { task ->
        task.group = "compose"
    }

    forAllJvmCompilations { compilation ->
        compilation.hotReloadTask.await()
    }
}

internal val KotlinCompilation<*>.hotReloadTask: Future<TaskProvider<HotReloadTask>> by future {
    if (hotReloadTaskName in project.tasks.names) return@future project.tasks.named(name, HotReloadTask::class.java)

    val task = project.tasks.register(hotReloadTaskName, HotReloadTask::class.java) { task ->
        task.description = "Hot Reloads code associated with the '$this'"
        task.onlyIf("Running application is known") { task.agentPort.isPresent }
        task.agentPort.set(pidFileOrchestrationPort)
    }

    val snapshotTask = hotSnapshotTask.await()
    task.configure { task ->
        task.pendingRequestFile.set(snapshotTask.flatMap { it.pendingRequestFile })
        task.dependsOn(snapshotTask)
    }

    project.hotReloadLifecycleTask.await()?.configure { lifecycleTask ->
        if (pidFileOrchestrationPort.isPresent) {
            lifecycleTask.dependsOn(task)
            lifecycleTask.pidFiles.from(pidFile)
            lifecycleTask.activeReloadTaskPaths.add(task.map { it.path })
        }
    }

    task
}


@DisableCachingByDefault(because = "Should always run")
@InternalHotReloadGradleApi
abstract class HotReloadTask : DefaultTask() {
    private val rootProjectDirectory = project.rootProject.layout.projectDirectory

    @get:Internal
    val agentPort: Property<Int> = project.objects.property<Int>()

    @get:Internal
    val pendingRequestFile: RegularFileProperty = project.objects.fileProperty()

    @Inject
    abstract fun getCancellationToken(): BuildCancellationToken

    @TaskAction
    fun execute() {
        val client = runCatching { connectOrchestrationClient(Compiler, agentPort.get()) }.getOrNull() ?: run {
            logger.quiet("Failed to create 'OrchestrationClient'!")
            getCancellationToken().cancel()
            error("Failed to create 'OrchestrationClient'!")
        }

        client.use { client ->
            val pendingRequestFile = pendingRequestFile.get().asFile.toPath()

            val request = if (pendingRequestFile.exists()) pendingRequestFile.readObject<ReloadClassesRequest>() else {
                logger.info("UP-TO-DATE")
                ReloadClassesRequest(emptyMap())
            }

            if (request.changedClassFiles.isEmpty()) {
                logger.debug("UP-TO-DATE: No changed classes found")
            }

            logger.quiet(reloadReport(request))
            client.sendMessage(request).get()
            pendingRequestFile.deleteIfExists()
        }
    }


    private fun reloadReport(request: ReloadClassesRequest): String {
        val rootPath = rootProjectDirectory.asFile
        val entries = request.changedClassFiles.entries.toTypedArray()

        return """
            Reloading classes
              Application listening at '{{port}}'
              Request ID: '{{messageId}}'
              
              Summary: 
                - Modifications: {{modifications}}
                - Additions: {{additions}}
                - Deletions: {{deletions}}
              
              Details: 
                - {{detail}}
        """.trimIndent().asTemplateOrThrow().renderOrThrow {
            "port"(agentPort.get())
            "messageId"(request.messageId)
            "modifications"(entries.count { it.value == Modified })
            "additions"(entries.count { it.value == Added })
            "deletions"(entries.count { it.value == Removed })

            entries.forEach { (file, changeType) ->
                "detail"("${file.relativeToOrSelf(rootPath)}: ${changeType.name.lowercase()}")
            }
        }
    }
}

internal abstract class HotReloadLifecycleTask : DefaultTask() {
    @get:Internal
    internal val pidFiles = project.objects.fileCollection()

    @get:Internal
    internal val activeReloadTaskPaths = project.objects.listProperty(String::class.java)
}
