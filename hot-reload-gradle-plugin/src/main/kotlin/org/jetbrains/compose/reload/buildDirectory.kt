/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.jetbrains.compose.reload.gradle.capitalized
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

internal val KotlinCompilation<*>.runBuildDirectory: Provider<Directory>
    get() {
        return project.layout.buildDirectory.dir("run/${target.name}${compilationName.capitalized}")
    }

internal fun KotlinCompilation<*>.runBuildDirectory(path: String): Provider<Directory> {
    return runBuildDirectory.map { directory -> directory.dir(path) }
}

internal fun KotlinCompilation<*>.runBuildFile(path: String): Provider<RegularFile> {
    return runBuildDirectory.map { directory -> directory.file(path) }
}
