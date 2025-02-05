/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin.VERIFICATION_GROUP
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.gradle.files
import org.jetbrains.compose.reload.gradle.kotlinJvmOrNull
import org.jetbrains.compose.reload.gradle.kotlinMultiplatformOrNull
import org.jetbrains.compose.reload.gradle.withKotlinPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

internal fun Project.configureGradleTestTasks() {
    tasks.withType<HotReloadFunctionalTestTask>().configureEach { task ->
        task.useJUnitPlatform()
        task.group = VERIFICATION_GROUP
        task.description = "Runs Hot Reload screenshot test"

        task.systemProperty(
            "reloadTests.gradleWrapper",
            task.gradlewWrapperFile.map { it.asFile.absolutePath }.string()
        )

        task.systemProperty(
            "reloadTests.gradleWrapperBat",
            task.gradlewWrapperBatFile.map { it.asFile.absolutePath }.string()
        )

        task.systemProperty(
            "reloadTests.gradleWrapperJar",
            task.gradlewWrapperJarFile.map { it.asFile.absolutePath }.string()
        )

        task.systemProperty(
            "reloadTests.screenshotsDirectory",
            task.screenshotsDirectory.map { it.asFile.absolutePath }.string()
        )

        intellijDebuggerDispatchPort.orNull?.let { port ->
            task.environment(HotReloadProperty.IntelliJDebuggerDispatchPort.key, port.toString())
            task.doFirst {
                task.systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "1")
            }
        }

        task.classpath = project.files {
            listOf(task.compilation.get().output.allOutputs, task.compilation.get().runtimeDependencyFiles)
        }

        task.testClassesDirs = project.files {
            task.compilation.get().output.classesDirs
        }

        task.screenshotsDirectory.convention(
            task.compilation.map { compilation ->
                project.layout.projectDirectory.dir(
                    "src/${lowerCamelCase(compilation.target.name, compilation.name)}/resources/screenshots"
                )
            }
        )
    }

    fun KotlinTarget.createDefaultTasks() {
        val mainCompilation = compilations.getByName("main")

        val reloadFunctionalTestWarmupCompilation = compilations.create("reloadFunctionalTestWarmup")
        reloadFunctionalTestWarmupCompilation.associateWith(mainCompilation)

        val reloadFunctionalTestCompilation = compilations.create("reloadFunctionalTest")
        reloadFunctionalTestCompilation.associateWith(mainCompilation)

        val warmup = registerHotReloadFunctionalTestTask(reloadFunctionalTestWarmupCompilation)
        val test = registerHotReloadFunctionalTestTask(reloadFunctionalTestCompilation)

        configureWarmup(warmup, test)
    }

    withKotlinPlugin {
        kotlinJvmOrNull?.target?.createDefaultTasks()
        kotlinMultiplatformOrNull?.targets?.withType<KotlinJvmTarget>()?.all { target ->
            target.createDefaultTasks()
        }
    }
}

private fun registerHotReloadFunctionalTestTask(compilation: KotlinCompilation<*>): TaskProvider<HotReloadFunctionalTestTask> {
    compilation.defaultSourceSet.dependencies {
        implementation("org.jetbrains.compose:hot-reload-gradleTestFixtures:${HOT_RELOAD_VERSION}")
    }

    val task = compilation.project.tasks.register<HotReloadFunctionalTestTask>(
        lowerCamelCase(compilation.target.name, compilation.name)
    ) {
        this.compilation.set(compilation)
    }

    compilation.project.tasks.named { name -> name == "check" }.configureEach { check ->
        check.dependsOn(task)
    }

    return task
}

private fun configureWarmup(warmup: TaskProvider<*>, test: TaskProvider<*>) {
    warmup.configure { task ->
        val outputMarker = task.project.layout.buildDirectory.file("${task.name}/warmup.marker")
        task.outputs.file(outputMarker)
        task.outputs.upToDateWhen { outputMarker.get().asFile.exists() }
        task.onlyIf { !outputMarker.get().asFile.exists() }

        task.doLast {
            outputMarker.get().asFile
                .toPath().createParentDirectories()
                .writeText("Warmup done")
        }
    }

    test.configure { task ->
        task.dependsOn(warmup)
    }
}

@Suppress("UnstableApiUsage")
abstract class HotReloadFunctionalTestTask : Test() {
    @get:Internal
    val screenshotsDirectory: DirectoryProperty = project.objects.directoryProperty()

    @get:InputFile
    val gradlewWrapperFile: RegularFileProperty = project.objects.fileProperty()
        .convention(project.isolated.rootProject.projectDirectory.file("gradlew"))

    @get:InputFile
    val gradlewWrapperBatFile: RegularFileProperty = project.objects.fileProperty()
        .convention(project.isolated.rootProject.projectDirectory.file("gradlew.bat"))

    @get:InputFile
    val gradlewWrapperJarFile: RegularFileProperty = project.objects.fileProperty()
        .convention(project.isolated.rootProject.projectDirectory.file("gradle/wrapper/gradle-wrapper.jar"))

    @get:Internal
    @Transient
    val compilation: Property<KotlinCompilation<*>> = project.objects.property(KotlinCompilation::class.java)
}
