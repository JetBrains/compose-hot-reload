/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test

import java.io.File
import kotlin.io.path.Path

internal val compileClasspath = requireEnvVar("chr.compilePath").split(File.pathSeparator).map(::Path)

internal val compilePluginClasspath = requireEnvVar("chr.compilePluginPath").split(File.pathSeparator).map(::Path)

internal val compileModuleName = requireEnvVar("chr.compileModuleName")

internal fun applicationClassesDir() = Path(requireSystemProperty("applicationClassesDir"))

internal fun requireSystemProperty(key: String): String {
    return System.getProperty(key) ?: error("System Property '$key' is not defined")
}

internal fun requireEnvVar(key: String): String {
    return System.getenv(key) ?: error("Environment Variable '$key' is not defined")
}
