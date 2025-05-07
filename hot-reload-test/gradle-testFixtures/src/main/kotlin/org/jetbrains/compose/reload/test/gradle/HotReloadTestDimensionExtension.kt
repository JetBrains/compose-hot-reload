/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.junit.jupiter.api.extension.ExtensionContext
import java.util.ServiceLoader

public interface HotReloadTestDimensionExtension {
    public fun transform(
        context: ExtensionContext,
        tests: List<HotReloadTestInvocationContext>
    ): List<HotReloadTestInvocationContext>
}

internal fun buildHotReloadTestDimensions(context: ExtensionContext): List<HotReloadTestInvocationContext> {
    val fromAnnotation = context.findRepeatableAnnotations<ExtendHotReloadTestDimension>().map { annotation ->
        annotation.extension.objectInstance ?: annotation.extension.java.getDeclaredConstructor().newInstance()
    }

    return ServiceLoader.load(HotReloadTestDimensionExtension::class.java).plus(fromAnnotation)
        .fold(listOf(HotReloadTestInvocationContext())) { tests, extension ->
            extension.transform(context, tests)
        }
}
