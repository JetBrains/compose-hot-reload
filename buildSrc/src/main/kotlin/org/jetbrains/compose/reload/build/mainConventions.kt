/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.build

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.reload.gradle.HotReloadUsage
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

open class MainConventionsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        withProject(target) {
            setupDefaultDependencies()
            setupKotlinStdlibDependency()
            setupResolveDependenciesTask()
            setupCompileLifecycleTask()
            setupJavaCompatibility()
            setupKotlinCompatibility()
            setupDefaultOptIns()
        }
    }
}


private fun Project.setupDefaultDependencies() {
    configureKotlin {
        if (this !is KotlinJvmProjectExtension) return@configureKotlin
        dependencies {
            if (project.name != "hot-reload-core") {
                "testImplementation"(testFixtures(project(":hot-reload-core")))
            }

            if (project.name != "hot-reload-annotations") {
                "api"(project(":hot-reload-annotations"))
            }

            attributesSchema.attribute(Usage.USAGE_ATTRIBUTE)
                .compatibilityRules.add(HotReloadUsage.CompatibilityRule::class.java)
        }
    }
}

private fun Project.setupKotlinStdlibDependency() {
    configureKotlin {
        if (this is KotlinMultiplatformExtension) {
            sourceSets.commonMain.dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.21")
            }
        }

        if (this is KotlinJvmProjectExtension) {
            dependencies {
                "implementation"("org.jetbrains.kotlin:kotlin-stdlib:2.1.21")
            }
        }
    }
}

private fun Project.setupResolveDependenciesTask() {
    tasks.register("resolveDependencies") {
        val files = project.files()
        inputs.files(files)
        configurations.all {
            val configuration = this

            if (!configuration.isCanBeResolved) return@all

            if (
                configuration.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)?.name !in
                listOf(Usage.JAVA_RUNTIME, Usage.JAVA_API, *KotlinUsages.values.toTypedArray())
            ) return@all

            if (configuration.isCanBeResolved) {
                files.from(configuration.incoming.artifactView { isLenient = true }.files)
            }
        }

        doLast {
            files.files
        }
    }
}

private fun Project.setupCompileLifecycleTask() {
    tasks.register("compile") {
        dependsOn(tasks.withType<AbstractCompile>())
    }
}

private fun Project.setupJavaCompatibility() {
    configureKotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
            @Suppress("UnstableApiUsage")
            vendor.set(JvmVendorSpec.JETBRAINS)
        }
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            this.jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }
}

private fun Project.setupKotlinCompatibility() {
    configureKotlin {
        this as HasConfigurableKotlinCompilerOptions<*>
        compilerOptions {
            languageVersion.set(KotlinVersion.KOTLIN_2_1)
            apiVersion.set(KotlinVersion.KOTLIN_2_1)
        }
    }
}

private fun Project.setupDefaultOptIns() {
    configureKotlin {
        if (this is HasConfigurableKotlinCompilerOptions<*>) {
            this.compilerOptions {
                optIn.add("org.jetbrains.compose.reload.InternalHotReloadApi")
                optIn.add("org.jetbrains.compose.reload.DelicateHotReloadApi")
                optIn.add("org.jetbrains.compose.reload.ExperimentalHotReloadApi")
            }
        }
    }
}
