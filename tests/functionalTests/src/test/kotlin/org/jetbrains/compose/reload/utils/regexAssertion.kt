package org.jetbrains.compose.reload.utils

import org.intellij.lang.annotations.Language
import kotlin.test.fail

fun String.assertMatchesRegex(@Language("RegExp") regex: String) {
    return assertMatchesRegex(Regex(regex))
}

fun String.assertMatchesRegex(regex: Regex) {
    if (!matches(regex)) {
        fail("Expected regex: '$regex', Actual: '$this'")
    }
}