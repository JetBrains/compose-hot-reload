/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment.STANDARD_JVM
import org.gradle.api.attributes.java.TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE
import org.gradle.api.internal.artifacts.transform.UnzipTransform
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.reload.gradle.kotlinJvmOrNull
import org.jetbrains.compose.reload.gradle.kotlinMultiplatformOrNull
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.tooling.core.extrasLazyProperty

internal const val COMPOSE_DEV_RUNTIME_USAGE = "compose-dev-java-runtime"


/**
 * Hot Reloading works significantly better if only class directories are used.
 * Therefore, this method will create additional variants which will provide the classes dirs directly
 * as outgoing runtime elements.
 */
internal fun Project.setupComposeHotReloadRuntimeElements() {
    project.dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE)
        .compatibilityRules.add(ComposeHotReloadCompatibility::class.java)

    project.dependencies.registerTransform(UnzipTransform::class.java) { transform ->
        transform.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
        transform.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE)
    }

    kotlinJvmOrNull?.apply {
        target.createComposeHotReloadRuntimeElements()
    }

    kotlinMultiplatformOrNull?.apply {
        targets.withType<KotlinJvmTarget>().all { target ->
            target.createComposeHotReloadRuntimeElements()
        }
    }
}


/**
 * We're trying to resolve the classpath to classes dirs:
 * Therefore, we're explicitly trying to resolve classes by looking out for a
 * [ComposeHotReloadMarker].
 *
 * Projects which also do have the hot-reload plugin applied will be able to provide
 * us with a better variant. Projects which do not have this plugin applied will provide
 * use with a regular variant.
 */
internal val KotlinCompilation<*>.composeDevRuntimeDependencies: Configuration by extrasLazyProperty("composeDevRuntimeDependencies") {
    val runtimeConfigurationName = runtimeDependencyConfigurationName ?: compileDependencyConfigurationName
    val runtimeConfiguration = project.configurations.getByName(runtimeConfigurationName)

    project.configurations.create(runtimeConfigurationName + "ComposeDev").apply {
        extendsFrom(runtimeConfiguration)
        isCanBeResolved = true
        isCanBeConsumed = false
        attributes.attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
        attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(COMPOSE_DEV_RUNTIME_USAGE))
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))
        attributes.attribute(TARGET_JVM_ENVIRONMENT_ATTRIBUTE, project.objects.named(STANDARD_JVM))

    }
}

private fun KotlinTarget.createComposeHotReloadRuntimeElements() {
    val main = compilations.getByName("main")
    val runtimeConfiguration = project.configurations.getByName(main.runtimeDependencyConfigurationName ?: return)
    val hotRuntimeConfigurationName = main.runtimeDependencyConfigurationName + "ComposeHot"
    project.configurations.findByName(hotRuntimeConfigurationName)?.let { return }
        ?: project.configurations.create(hotRuntimeConfigurationName) { configuration ->
            configuration.extendsFrom(runtimeConfiguration)

            configuration.attributes.attribute(KotlinPlatformType.attribute, platformType)
            configuration.attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(COMPOSE_DEV_RUNTIME_USAGE))
            configuration.attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))
            configuration.attributes.attribute(TARGET_JVM_ENVIRONMENT_ATTRIBUTE, project.objects.named(STANDARD_JVM))

            configuration.isCanBeResolved = false
            configuration.isCanBeConsumed = true

            project.afterEvaluate {
                main.output.classesDirs.forEach { classesDir ->
                    configuration.outgoing.artifact(classesDir) { artifact ->
                        artifact.builtBy(main.output.allOutputs)
                        artifact.builtBy(main.compileTaskProvider)
                        artifact.type = ArtifactTypeDefinition.DIRECTORY_TYPE
                    }
                }
            }

            configuration.outgoing.artifact(project.provider { main.output.resourcesDirProvider }) { artifact ->
                artifact.builtBy(main.output.allOutputs)
                artifact.builtBy(main.compileTaskProvider)
                artifact.type = ArtifactTypeDefinition.DIRECTORY_TYPE
            }
        }
}


internal class ComposeHotReloadCompatibility : AttributeCompatibilityRule<Usage> {
    override fun execute(details: CompatibilityCheckDetails<Usage>) {
        if (details.consumerValue?.name == COMPOSE_DEV_RUNTIME_USAGE &&
            details.producerValue?.name == Usage.JAVA_RUNTIME
        ) {
            details.compatible()
        }
    }
}
