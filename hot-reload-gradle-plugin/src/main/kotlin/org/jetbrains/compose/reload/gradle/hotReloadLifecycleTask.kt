/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.withType
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.compose.reload.gradle.android.ComposeHotAndroidReloadTask
import org.jetbrains.compose.reload.gradle.desktop.ComposeHotDesktopReloadTask

/*
 * The shared entry point of a reload: ships to every running application, no matter the target.
 * Desktop and Android each have their own sender; this lifecycle task simply depends on them all.
 */
internal val Project.hotReloadLifecycleTask: Future<TaskProvider<ComposeHotReloadLifecycleTask>?> by projectFuture {
    PluginStage.EagerConfiguration.await()
    val name = "reload"
    if (name in tasks.names) {
        logger.error("Conflicting '$name' task detected")
        return@projectFuture null
    }

    tasks.register(name, ComposeHotReloadLifecycleTask::class.java) { task ->
        task.description = "Hot Reloads code for all running applications"
        task.dependsOn(tasks.withType<ComposeHotDesktopReloadTask>().matching { it.agentPort.isPresent })
        task.dependsOn(tasks.withType<ComposeHotAndroidReloadTask>().matching { it.agentPort.isPresent })
    }
}

@DisableCachingByDefault(because = "Lifecycle task: Not worth caching")
internal abstract class ComposeHotReloadLifecycleTask : DefaultTask(), ComposeHotReloadOtherTask
