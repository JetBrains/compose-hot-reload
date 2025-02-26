/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.utils

import org.gradle.api.tasks.JavaExec
import org.jetbrains.compose.reload.ComposeHotReloadArguments
import org.jetbrains.compose.reload.core.HotReloadProperty
import kotlin.test.assertEquals
import kotlin.test.fail

fun JavaExec.getComposeHotReloadArgumentsOrFail(): ComposeHotReloadArguments {
    return jvmArgumentProviders.filterIsInstance<ComposeHotReloadArguments>().firstOrNull()
        ?: fail("Missing 'ComposeHotReloadArguments' argument provider of '$path'")
}

fun ComposeHotReloadArguments.getSystemPropertyOrFail(property: HotReloadProperty): String {
    val declarations = asArguments().filter { argument -> argument.startsWith("-D${property.key}=") }
    if (declarations.isEmpty()) {
        fail("Missing '${property.key}' in arguments:\n${asArguments().joinToString("\n")}")
    }

    if (declarations.size > 1) {
        fail("Multiple declarations of '${property.key}' in arguments:\n${declarations.joinToString("\n")}")
    }

    return declarations.first().substringAfter("=")
}

fun ComposeHotReloadArguments.assertSystemPropertyEquals(
    property: HotReloadProperty, expectedValue: String
) {
    val actualValue = getSystemPropertyOrFail(property)
    assertEquals(
        expectedValue, actualValue,
        "Expected '${property.key}' system property to be '$expectedValue'; found '$actualValue'"
    )
}
