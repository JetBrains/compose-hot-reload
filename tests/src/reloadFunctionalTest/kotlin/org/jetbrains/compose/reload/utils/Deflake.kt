/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.reload.utils

import org.jetbrains.compose.reload.test.gradle.HotReloadTestDimension
import org.jetbrains.compose.reload.test.gradle.HotReloadTestDimensionExtension
import org.jetbrains.compose.reload.test.gradle.HotReloadTestInvocationContext
import org.jetbrains.compose.reload.test.gradle.copy
import org.jetbrains.compose.reload.test.gradle.findAnnotation
import org.jetbrains.kotlin.tooling.core.extrasKeyOf
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Can be used to 'Deflake' a test by running it many times.
 */
@Suppress("unused") // Debugging/Deflaking utility
annotation class Deflake

data class DeflakeInvocationIndex(val index: Int) : HotReloadTestDimension {
    override fun displayName(): String {
        return "Deflake #$index"
    }

    companion object {
        val key = extrasKeyOf<DeflakeInvocationIndex>()
    }
}

internal class DeflakeExtension : HotReloadTestDimensionExtension {
    override fun transform(
        context: ExtensionContext, tests: List<HotReloadTestInvocationContext>
    ): List<HotReloadTestInvocationContext> {
        context.findAnnotation<Deflake>() ?: return tests
        return buildList {
            repeat(1024) { index ->
                addAll(tests.map { context ->
                    context.copy {
                        extras[DeflakeInvocationIndex.key] = DeflakeInvocationIndex(index)
                    }
                })
            }
        }
    }

}
