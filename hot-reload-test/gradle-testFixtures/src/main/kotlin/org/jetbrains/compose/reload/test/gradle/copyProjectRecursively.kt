/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createParentDirectories

@OptIn(ExperimentalPathApi::class)
@Suppress("unused") // Used for debugging
public fun HotReloadTestFixture.copyProjectRecursively(into: Path) {
    projectDir.path.copyToRecursively(
        target = into.createParentDirectories(),
        followLinks = false,
        overwrite = true
    )
}
