/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import kotlin.io.path.Path

internal fun screenshotsDirectory() = requireSystemProperty("reloadTests.screenshotsDirectory").let(::Path)

internal fun gradleWrapperFile() = requireSystemProperty("reloadTests.gradleWrapper").let(::Path)

internal fun gradleWrapperBatFile() = requireSystemProperty("reloadTests.gradleWrapperBat").let(::Path)

internal fun gradleWrapperJarFile() = requireSystemProperty("reloadTests.gradleWrapperJar").let(::Path)

internal fun requireSystemProperty(key: String): String {
    return System.getProperty(key) ?: error("Missing System Property '$key'")
}
