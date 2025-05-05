/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.jetbrains.compose.reload.core.PidFileInfo
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.gradle.camelCase
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation.Companion.MAIN_COMPILATION_NAME

internal val KotlinCompilation<*>.runBuildDirectory: Provider<Directory>
    get() = project.layout.buildDirectory.dir("run/${camelCase(target.name, compilationName)}")

internal fun KotlinCompilation<*>.runBuildDirectory(path: String): Provider<Directory> {
    return runBuildDirectory.map { directory -> directory.dir(path) }
}

internal fun KotlinCompilation<*>.runBuildFile(path: String): Provider<RegularFile> {
    return runBuildDirectory.map { directory -> directory.file(path) }
}

internal val KotlinCompilation<*>.pidFileOrchestrationPort: Provider<Int>
    get() = project.providers.fileContents(pidFile).asBytes.map { bytes ->
        PidFileInfo(bytes).getOrThrow().orchestrationPort ?: error("Failed reading pid file")
    }

/**
 * Represents the directory where 'hot' classes will be put for the running application
 * to load them from.
 */
internal val KotlinCompilation<*>.hotClassesOutputDirectory
    get() = runBuildDirectory("classpath/hot")

/**
 * The associated file containing the runtime-classpath snapshot used by the [HotReloadTask]
 */
internal val KotlinCompilation<*>.hotClasspathSnapshotFile
    get() = runBuildDirectory("classpath").map { it.file(".snapshot") }

internal val KotlinCompilation<*>.hotReloadRequestFile
    get() = runBuildDirectory("classpath").map { it.file(".request") }

internal val KotlinCompilation<*>.pidFile
    get() = runBuildFile("${camelCase(target.name, name)}.pid")

internal val KotlinCompilation<*>.argFile
    get() = runBuildFile("${camelCase(target.name, name)}.argfile")

internal val KotlinCompilation<*>.hotRunTaskName
    get() = camelCase(target.name, compilationName.takeIf { it != MAIN_COMPILATION_NAME }, "run", "hot")

internal val KotlinCompilation<*>.hotDevTaskName
    get() = camelCase(target.name, "run", compilationName)

internal val KotlinCompilation<*>.hotReloadTaskName
    get() = camelCase("hot", "reload", target.name, compilationName)

internal val KotlinCompilation<*>.hotSnapshotTaskName
    get() = camelCase("hot", "snapshot", target.name, compilationName)
