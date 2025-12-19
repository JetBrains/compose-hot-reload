/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalComposeLibrary::class)

import org.gradle.api.attributes.java.TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.reload.gradle.HotReloadUsage
import org.jetbrains.compose.reload.gradle.HotReloadUsageType
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.compose.hot-reload")
    `bootstrap-conventions`
    build.publish
    build.apiValidation
    build.withShadowing
}

kotlin {
    compilerOptions {
        explicitApi()
    }
}

/**
 * The published artifacts are not usable for 'generic' JAVA_RUNTIME.
 * It only makes sense for the hot reload runs; therefore,
 * we publish the elemnts with our special 'COMPOSE_DEV_RUNTIME' usage.
 */
configurations.shadowRuntimeElements.configure {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(HotReloadUsage.COMPOSE_DEV_RUNTIME_USAGE))
        attribute(HotReloadUsageType.attribute, HotReloadUsageType.Dev)
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
        attribute(TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
    }
}

/**
 * Note:
 * Since we allow embedding/shadowing some libraries into this runtime artifact,
 * we should very well ensure that we relocate all entries to avoid clashes with the user application.
 */
withShadowing {
    relocate(
        from = "org.jetbrains.compose.resources",
        to = "org.jetbrains.compose.reload.shaded.resources"
    )
}

compose {
    resources {
        generateResClass = always
    }
}

kotlin.sourceSets.main.configure {
    kotlin.srcDir("src/main/kotlinUI")
}
dependencies {
    compileOnly(deps.compose.desktop.common) {
        exclude(group = "org.jetbrains.compose.material")
        version {
            strictly(deps.versions.composeMin.get())
        }
    }

    implementation(project(":hot-reload-devtools-api"))
    shadowedImplementation(compose.components.resources)
    devImplementation(compose.components.resources)

    compileOnly(project(":hot-reload-core"))
    compileOnly(project(":hot-reload-agent"))
    compileOnly(project(":hot-reload-orchestration"))

    testImplementation(kotlin("test"))
    testImplementation(deps.compose.desktop.common)
}
