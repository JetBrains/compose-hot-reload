/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.core.testFixtures

import java.nio.file.Path
import kotlin.io.path.Path

fun String.sanitized(): String {
    return lines().joinToString("\n").trim()
}

val repositoryRoot: Path by lazy {
    Path(System.getProperty("repo.path") ?: error("Missing 'repo.path' system property"))
}
