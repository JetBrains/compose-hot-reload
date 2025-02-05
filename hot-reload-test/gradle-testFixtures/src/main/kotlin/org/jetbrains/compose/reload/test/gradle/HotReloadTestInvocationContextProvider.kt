package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.kotlin.tooling.core.compareTo
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import org.junit.platform.commons.util.AnnotationUtils.findAnnotation
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull
import kotlin.streams.asStream


internal class HotReloadTestInvocationContextProvider : TestTemplateInvocationContextProvider {
    override fun supportsTestTemplate(context: ExtensionContext): Boolean {
        if (context.testMethod.isEmpty) return false
        return findAnnotation(context.testMethod.get(), HotReloadTest::class.java) != null
    }

    override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
        return buildHotReloadTestDimensions(context)
            .distinct()
            .filter { invocationContext ->
                val kotlinVersionMin = findAnnotation(context.testMethod, MinKotlinVersion::class.java)
                    .getOrNull()?.version
                if (kotlinVersionMin != null && invocationContext.kotlinVersion.version < kotlinVersionMin) {
                    return@filter false
                }

                val kotlinVersionMax = findAnnotation(context.testMethod, MaxKotlinVersion::class.java)
                    .getOrNull()?.version
                if (kotlinVersionMax != null && invocationContext.kotlinVersion.version > kotlinVersionMax) {
                    return@filter false
                }

                true
            }
            .filterIndexed filter@{ index, invocationContext ->
                /* If the 'Debug' annotation is present, then we should filter for the desired target */
                val hotReloadTestFilterAnnotation =
                    findAnnotation(context.testMethod, Debug::class.java).getOrNull()
                        ?: return@filter true
                Regex(hotReloadTestFilterAnnotation.target).matches(invocationContext.getDisplayName(index))
            }
            .apply { assumeTrue(isNotEmpty(), "No matching context") }
            .asSequence().asStream()
    }
}
