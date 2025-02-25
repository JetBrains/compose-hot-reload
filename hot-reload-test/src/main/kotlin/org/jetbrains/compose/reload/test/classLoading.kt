/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.test

import java.io.File
import java.net.URLClassLoader

public val testClassLoader: URLClassLoader = URLClassLoader.newInstance(
    System.getProperty("testClasses").split(File.pathSeparator).map { File(it).toURI().toURL() }.toTypedArray(),
    ClassLoader.getSystemClassLoader()
)
