/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.build

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withNormalizer
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.gradle.intellijDebuggerDispatchPort
import org.jetbrains.compose.reload.gradle.lazyProjectProperty
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

private val Project.testedVersions: List<String>
    get() = listOf(
        "1.0.0-beta03", "1.0.0-beta04", "1.0.0-beta05",
        "1.0.0-beta06", "1.0.0-beta07", "1.0.0-beta08",
        "1.0.0-beta09", "1.0.0-rc01", "1.0.0-rc02",
        project.providers.gradleProperty("bootstrap.version").get()
    )

open class OrchestrationCompatibilityTestsPlugin : Plugin<Project> {
    override fun apply(target: Project) = withProject(target) {
        setupCompatibilityTestDependencies()
        setupCompatibilityTest()
    }
}

private val Project.kotlin get() = project.kotlinExtension as KotlinJvmProjectExtension

private val Project.compatibilityTestCompilation by lazyProjectProperty {
    kotlin.target.compilations.create("compatibilityTest").apply {
        associateWith(kotlin.target.compilations.getByName("main"))
        compileTaskProvider.configure {
            compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
        }
    }
}

private val Project.testedClasspaths: Map<String, Configuration> by lazyProjectProperty {
    testedVersions.associateWith { version ->
        rootProject.configurations.create("classpathV$version").also { classpath ->
            classpath.attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            }


            rootProject.dependencies {
                classpath("org.jetbrains.compose.hot-reload:hot-reload-orchestration") {
                    version {
                        strictly(version)
                    }
                }
            }
        }
    }
}

private fun Project.setupCompatibilityTestDependencies() {
    dependencies {
        compatibilityTestCompilation.implementationConfigurationName(kotlin("stdlib"))
        compatibilityTestCompilation.implementationConfigurationName(kotlin("test"))
        compatibilityTestCompilation.implementationConfigurationName(kotlin("test-junit5"))
        compatibilityTestCompilation.implementationConfigurationName(kotlin("reflect"))
    }
}

private fun Project.setupCompatibilityTest() = tasks.register<Test>("compatibilityTest") {
    testedClasspaths.forEach { (version, classpath) ->
        inputs.files(classpath).withNormalizer(ClasspathNormalizer::class)
        systemProperty("classpathV$version", classpath.asPath)
    }

    systemProperty("testedVersions", testedVersions.joinToString(";"))
    systemProperty("testClasspath", compatibilityTestCompilation.output.allOutputs.asPath)

    project.intellijDebuggerDispatchPort.orNull?.let { dispatchPort ->
        systemProperty(HotReloadProperty.IntelliJDebuggerDispatchPort.key, dispatchPort)
    }

    classpath = files(
        compatibilityTestCompilation.output.allOutputs,
        compatibilityTestCompilation.runtimeDependencyFiles
    )

    testClassesDirs = files(
        compatibilityTestCompilation.output.classesDirs,
    )
}
