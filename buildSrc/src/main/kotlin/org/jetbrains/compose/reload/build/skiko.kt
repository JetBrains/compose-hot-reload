/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.build

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.reload.core.Os

/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

fun Project.skikoCurrentOs(): String {
    val default = extensions.getByType<VersionCatalogsExtension>().named("deps").findLibrary("skiko-awt").get().get()

    val targetOs = when(Os.current()) {
        Os.MacOs -> "macos"
        Os.Windows -> "windows"
        Os.Linux -> "linux"
    }

    val osArch = System.getProperty("os.arch")
    val targetArch = when (osArch) {
        "x86_64", "amd64" -> "x64"
        "aarch64" -> "arm64"
        else -> error("Unsupported arch: $osArch")
    }

    return "org.jetbrains.skiko:skiko-awt-runtime-$targetOs-$targetArch:${default.version}"
}
