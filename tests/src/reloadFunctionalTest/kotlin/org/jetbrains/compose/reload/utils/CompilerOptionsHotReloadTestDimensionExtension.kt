package org.jetbrains.compose.reload.utils

import org.jetbrains.compose.reload.test.core.CompilerOption
import org.jetbrains.compose.reload.test.gradle.HotReloadTestDimensionExtension
import org.jetbrains.compose.reload.test.gradle.HotReloadTestInvocationContext
import org.jetbrains.compose.reload.test.gradle.copy
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.util.AnnotationUtils
import kotlin.jvm.optionals.getOrNull

internal class CompilerOptionsHotReloadTestDimensionExtension : HotReloadTestDimensionExtension {
    override fun transform(
        context: ExtensionContext, tests: List<HotReloadTestInvocationContext>
    ): List<HotReloadTestInvocationContext> {
        if (tests.isEmpty()) return tests
        val test = context.testMethod.getOrNull() ?: return tests
        if (AnnotationUtils.isAnnotated(test, TestOnlyDefaultCompilerOptions::class.java)) return tests

        return tests + tests.last().copy {
            compilerOption(CompilerOption.OptimizeNonSkippingGroups, !CompilerOption.OptimizeNonSkippingGroups.default)
        }
    }
}
