/*
 * Copyright 2024-2026 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.core.asFileName
import org.jetbrains.compose.reload.core.withAsyncTrace
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeResult
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.SemanticTreeRequest
import org.jetbrains.compose.reload.test.core.TestEnvironment
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.fail

public suspend fun HotReloadTestFixture.checkSemanticTree(name: String): Unit =
    withAsyncTrace("'checkSemanticTree($name)'") run@{
        val tree = sendMessage(SemanticTreeRequest()) {
            skipToMessage<SemanticTreeResult>()
        }.tree.prettyPrintJson()

        val directory = semanticTreesDirectory()
            .resolve(testClassName.asFileName().replace(".", "/"))
            .resolve(testMethodName.asFileName())

        val expectFile = directory.resolve("$name.json")

        if (TestEnvironment.updateTestData) {
            expectFile.deleteIfExists()
            expectFile.createParentDirectories()
            expectFile.writeText(tree)
            return@run
        }

        if (!expectFile.exists()) {
            expectFile.createParentDirectories()
            expectFile.writeText(tree)
            fail("Semantic tree '${expectFile.toUri()}' did not exist; Generated")
        }

        val expected = expectFile.readText()
        if (tree != expected) {
            val actualFile = expectFile.resolveSibling("${expectFile.nameWithoutExtension}-actual.json")
            actualFile.writeText(tree)
            fail("Semantic tree ${expectFile.toUri()} does not match; actual written to ${actualFile.toUri()}")
        }
    }

private fun String.prettyPrintJson(): String = buildString {
    var indent = 0
    var inString = false
    var escape = false

    for (c in this@prettyPrintJson) {
        when {
            escape -> { append(c); escape = false }
            c == '\\' && inString -> { append(c); escape = true }
            c == '"' -> { append(c); inString = !inString }
            inString -> append(c)
            c == '{' || c == '[' -> {
                append(c); append('\n')
                indent++
                repeat(indent) { append("  ") }
            }
            c == '}' || c == ']' -> {
                append('\n')
                indent--
                repeat(indent) { append("  ") }
                append(c)
            }
            c == ',' -> {
                append(c); append('\n')
                repeat(indent) { append("  ") }
            }
            c == ':' -> append(": ")
            else -> append(c)
        }
    }
}
