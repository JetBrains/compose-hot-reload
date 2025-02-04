package org.jetbrains.compose.reload.test.gradle

import org.jetbrains.compose.reload.test.core.CompilerOption
import org.jetbrains.compose.reload.test.core.CompilerOption.OptimizeNonSkippingGroups
import org.jetbrains.compose.reload.test.core.CompilerOptions
import org.jetbrains.compose.reload.test.core.TestEnvironment
import org.jetbrains.kotlin.tooling.core.compareTo
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.Extension
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
        return TestedGradleVersion.entries.flatMap { testedGradleVersion ->
            TestedKotlinVersion.entries.flatMap { testedKotlinVersion ->
                TestedComposeVersion.entries.flatMap { testedComposeVersion ->
                    ProjectMode.entries.map { mode ->
                        HotReloadTestInvocationContext(
                            gradleVersion = testedGradleVersion,
                            kotlinVersion = testedKotlinVersion,
                            composeVersion = testedComposeVersion,
                            androidVersion = null,
                            projectMode = mode,
                            compilerOptions = CompilerOptions.default,
                        )
                    }
                }
            }
        }
            .filter { invocationContext ->
                /* Only run in 'Jvm' mode for 'HostIntegrationTests' */
                if (invocationContext.projectMode == ProjectMode.Jvm) {
                    //return@filter findAnnotation(context.testMethod, HostIntegrationTest::class.java).isPresent
                    return@filter false
                }
                true
            }
            .filter { invocationContext ->
                invocationContext.projectMode == ProjectMode.Jvm ||
                    findAnnotation(context.testMethod, TestOnlyJvm::class.java).isEmpty
            }
            .filter { invocationContext ->
                invocationContext.projectMode == ProjectMode.Kmp ||
                    findAnnotation(context.testMethod, TestOnlyKmp::class.java).isEmpty
            }
            .filter { invocationContext ->
                (findAnnotation(context.testMethod, TestOnlyLatestVersions::class.java).isEmpty &&
                    !TestEnvironment.testOnlyLatestVersions) ||
                    invocationContext.gradleVersion == TestedGradleVersion.entries.last() &&
                    invocationContext.kotlinVersion == TestedKotlinVersion.entries.last() &&
                    invocationContext.composeVersion == TestedComposeVersion.entries.last() &&
                    (invocationContext.androidVersion == null || invocationContext.androidVersion == TestedAndroidVersion.entries.last())
            }
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
            .run {
                if (findAnnotation(context.testMethod, TestOnlyDefaultCompilerOptions::class.java).isEmpty) {
                    this + this.lastOrNull()?.run {
                        val isNonSkippingGroupsEnabled = compilerOptions[OptimizeNonSkippingGroups] == true
                        copy(compilerOptions = compilerOptions + mapOf(OptimizeNonSkippingGroups to !isNonSkippingGroupsEnabled))
                    }
                } else this
            }
            .filterNotNull()
            .filterIndexed filter@{ index, invocationContext ->
                /* If the 'Debug' annotation is present, then we should filter for the desired target */
                val debugAnnotation = findAnnotation(context.testMethod, Debug::class.java).getOrNull()
                    ?: return@filter true
                Regex(debugAnnotation.target).matches(invocationContext.getDisplayName(index))
            }
            .apply { assumeTrue(isNotEmpty(), "No matching context") }
            .asSequence().asStream()
    }
}

internal data class HotReloadTestInvocationContext(
    val gradleVersion: TestedGradleVersion,
    val composeVersion: TestedComposeVersion,
    val kotlinVersion: TestedKotlinVersion,
    val androidVersion: TestedAndroidVersion?,
    val projectMode: ProjectMode,
    val compilerOptions: Map<CompilerOption, Boolean>
) : TestTemplateInvocationContext {
    override fun getDisplayName(invocationIndex: Int): String {
        return buildString {
            append("$projectMode,")
            append(" Gradle $gradleVersion,")
            append(" Kotlin $kotlinVersion,")
            append(" Compose $composeVersion,")
            if (androidVersion != null) {
                append(" Android $androidVersion")
            }

            /* Append 'non default' compiler options */
            compilerOptions.filter { (key, value) -> CompilerOptions.default[key] != value }.forEach { (key, value) ->
                append(" $key=$value,")
            }
        }
    }

    override fun getAdditionalExtensions(): List<Extension> {
        return listOf(
            SimpleValueProvider(gradleVersion),
            SimpleValueProvider(kotlinVersion),
            SimpleValueProvider(composeVersion),
            HotReloadTestFixtureExtension(this),
        )
    }
}
