/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

import org.gradle.api.attributes.java.TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    kotlin("jvm") apply false
    `maven-publish` apply false
}

/**
 * We're creating a 'jvmDev' source set, compilation and publication which can be used at runtime
 * when actually running in 'development mode'. Code in such a source set will then not 'pollute' the
 * production builds of applications.
 */
extensions.configure<KotlinJvmProjectExtension> {
    val main = target.compilations.getByName("main")
    val test = target.compilations.getByName("test")
    val dev = target.compilations.create("dev")
    val shared = target.compilations.create("shared")

    dev.associateWith(shared)
    main.associateWith(shared)
    test.associateWith(dev)

    tasks.withType<Zip>().configureEach {
        from(shared.output.allOutputs)
    }

    val jvmDevJar = project.tasks.register<Jar>("jvmDevJar") {
        from(dev.output.allOutputs)
        archiveClassifier.set("dev")
    }

    val jvmDevRuntimeElements = project.configurations.create("jvmDevRuntimeElements") {
        extendsFrom(dev.configurations.runtimeDependencyConfiguration)
        isCanBeResolved = false
        isCanBeConsumed = true

        attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named("compose-dev-java-runtime"))
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))
        attributes.attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
        attributes.attribute(TARGET_JVM_ENVIRONMENT_ATTRIBUTE, project.objects.named(TargetJvmEnvironment.STANDARD_JVM))

        attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named("compose-dev-java-runtime"))

        outgoing.artifact(jvmDevJar) {
            classifier = "dev"
        }
    }

    val java = components.getByName("java") as AdhocComponentWithVariants

    java.addVariantsFromConfiguration(jvmDevRuntimeElements) {
        mapToOptional()
        mapToMavenScope("runtime")
    }
}
