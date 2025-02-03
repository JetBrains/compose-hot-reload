package org.jetbrains.compose.reload.test.gradle

import kotlin.io.path.Path

internal fun screenshotsDirectory() = requireSystemProperty("reloadTests.screenshotsDirectory").let(::Path)

internal fun gradleWrapperFile() = requireSystemProperty("reloadTests.gradleWrapper").let(::Path)

internal fun gradleWrapperBatFile() = requireSystemProperty("reloadTests.gradleWrapperBat").let(::Path)

internal fun gradleWrapperJarFile() = requireSystemProperty("reloadTests.gradleWrapperJar").let(::Path)

internal fun requireSystemProperty(key: String): String {
    return System.getProperty(key) ?: error("Missing System Property '$key'")
}
