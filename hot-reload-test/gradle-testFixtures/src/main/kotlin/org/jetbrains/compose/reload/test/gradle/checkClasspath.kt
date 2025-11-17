/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.test.core.AppClasspath
import java.io.File.separator
import kotlin.test.fail

internal fun checkClasspath(classpath: AppClasspath, composeVersion: TestedComposeVersion) {
    val problems = mutableListOf<String>()

    val composeRuntimeVersion = composeRuntimeFinder.findVersion(classpath)
    if (composeRuntimeVersion != composeVersion.toString()) {
        problems.add("Compose Runtime version mismatch: expected $composeVersion, found $composeRuntimeVersion")
    }

    val composeDesktopVersion = composeDesktopFinder.findVersion(classpath)
    if (composeDesktopVersion != composeVersion.toString()) {
        problems.add("Compose Desktop version mismatch: expected $composeVersion, found $composeDesktopVersion")
    }

    val composeFoundationVersion = composeFoundationFinder.findVersion(classpath)
    if (composeFoundationVersion != composeVersion.toString()) {
        problems.add("Compose Foundation version mismatch: expected $composeVersion, found $composeFoundationVersion")
    }

    if (problems.isNotEmpty()) {
        fail(buildString {
            appendLine("Suspicious classpath:")
            problems.forEach { appendLine("  $it") }
            appendLine()
            appendLine("Classpath:")
            classpath.files.forEach { appendLine("  $it") }
        })
    }
}

private class DependencyFinder(groupId: String, artifactId: String) {
    private val regex = dependencyRegex(groupId, artifactId)
    fun findVersion(classpath: AppClasspath): String? {
        classpath.files.forEach { file ->
            val match = regex.find(file) ?: return@forEach
            return match.groups["version"]?.value
        }
        return null
    }
}

private val composeRuntimeFinder = DependencyFinder("org.jetbrains.compose.runtime", "runtime-desktop")
private val composeDesktopFinder = DependencyFinder("org.jetbrains.compose.desktop", "desktop-jvm")
private val composeFoundationFinder = DependencyFinder("org.jetbrains.compose.foundation", "foundation-desktop")

private fun dependencyRegex(groupId: String, artifactId: String): Regex {
    val separator = Regex.escape(separator)
    val escapedGroupId = Regex.escape(groupId)
    val escapedArtifactId = Regex.escape(artifactId)
    return Regex("""$separator$escapedGroupId$separator$escapedArtifactId$separator.*$escapedArtifactId-(?<version>.*).jar""")
}
