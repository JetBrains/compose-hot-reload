/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:OptIn(ExperimentalComposeLibrary::class)

import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.reload.gradle.HotReloadUsage
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.compose.hot-reload")
    id("org.jetbrains.compose.hot-reload.test")
    `maven-publish`
    `bootstrap-conventions`
    `publishing-conventions`
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        languageVersion = KOTLIN_2_2
        apiVersion = KOTLIN_2_2
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("org.jetbrains.compose.reload.test.core.InternalHotReloadTestApi")
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(compose.runtime)
    implementation(project(":hot-reload-devtools-api"))

    implementation(project(":hot-reload-core"))
    implementation(project(":hot-reload-orchestration"))
    implementation(project(":hot-reload-runtime-api"))

    implementation(compose.desktop.common)
    implementation(compose.material3)
    implementation(compose.components.resources)
    implementation(deps.compose.icons.core)
    implementation(deps.coroutines.swing)
    implementation(deps.kotlinxDatetime)

    implementation(deps.evas)
    implementation(deps.evas.compose)

    testImplementation(project(":hot-reload-runtime-jvm"))
    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
    testImplementation(deps.junit.jupiter)
    testImplementation(deps.junit.jupiter.engine)
    testImplementation(compose.uiTest)
    testImplementation(compose.desktop.currentOs)

    devCompileOnly(project(":hot-reload-agent"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

open class ComposeDevRuntimeCompatibilityRule : AttributeCompatibilityRule<Usage> {
    override fun execute(details: CompatibilityCheckDetails<Usage>) {
        if (details.consumerValue?.name == Usage.JAVA_RUNTIME &&
            details.producerValue?.name == HotReloadUsage.COMPOSE_DEV_RUNTIME_USAGE
        ) {
            details.compatible()
        } else {
            details.incompatible()
        }
    }

}

dependencies {
    attributesSchema.attribute(Usage.USAGE_ATTRIBUTE).compatibilityRules.add(ComposeDevRuntimeCompatibilityRule::class.java)
}
