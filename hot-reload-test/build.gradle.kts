/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
import org.jetbrains.compose.reload.gradle.HotReloadUsage
import org.jetbrains.compose.reload.gradle.HotReloadUsageType
import org.jetbrains.compose.resources.ResourcesExtension.ResourceClassGeneration

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    build.publish
    build.apiValidation
    `bootstrap-conventions`
}

kotlin {
    compilerOptions {
        explicitApi()
    }
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib"))
    implementation(deps.coroutines.core)
    implementation(deps.coroutines.test)
    implementation(deps.coroutines.swing)
    implementation(deps.asm.tree)
    implementation(deps.asm)
    implementation(project(":hot-reload-core"))
    implementation(project(":hot-reload-test:core"))
    implementation(project(":hot-reload-analysis"))
    implementation(kotlin("compiler-embeddable"))
    implementation(project(":hot-reload-orchestration"))

    api(deps.compose.material3)
    implementation(deps.compose.resources)
}

compose.resources {
    generateResClass = ResourceClassGeneration.Always
}

configurations.compileClasspath {
    attributes.attribute(HotReloadUsageType.attribute, HotReloadUsageType.Dev)
    attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(HotReloadUsage.COMPOSE_DEV_RUNTIME_USAGE))
}

dependencies {
    compileOnly(project(":hot-reload-agent"))
    compileOnly(project(":hot-reload-runtime-api"))
    compileOnly(project(":hot-reload-runtime-jvm"))
}
