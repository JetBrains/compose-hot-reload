/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.jetbrains.compose.reload.build

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper

open class TestConventionsPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = withProject(target) {
        tasks.withType<AbstractTestTask>().configureEach {
            if (this is Test) {
                useJUnitPlatform()

                maxHeapSize = "1G"
                properties.filter { (key, _) -> key.startsWith("chr") }.forEach { (key, value) ->
                    systemProperty(key, value.toString())
                }

                javaLauncher.set(serviceOf<JavaToolchainService>().launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(21))
                    vendor.set(JvmVendorSpec.JETBRAINS)
                })

                systemProperty(
                    "firework.version",
                    project.versionCatalogs.named("deps").findVersion("kotlin").get().requiredVersion
                )

                systemProperty(
                    "repo.path", project.rootDir.absolutePath
                )

                /* We do not want to open actual windows */
                systemProperty("apple.awt.UIElement", true)

                if (!providers.gradleProperty("chr.tests.sequential").isPresent) {
                    val parallelism = providers.gradleProperty("chr.tests.parallelism").getOrElse("2").toInt()
                    systemProperty("junit.jupiter.execution.parallel.enabled", true)
                    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
                    systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
                    systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", parallelism)
                    systemProperty("junit.jupiter.execution.parallel.config.fixed.max-pool-size", parallelism)
                }
            }

            testLogging {
                showStandardStreams = true
                events = setOf(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
            }
        }

        plugins.withType<KotlinPluginWrapper> {
            dependencies {
                if (project.name != "hot-reload-core") {
                    "testImplementation"(testFixtures(project(":hot-reload-core")))
                }

                "testImplementation"(kotlin("test"))
                "testImplementation"(project.versionCatalogs.named("deps").findLibrary("junit-jupiter").get())
                "testImplementation"(project.versionCatalogs.named("deps").findLibrary("junit-jupiter-engine").get())
            }
        }

        tasks.named { it == "check" }.configureEach {
            dependsOn(tasks.withType<AbstractTestTask>())
        }
    }
}
