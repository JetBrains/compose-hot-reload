/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.outputStream

internal val Project.hotArgFileTasks: Future<Collection<TaskProvider<ComposeHotArgFileTask>>> by projectFuture {
    hotRunTasks.await().map { runTask ->
        registerHotArgfileTask(runTask)
    }

}

internal fun TaskProvider<out JavaExec>.argFileTaskName(): String {
    return "${name}Argfile"
}

private fun Project.registerHotArgfileTask(
    runTask: TaskProvider<out AbstractComposeHotRun>
): TaskProvider<ComposeHotArgFileTask> {
    val createArgsTask = tasks.register(runTask.argFileTaskName(), ComposeHotArgFileTask::class.java) { task ->
        task.description = "Creates an argument file for the '${runTask.name}' task"
        task.runTaskName.set(runTask.name)
        task.argFile.set(provider { runTask.get().argFile.get() })
        task.arguments.addAll(provider { runTask.get().allJvmArgs + runTask.get().jvmArgs.orEmpty() })
        task.classpath.from(project.files { runTask.get().classpath })
        task.dependsOn(provider { runTask.get().snapshotTaskName })
    }

    runTask.configure { task ->
        task.dependsOn(createArgsTask)
        task.argFileTaskName.set(runTask.argFileTaskName())
    }

    return createArgsTask
}

internal open class ComposeHotArgFileTask : DefaultTask(), ComposeHotReloadOtherTask {
    @get:Input
    internal val arguments = project.objects.listProperty(String::class.java)

    @get:Classpath
    internal val classpath = project.objects.fileCollection()

    @get:Input
    internal val runTaskName = project.objects.property(String::class.java)

    @get:OutputFile
    internal val argFile = project.objects.fileProperty()

    @TaskAction
    internal fun createArgfile() {
        val argFile = this.argFile.get().asFile.toPath()
        argFile.createArgfile(arguments.get(), classpath.files)
        logger.info("$argFile created")
    }
}

internal fun Path.createArgfile(arguments: List<String>, classpath: Collection<File>) {
    createParentDirectories()
    outputStream().bufferedWriter().use { writer ->
        arguments.forEach { arg ->
            val escaped = arg.replace("""\""", """\\""")
            writer.appendLine("\"$escaped\"")
        }

        val classpathFormatted = classpath.joinToString(separator = "${File.pathSeparator}\\\n") { file ->
            file.absolutePath.replace("""\""", """\\""")
        }
        writer.appendLine("-cp \"$classpathFormatted\"")
        writer.flush()
    }
}
