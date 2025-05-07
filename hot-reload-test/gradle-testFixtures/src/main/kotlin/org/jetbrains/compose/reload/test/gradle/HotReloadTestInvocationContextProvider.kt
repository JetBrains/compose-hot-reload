/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.kotlin.tooling.core.compareTo
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import org.junit.platform.commons.util.AnnotationUtils
import java.util.stream.Stream
import kotlin.streams.asStream


internal class HotReloadTestInvocationContextProvider : TestTemplateInvocationContextProvider {
    override fun supportsTestTemplate(context: ExtensionContext): Boolean {
        if (context.testMethod.isEmpty) return false
        return AnnotationUtils.findAnnotation(context.testMethod.get(), HotReloadTest::class.java) != null
    }

    override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
        return buildHotReloadTestDimensions(context)
            .distinct()
            .filter { invocationContext ->
                val kotlinVersionMin = context.findAnnotation<MinKotlinVersion>()?.version
                if (kotlinVersionMin != null && invocationContext.kotlinVersion.version < kotlinVersionMin) {
                    return@filter false
                }

                val kotlinVersionMax = context.findAnnotation<MaxKotlinVersion>()?.version
                if (kotlinVersionMax != null && invocationContext.kotlinVersion.version > kotlinVersionMax) {
                    return@filter false
                }

                true
            }
            .filterIndexed filter@{ index, invocationContext ->
                /* If the 'Debug' annotation is present, then we should filter for the desired target */
                val hotReloadTestFilterAnnotation = context.findAnnotation<Debug>() ?: return@filter true
                Regex(hotReloadTestFilterAnnotation.target).containsMatchIn(invocationContext.getDisplayName(index))
            }
            .apply { assumeTrue(isNotEmpty(), "No matching context") }
            .asSequence().asStream()
    }
}
