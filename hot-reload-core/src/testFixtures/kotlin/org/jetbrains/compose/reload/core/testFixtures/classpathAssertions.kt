/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("TestFunctionName")

package org.jetbrains.compose.reload.core.testFixtures

import org.intellij.lang.annotations.Language
import org.intellij.lang.annotations.RegExp
import org.junit.jupiter.api.fail
import java.io.File

fun Iterable<File>.assertMatches(
    vararg matchers: FileMatcher
): Iterable<File> {
    val allFiles = toList()
    val allMatchers = matchers.toList()

    val marriedMatchers = mutableSetOf<FileMatcher>()
    val marriedFiles = mutableSetOf<File>()

    allFiles.forEach { file ->
        matchers.forEach { matcher ->
            if (matcher.matches(file)) {
                marriedMatchers.add(matcher)
                marriedFiles.add(file)
            }
        }
    }

    val singleFiles = allFiles.toSet() - marriedFiles
    val singleMatchers = allMatchers.toSet() - marriedMatchers

    if (singleFiles.isEmpty() && singleMatchers.isEmpty()) {
        // Love indeed is real.
        return this
    }

    val errorMessage = buildString {
        if (singleFiles.isNotEmpty()) {
            appendLine("Unexpected files found:")
            singleFiles.forEach { file ->
                appendLine("  $file")
            }
        }

        if (singleFiles.isNotEmpty() && singleMatchers.isNotEmpty()) {
            appendLine()
        }

        if (singleMatchers.isNotEmpty()) {
            appendLine("No files found matching:")
            singleMatchers.forEach { matcher ->
                appendLine("  $matcher")
            }
        }

        appendLine()
        appendLine("All files:")
        allFiles.forEach { file ->
            appendLine("  $file")
        }
    }

    fail(errorMessage)
}

fun Iterable<File>.assertNotMatches(
    vararg matchers: FileMatcher
): Iterable<File> {
    val allFiles = toList()
    val matches = mutableMapOf<FileMatcher, MutableSet<File>>()

    allFiles.forEach { file ->
        matchers.forEach { matcher ->
            if (matcher.matches(file)) {
                matches.getOrPut(matcher, ::mutableSetOf).add(file)
            }
        }
    }

    if (matches.isEmpty()) {
        return this
    }

    val errorMessage = buildString {
        for ((matcher, files) in matches) {
            appendLine("Unexpected match found")
            appendLine("  Matcher: $matcher")
            appendLine("  Files:")
            files.forEach { file ->
                appendLine("      $file")
            }
            appendLine()
        }
        appendLine("All files:")
        allFiles.forEach { file ->
            appendLine("  $file")
        }
    }

    fail(errorMessage)
}

fun PathRegex(@RegExp @Language("RegExp") regex: String): FileMatcher = PathRegex(Regex(regex))

private data class PathRegex(private val regex: Regex) : FileMatcher {
    override fun matches(file: File): Boolean {
        val path = file.absolutePath.replace("""\""", "/")
        return path.matches(regex)
    }

    override fun toString(): String {
        return regex.pattern
    }
}

fun interface FileMatcher {
    fun matches(file: File): Boolean
}

operator fun FileMatcher.minus(other: FileMatcher): FileMatcher = FileMatcher { file ->
    this@minus.matches(file) && !other.matches(file)
}
