/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.analysis.testFixtures

import org.jetbrains.compose.reload.analysis.javap
import org.jetbrains.compose.reload.core.asFileName
import org.jetbrains.compose.reload.test.core.TestEnvironment
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.fail
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText

@OptIn(ExperimentalPathApi::class)
fun checkJavap(testInfo: TestInfo, name: String = "", code: Map<String, ByteArray>) {

    val directory = Path("src/test/resources/javap")
        .resolve(testInfo.testClass.get().name.asFileName().replace(".", "/"))
        .resolve(testInfo.testMethod.get().name.asFileName())
        .resolve(name.asFileName())

    val actualContent = code
        .mapKeys { (path, _) -> directory.resolve("$path.javap.txt") }
        .mapValues { (_, code) ->
            javap(code).trim()
                .replace(Regex("""/.*/Bytecode.class"""), "<bytecode path>")
                .replace(Regex("Last modified.*;"), "Last modified <Date>;")
        }

    if (TestEnvironment.updateTestData && directory.exists()) {
        directory.deleteRecursively()
    }

    if (!directory.exists()) {
        actualContent.forEach { file, code ->
            file.createParentDirectories()
            file.writeText(code)
        }
        if (!TestEnvironment.updateTestData) {
            fail("javap directory '${directory.pathString}' did not exist; Generated")
        }
    }

    val expectedContent = directory.listDirectoryEntries("*.javap.txt")
        .associateWith { path -> path.readText().trim() }

    val unexpectedActualPaths = actualContent.keys - expectedContent.keys
    if (unexpectedActualPaths.isNotEmpty()) {
        fail("Unexpected class files: $unexpectedActualPaths")
    }

    val missingExpectPaths = expectedContent.keys - actualContent.keys
    if (missingExpectPaths.isNotEmpty()) {
        fail("Missing class files: $missingExpectPaths")
    }

    expectedContent.forEach { (path, code) ->
        val actualCode = actualContent.getValue(path)
        if (code != actualCode) {
            path.resolveSibling(path.nameWithoutExtension + "-actual.txt").writeText(actualCode)
            fail("Javap '${path.toUri()}' does not match")
        }
    }
}
