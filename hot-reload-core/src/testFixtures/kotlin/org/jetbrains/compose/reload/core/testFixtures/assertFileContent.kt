/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core.testFixtures

import org.jetbrains.compose.reload.test.core.TestEnvironment
import org.junit.jupiter.api.fail
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun assertFileContent(expectFile: Path, actualText: String) {
    expectFile.createParentDirectories()

    if (!expectFile.exists() || TestEnvironment.updateTestData) {
        expectFile.writeText(actualText.sanitized())
        if (!TestEnvironment.updateTestData) error("${expectFile.toUri()} did not exist; Generated")
    }

    if (expectFile.readText().sanitized() != actualText.sanitized()) {
        val actualFile = expectFile.resolveSibling(
            "${expectFile.nameWithoutExtension}-actual.${expectFile.extension}"
        )
        actualFile.writeText(actualText.sanitized())
        fail("${expectFile.toUri()} did not match\n${actualFile.toUri()}")
    }
}
