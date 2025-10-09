/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.build

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.container
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.gradle.process.JavaForkOptions
import org.jetbrains.compose.ComposePlugin
import java.io.File.pathSeparator


/**
 * This plugin allwos for writing tests with the Kotlin and Compose compiler being availeble.
 * - The Kotlin compiler will be available as 'testCompilerClasspath' System Property
 * - The Compose compiler will be available as 'testComposeCompilerClasspath' System Property
 */

open class CompilerTestSupportPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = withProject(target) {
        val extension = extensions.create<TestWithCompilerExtension>("testWithCompiler", project)

        /*
        Dependencies visible to the compiler used inside the tests `Compiler.compile`
         */
        val testCompilerDependencies = configurations.create("testCompilerDependencies").apply {
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            }
        }

        /*
        Configuration resolving the compose plugin for the compiler used inside the tests `Compiler.compile`
        */
        val testComposeCompiler = configurations.create("testComposeCompiler").apply {
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            }
        }


        project.dependencies {
            "testImplementation"(kotlin("compiler-embeddable"))
            testCompilerDependencies(kotlin("stdlib"))
            testCompilerDependencies(ComposePlugin.Dependencies(project).desktop.currentOs)
            testCompilerDependencies(ComposePlugin.Dependencies(project).material3)
            testComposeCompiler(
                kotlin(
                    "compose-compiler-plugin-embeddable",
                    project.versionCatalogs.named("deps").findVersion("kotlin").get().requiredVersion
                )
            )
        }

        tasks.withType<Test>().configureEach {
            setupCompilerTestFixture()
        }


        extension.tasks.configureEach {
            setupCompilerTestFixture()
        }
    }
}

fun <T> T.setupCompilerTestFixture() where T : Task, T : JavaForkOptions {
    val testCompilerDependencies = project.configurations.getByName("testCompilerDependencies")
    val testComposeCompiler = project.configurations.getByName("testComposeCompiler")

    dependsOn(testCompilerDependencies)
    dependsOn(testComposeCompiler)
    val testCompilerClasspath = testCompilerDependencies.files
    val testComposeCompilerClasspath = testComposeCompiler.files

    doFirst {
        systemProperty(
            "testCompilerClasspath",
            testCompilerClasspath.joinToString(pathSeparator) { it.absolutePath })

        systemProperty(
            "testComposeCompilerClasspath",
            testComposeCompilerClasspath.joinToString(pathSeparator) { it.absolutePath })
    }
}

/*
Allows using the Kotlin + Compose compiler within tests by using the
`testCompilerClasspath` and `testComposeCompilerClasspath` System properties in the test
 */

abstract class TestWithCompilerExtension(val project: Project) {
    val tasks = project.container<JavaExec>()
}
