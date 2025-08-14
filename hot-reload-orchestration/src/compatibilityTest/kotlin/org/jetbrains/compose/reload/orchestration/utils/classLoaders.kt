/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.orchestration.utils

import java.io.File
import java.net.URLClassLoader
import kotlin.io.path.Path

val previousClassLoader: URLClassLoader = URLClassLoader.newInstance(
    System.getProperty("previousClasspath").split(File.pathSeparator).map { Path(it).toUri().toURL() }.toTypedArray(),
    ClassLoader.getSystemClassLoader()
)

val currentClassLoader: URLClassLoader = URLClassLoader.newInstance(
    System.getProperty("currentClasspath").split(File.pathSeparator).map { Path(it).toUri().toURL() }.toTypedArray(),
    ClassLoader.getSystemClassLoader()
)
