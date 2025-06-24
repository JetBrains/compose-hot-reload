/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin.VERIFICATION_GROUP
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.Os
import org.jetbrains.compose.reload.gradle.core.composeReloadJetBrainsRuntimeBinary
import org.jetbrains.compose.reload.gradle.files
import org.jetbrains.compose.reload.gradle.intellijDebuggerDispatchPort
import org.jetbrains.compose.reload.gradle.jetbrainsRuntimeLauncher
import org.jetbrains.compose.reload.gradle.kotlinJvmOrNull
import org.jetbrains.compose.reload.gradle.kotlinMultiplatformOrNull
import org.jetbrains.compose.reload.gradle.string
import org.jetbrains.compose.reload.gradle.withKotlinPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createParentDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.walk
import kotlin.io.path.writeText

private val Project.testGradleUserHome get() = project.layout.buildDirectory.dir("gradleHome").get().asFile

private val Project.testJavaHome
    get() = (project.composeReloadJetBrainsRuntimeBinary?.toFile() ?: project.jetbrainsRuntimeLauncher()
        .get().executablePath.asFile)
        .parentFile.parentFile


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

        task.environment("GRADLE_USER_HOME", project.testGradleUserHome.absolutePath)
        task.environment("JAVA_HOME", testJavaHome.absolutePath)

        intellijDebuggerDispatchPort.orNull?.let { port ->
            task.environment(HotReloadProperty.IntelliJDebuggerDispatchPort.key, port.toString())
            task.doFirst {
                task.systemProperty("junit.jupiter.execution.parallel.enabled", false)
                task.systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "1")
                task.systemProperty("junit.jupiter.execution.parallel.config.fixed.max-pool-size", "1")
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
        val testFinalizer = tasks.register<StopFunctionalTestGradleDaemonsTask>("stopFunctionalTestDaemons")
        test.configure { task -> task.finalizedBy(testFinalizer) }

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
        implementation("org.jetbrains.compose.hot-reload:hot-reload-gradle-testFixtures:${HOT_RELOAD_VERSION}")
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


internal open class StopFunctionalTestGradleDaemonsTask : DefaultTask() {

    @get:Internal
    internal val gradleUserHome = project.testGradleUserHome

    @get:Internal
    internal val javaHome = project.testJavaHome

    @OptIn(ExperimentalPathApi::class)
    @TaskAction
    fun stopDaemons() {
        val binaryName = if (Os.currentOrNull() == Os.Windows) "gradle.bat" else "gradle"
        val dists = gradleUserHome.toPath().resolve("wrapper/dists")
        dists.listDirectoryEntries().filter { it.isDirectory() }.forEach { dist ->
            logger.quiet("Stopping daemons for '${dist.name}'")
            val binary = dist.walk().first { it.endsWith("bin/$binaryName") }
            val builder = ProcessBuilder(binary.absolutePathString(), "--stop")
            builder.environment()["GRADLE_USER_HOME"] = gradleUserHome.absolutePath
            builder.environment()["JAVA_HOME"] = javaHome.absolutePath
            builder.redirectErrorStream(true)
            val process = builder.start()
            process.inputStream.bufferedReader().forEachLine { logger.quiet(it) }
        }
    }
}
