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
