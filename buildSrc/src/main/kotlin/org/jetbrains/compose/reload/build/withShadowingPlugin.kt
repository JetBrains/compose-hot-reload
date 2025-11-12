/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.build

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.serviceOf
import java.util.zip.ZipFile
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString

class WithShadowingExtension(
    val shadowJar: TaskProvider<ShadowJar>,
) {
    fun relocate(from: String, to: String) = shadowJar.configure {
        relocate(from, to)
    }
}

/**
 * A specialized plugin for setting up the 'runtime-jvm' project.
 * This project is special, because it is allowed to shadow _certain_ dependencies
 */
class WithShadowingPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply(ShadowPlugin::class.java)

        /* Setup configurations */
        val implementation = target.configurations.getByName("implementation")
        val runtimeOnly = target.configurations.getByName("runtimeOnly")
        val compileOnly = target.configurations.getByName("compileOnly")
        val shadow = target.configurations.getByName("shadow")
        val shadowedImplementation = target.configurations.register("shadowedImplementation")
        val shadowRuntimeElements = target.configurations.getByName("shadowRuntimeElements")
        val sourcesElements = target.configurations.getByName("sourcesElements")
        val javadocElements = target.configurations.getByName("javadocElements")

        /**
         * Everything that is declared as regular 'implementation' or 'runtimeOnly' dependency
         * shall behave as 'regular' runtime dependency (not shadowed)
         */
        shadow.extendsFrom(implementation)
        shadow.extendsFrom(runtimeOnly)

        /**
         * Dependencies within the 'shadowImplementation' have to be listed *all* exhaustively
         * (no transitivity, to avoid unintended shadowing)
         */
        shadowedImplementation.configure {
            isCanBeConsumed = false
            isCanBeResolved = true
            isTransitive = false
        }

        /**
         * Everything, embedded into the 'shadowImplementation' shall be available during compilation
         */
        compileOnly.extendsFrom(shadowedImplementation.get())


        /**
         * Configure the jar:
         * Let the 'shadow' jar be the 'default' jar and call the unshaded jar 'light'
         */
        val shadowJar = target.tasks.named<ShadowJar>("shadowJar")
        val jar = target.tasks.named<Jar>("jar")

        shadowJar.configure {
            archiveClassifier.set("")
            configurations.set(listOf(shadowedImplementation.get()))
        }

        jar.configure {
            archiveClassifier.set("light")
        }

        /* Configure the publication by creating a new component */
        val component = target.serviceOf<SoftwareComponentFactory>().adhoc("shadowed")
        component.addVariantsFromConfiguration(shadowRuntimeElements) {
            mapToMavenScope("runtime")
        }

        component.addVariantsFromConfiguration(sourcesElements) {
            mapToOptional()
        }

        component.addVariantsFromConfiguration(javadocElements) {
            mapToOptional()
        }

        /**
         * Configure the publishing:
         * We do want to publish the 'shadow' component only
         */
        target.plugins.withId("maven-publish") {
            target.extensions.configure<PublishingExtension>() {
                publications.register<MavenPublication>("default") {
                   from(component)
                }
            }
        }

        val checkTask = target.tasks.register("checkShadowedJar", JarConentCheck::class.java) {
            inputs.files(shadowJar)
        }

        target.tasks.named("check").configure {
            dependsOn(checkTask)
        }

        /* create extension */
        val extension = target.extensions.add(
            WithShadowingExtension::class.java, "withShadowing",
            WithShadowingExtension(shadowJar)
        )
    }
}

/**
 * Since we're shading elements into jars here, this task will compare the content
 * (packages, resources) of the input jar with a dump file
 */
internal open class JarConentCheck : DefaultTask() {

    @get:OutputFile
    val output: Provider<RegularFile> = project.objects.fileProperty()
        .convention(project.layout.projectDirectory.dir("api").file("${project.name}.jar.txt"))

    @TaskAction
    fun check() {
        val entries = inputs.files.flatMap { file ->
            ZipFile(file).use { zipFile ->
                zipFile.entries().asSequence().toList().mapNotNull { entry ->
                    if (entry.isDirectory) {
                        return@mapNotNull entry.name
                    } else if (entry.name.endsWith(".class")) {
                        val parent = Path(entry.name).parent
                        if (parent != null) {
                            return@mapNotNull parent.invariantSeparatorsPathString
                        }
                    } else {
                        return@mapNotNull entry.name
                    }
                    null
                }
            }
        }.toSet()

        val actualText = buildString {
            appendLine("Contains packages and resources found in the jar file")
            appendLine("########################################################")
            appendLine()
            entries.sorted().forEach { entry ->
                appendLine(entry)
            }
        }.trim().lines().joinToString("\n")

        val expectFile = output.get().asFile
        if (!expectFile.exists()) {
            expectFile.writeText(actualText)
            throw AssertionError("File ${expectFile.toURI()} did not exist; Generated.")
        }

        val expectText = output.get().asFile.readText()
            .trim().lines().joinToString("\n")
        if (expectText != actualText) {
            val actualFile = expectFile.resolveSibling(
                "${expectFile.nameWithoutExtension}-actual.${expectFile.extension}"
            )

            actualFile.writeText(actualText)

            throw AssertionError(
                "Inconsistent jar dump:\n" +
                    "expected: ${expectFile.toURI()}\n" +
                    "actual: ${actualFile.toURI()}"
            )
        }
    }
}
