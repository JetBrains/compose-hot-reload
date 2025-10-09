/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    kotlin("jvm") version deps.versions.kotlin
}

gradlePlugin {
    plugins.register("root-conventions") {
        id = "root-conventions"
        implementationClass = "org.jetbrains.compose.reload.build.RootPlugin"
    }

    plugins.register("main-conventions") {
        id = "main-conventions"
        implementationClass = "org.jetbrains.compose.reload.build.MainConventionsPlugin"
    }

    plugins.register("test-conventions") {
        id = "test-conventions"
        implementationClass = "org.jetbrains.compose.reload.build.TestConventionsPlugin"
    }

    plugins.register("bootstrap-conventions") {
        id = "bootstrap-conventions"
        implementationClass = "org.jetbrains.compose.reload.build.BootstrapConventionsPlugin"
    }


    plugins.register("build.publish") {
        id = "build.publish"
        implementationClass = "org.jetbrains.compose.reload.build.PublishingPlugin"
    }

    plugins.register("build.compilerTest") {
        id = "build.compilerTest"
        implementationClass = "org.jetbrains.compose.reload.build.CompilerTestSupportPlugin"
    }

    plugins.register("build.apiValidation") {
        id = "build.apiValidation"
        implementationClass = "org.jetbrains.compose.reload.build.ApiValidationPlugin"
    }

    plugins.register("build.orchestrationCompatibilityTests") {
        id = "build.orchestrationCompatibilityTests"
        implementationClass = "org.jetbrains.compose.reload.build.OrchestrationCompatibilityTestsPlugin"
    }
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.compose.reload.InternalHotReloadApi")
        optIn.add("org.jetbrains.compose.reload.DelicateHotReloadApi")
    }
}

dependencies {
    val bootstrapVersion = project.bootstrapVersion
    implementation("org.jetbrains.compose.hot-reload:hot-reload-core:${bootstrapVersion}")
    implementation("org.jetbrains.compose.hot-reload:hot-reload-gradle-core:${bootstrapVersion}")
    implementation("org.jetbrains.compose.hot-reload:hot-reload-gradle-plugin:${bootstrapVersion}")
    implementation("org.jetbrains.compose.hot-reload:hot-reload-test-gradle-plugin:${bootstrapVersion}")
    implementation(kotlin("gradle-plugin:${deps.versions.kotlin.get()}"))

    implementation(deps.kotlinxSerialization.json)
    implementation(deps.kotlinxSerialization.kaml)
    implementation(deps.ktor.client.core)
    implementation(deps.ktor.client.cio)
    implementation(deps.asm)
    implementation(deps.asm.tree)

    api("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:${deps.versions.kotlin.get()}")
    api("org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:${deps.versions.kotlin.get()}")
    api("org.jetbrains.compose:org.jetbrains.compose.gradle.plugin:${deps.versions.compose.get()}")
    api("com.android.tools.build:gradle:${deps.versions.androidGradlePlugin.get()}")

    api(deps.benchmark.gradlePlugin)
    api(deps.binaryCompatibilityValidator.gradlePlugin)
    api(deps.shadow.gradlePlugin)
    api(deps.pluginPublish.gradlePlugin)
}
