/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.compose.reload.utils

import org.jetbrains.compose.reload.test.gradle.HotReloadTestDimensionExtension
import org.jetbrains.compose.reload.test.gradle.HotReloadTestInvocationContext
import org.jetbrains.compose.reload.test.gradle.TestedComposeVersion
import org.jetbrains.compose.reload.test.gradle.copy
import org.junit.jupiter.api.extension.ExtensionContext

private enum class AdditionalTestedComposeVersion(val value: TestedComposeVersion) {
    v1_8_0(TestedComposeVersion("1.8.0-alpha04"))
}

class ComposeHotReloadTestDimensionExtension : HotReloadTestDimensionExtension {
    override fun transform(
        context: ExtensionContext, tests: List<HotReloadTestInvocationContext>
    ): List<HotReloadTestInvocationContext> {
        if (tests.isEmpty()) return tests

        /* We'll explode the last configuration */
        val lastConfiguration = tests.last()
        return tests + AdditionalTestedComposeVersion.entries.map { additionalTestedComposeVersion ->
            lastConfiguration.copy { composeVersion = additionalTestedComposeVersion.value }
        }
    }
}
