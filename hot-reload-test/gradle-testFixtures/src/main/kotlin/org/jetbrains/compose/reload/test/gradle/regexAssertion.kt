/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.intellij.lang.annotations.Language
import org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi
import kotlin.test.fail

@InternalHotReloadTestApi
public fun String.assertMatchesRegex(@Language("RegExp") regex: String) {
    return assertMatchesRegex(Regex(regex))
}

@InternalHotReloadTestApi
public fun String.assertMatchesRegex(regex: Regex) {
    if (!matches(regex)) {
        fail("Expected regex: '$regex', Actual: '$this'")
    }
}
