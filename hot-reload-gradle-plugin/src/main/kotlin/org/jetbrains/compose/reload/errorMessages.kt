/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload

internal object ErrorMessages {
    fun missingMainClassProperty() = """
        Missing 'mainClass' property. Please invoke the task with '-PmainClass=...`
        Example: ./gradlew runHot -PmainClass=my.package.MainKt
    """.trimIndent()
}
