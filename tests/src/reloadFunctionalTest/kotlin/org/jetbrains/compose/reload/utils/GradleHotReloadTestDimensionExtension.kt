/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.utils

import org.jetbrains.compose.reload.test.gradle.HotReloadTestDimensionExtension
import org.jetbrains.compose.reload.test.gradle.HotReloadTestInvocationContext
import org.jetbrains.compose.reload.test.gradle.TestedGradleVersion
import org.jetbrains.compose.reload.test.gradle.TestedKotlinVersion
import org.jetbrains.compose.reload.test.gradle.copy
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.util.AnnotationUtils
import kotlin.jvm.optionals.getOrNull

private class GradleIntegrationTestConfiguration(
    val gradleVersion: TestedGradleVersion,
    val kotlin: TestedKotlinVersion
)

private val additionalTestConfigurations = listOf(
    GradleIntegrationTestConfiguration(
        TestedGradleVersion("8.7"), TestedKotlinVersion.default
    )
)

internal class GradleHotReloadTestDimensionExtension : HotReloadTestDimensionExtension {
    override fun transform(
        context: ExtensionContext,
        tests: List<HotReloadTestInvocationContext>
    ): List<HotReloadTestInvocationContext> {
        if (tests.isEmpty()) return tests
        val test = context.testMethod.getOrNull() ?: return tests
        if (!AnnotationUtils.isAnnotated(test, GradleIntegrationTest::class.java)) return tests

        return tests.flatMap { context ->
            additionalTestConfigurations.map { config ->
                context.copy {
                    gradleVersion = config.gradleVersion
                    kotlinVersion = config.kotlin
                }
            } + context
        }
    }

}
