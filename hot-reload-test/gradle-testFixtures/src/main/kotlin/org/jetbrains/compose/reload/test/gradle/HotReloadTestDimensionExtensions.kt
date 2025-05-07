/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.junit.jupiter.api.extension.ExtensionContext

internal class AndroidHotReloadTestDimensionExtension : HotReloadTestDimensionExtension {
    override fun transform(
        context: ExtensionContext, tests: List<HotReloadTestInvocationContext>
    ): List<HotReloadTestInvocationContext> {
        if (tests.isEmpty()) return tests
        context.findAnnotation<AndroidHotReloadTest>() ?: return tests
        return tests + tests.last().copy {
            androidVersion = TestedAndroidVersion.default
        }
    }
}

internal class ProjectModeHotReloadTestDimensionExtension : HotReloadTestDimensionExtension {
    override fun transform(
        context: ExtensionContext,
        tests: List<HotReloadTestInvocationContext>
    ): List<HotReloadTestInvocationContext> {
        if (tests.isEmpty()) return tests
        val annotations = context.findRepeatableAnnotations<TestedProjectMode>()
        if (annotations.isEmpty()) return tests
        val modes = annotations.map { it.mode }.toSet()

        return tests.flatMap { context ->
            modes.map { mode -> context.copy { projectMode = mode } }
        }
    }
}
