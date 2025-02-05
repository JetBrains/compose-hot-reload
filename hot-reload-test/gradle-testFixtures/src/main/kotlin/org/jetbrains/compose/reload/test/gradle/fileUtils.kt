/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

@InternalHotReloadTestApi
public fun Path.replaceText(old: String, new: String) {
    writeText(readText().replace(old, new))
}
