/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("SuspiciousCollectionReassignment")

package org.jetbrains.compose.reload.utils

import org.jetbrains.compose.reload.test.core.CompilerOption
import org.jetbrains.compose.reload.test.gradle.Headless
import org.jetbrains.compose.reload.test.gradle.HotReloadTestDimensionExtension
import org.jetbrains.compose.reload.test.gradle.HotReloadTestInvocationContext
import org.jetbrains.compose.reload.test.gradle.ProjectMode
import org.jetbrains.compose.reload.test.gradle.TestedComposeVersion
import org.jetbrains.compose.reload.test.gradle.TestedGradleVersion
import org.jetbrains.compose.reload.test.gradle.TestedKotlinVersion
import org.jetbrains.compose.reload.test.gradle.copy
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.util.AnnotationUtils
import java.awt.GraphicsEnvironment
import kotlin.jvm.optionals.getOrNull
import kotlin.math.absoluteValue

class HotReloadTestDimensionBuilder : HotReloadTestDimensionExtension {
    override fun transform(
        context: ExtensionContext, tests: List<HotReloadTestInvocationContext>
    ): List<HotReloadTestInvocationContext> {
        val defaultKotlinVersion = repositoryDeclaredTestDimensions.kotlin.single { version ->
            version.isDefault
        }

        val defaultGradleVersion = repositoryDeclaredTestDimensions.gradle.single { version ->
            version.isDefault
        }

        val defaultComposeVersion = repositoryDeclaredTestDimensions.compose.single { version ->
            version.isDefault
        }

        val baselineContext = HotReloadTestInvocationContext {
            kotlinVersion = TestedKotlinVersion(KotlinToolingVersion(defaultKotlinVersion.version))
            gradleVersion = TestedGradleVersion(defaultGradleVersion.version)
            composeVersion = TestedComposeVersion(defaultComposeVersion.version)
        }

        var result = setOf(baselineContext)

        /* Expand Kotlin Versions */
        if (!AnnotationUtils.isAnnotated(context.requiredTestMethod, QuickTest::class.java)) {
            result += repositoryDeclaredTestDimensions.kotlin.flatMap { declaredKotlinVersion ->
                result.map { context ->
                    context.copy {
                        kotlinVersion = TestedKotlinVersion(KotlinToolingVersion(declaredKotlinVersion.version))
                    }
                }
            }
        }

        if (AnnotationUtils.isAnnotated(context.requiredTestMethod, GradleIntegrationTest::class.java)) {
            /* Expand Gradle Versions */
            result += repositoryDeclaredTestDimensions.gradle.flatMap { declaredGradleVersion ->
                result.mapNotNull { context ->
                    /* Only expand on the default kotlin versions */
                    if (context.kotlinVersion.version.toString() != defaultKotlinVersion.version) return@mapNotNull null
                    context.copy {
                        gradleVersion = TestedGradleVersion(declaredGradleVersion.version)
                    }
                }
            }

            /* Expand with testing against Kotlin/JVM as well */
            result += result.mapNotNull { context ->
                if (context.kotlinVersion.version.toString() != defaultKotlinVersion.version) return@mapNotNull null
                if (context.gradleVersion.version != defaultGradleVersion.version) return@mapNotNull null
                context.copy { projectMode = ProjectMode.Jvm }
            }
        }

        /* Expand Compose version */
        if (!AnnotationUtils.isAnnotated(context.requiredTestMethod, QuickTest::class.java)) {
            result += repositoryDeclaredTestDimensions.compose.map { declaredComposeVersion ->
                baselineContext.copy {
                    composeVersion = TestedComposeVersion(declaredComposeVersion.version)
                }
            }

        }

        /* Expand compiler options */
        if (!AnnotationUtils.isAnnotated(context.requiredTestMethod, TestOnlyDefaultCompilerOptions::class.java)) {
            result += baselineContext.copy {
                compilerOption(
                    CompilerOption.OptimizeNonSkippingGroups,
                    !CompilerOption.OptimizeNonSkippingGroups.default
                )
            }
        }

        return result.sortedWith(
            compareBy(
                { it.kotlinVersion.version },
                { it.gradleVersion.version },
                { it.composeVersion.version },
                { it.compilerOptions.contains(CompilerOption.OptimizeNonSkippingGroups) }
            )
        )
    }
}

class HotReloadTestDimensionFilter : HotReloadTestDimensionExtension {
    override fun transform(
        context: ExtensionContext,
        tests: List<HotReloadTestInvocationContext>
    ): List<HotReloadTestInvocationContext> {
        var result = tests.toList()

        System.getenv("TESTED_KOTLIN_VERSION")?.let { enforcedKotlinVersion ->
            result = result.filter { it.kotlinVersion.version.toString() == enforcedKotlinVersion }
        }

        System.getenv("TESTED_COMPOSE_VERSION")?.let { enforcedComposeVersions ->
            result = result.filter { it.composeVersion.version == enforcedComposeVersions }
        }

        System.getenv("TESTED_DEFAULT_COMPILER_OPTIONS")?.toBooleanStrict()?.let { onlyDefaultCompilerOptions ->
            result = result.filter { context ->
                val isDefaultOnly = context.compilerOptions.all { option -> option.value == option.key.default }
                isDefaultOnly == onlyDefaultCompilerOptions
            }
        }

        if (System.getProperty("hostIntegrationTests") == "true") {
            result = result.filter { context ->
                context.kotlinVersion.version.toString() ==
                    repositoryDeclaredTestDimensions.kotlin.single { it.isDefault }.version
            }.filter { context ->
                context.gradleVersion.version ==
                    repositoryDeclaredTestDimensions.gradle.single { it.isDefault }.version
            }.filter { context ->
                context.composeVersion.version ==
                    repositoryDeclaredTestDimensions.compose.single { it.isDefault }.version
            }.filter { context ->
                context.compilerOptions.all { option -> option.value == option.key.default }
            }
        }

        if (GraphicsEnvironment.isHeadless()) {
            result = result.filter { invocationContext ->
                AnnotationUtils.findAnnotation(context.requiredTestMethod, Headless::class.java)
                    .getOrNull()?.isHeadless ?: true
            }
        }

        val bucket = System.getenv("TESTED_BUCKET")?.toInt()
        val bucketsCount = System.getenv("TESTED_BUCKETS_COUNT")?.toInt()
        if (bucket != null && bucketsCount != null) {
            result = result.filter { invocationContext ->
                (invocationContext.getDisplayName().hashCode().absoluteValue % bucketsCount) + 1 == bucket
            }
        }

        return result
    }

}
