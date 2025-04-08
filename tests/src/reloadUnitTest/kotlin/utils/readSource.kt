/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utils

import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.readText

fun readSource(path: String): String {
    return Path("src/reloadUnitTest/kotlin/tests/$path").absolute().readText()
}
