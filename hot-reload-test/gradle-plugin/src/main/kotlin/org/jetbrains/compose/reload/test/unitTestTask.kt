/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.compose.reload.test

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.tasks.testing.TestExecuter
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.reload.core.HOT_RELOAD_VERSION
import org.jetbrains.compose.reload.gradle.composeHotReloadAgentConfiguration
import org.jetbrains.compose.reload.gradle.composeHotReloadAgentJar
import org.jetbrains.compose.reload.gradle.files
import org.jetbrains.compose.reload.gradle.jetbrainsRuntimeLauncher
import org.jetbrains.compose.reload.gradle.kotlinJvmOrNull
import org.jetbrains.compose.reload.gradle.kotlinMultiplatformOrNull
import org.jetbrains.compose.reload.gradle.withComposePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile

open class HotReloadUnitTestTask : AbstractTestTask() {
    @get:Classpath
    internal val compileClasspath = project.objects.fileCollection()

    @get:Classpath
    internal val compilePluginClasspath = project.objects.fileCollection()

    @get:Input
    internal val moduleName = project.objects.property(String::class.java)

    @get:Classpath
    internal val agentClasspath: FileCollection = project.composeHotReloadAgentConfiguration

    @get:Classpath
    internal val agentJar: FileCollection = project.composeHotReloadAgentJar()

    @get:Classpath
    internal val classpath: ConfigurableFileCollection = project.objects.fileCollection()

    @get:Input
    @get:Optional
    internal val className = project.objects.property(String::class.java)
        .convention(project.providers.systemProperty("reloadTest.className"))

    @get:Input
    @get:Optional
    internal val methodName = project.objects.property(String::class.java)
        .convention(project.providers.systemProperty("reloadTest.methodName"))

    @get:Nested
    internal val launcher = project.jetbrainsRuntimeLauncher()

    @get:Internal
    internal val intellijDebuggerDispatchPort = project.intellijDebuggerDispatchPort

    fun compilation(compilation: KotlinCompilation<*>) {
        compileClasspath.from(project.files { compilation.compileDependencyFiles })
        classpath.from(project.files { compilation.runtimeDependencyFiles ?: emptyList<Any>() })
        classpath.from(project.files { compilation.output.allOutputs })
        moduleName.set(compilation.compileTaskProvider.flatMap { (it.compilerOptions as KotlinJvmCompilerOptions).moduleName })
        compilePluginClasspath.from(
            compilation.compileTaskProvider.map { compileTask ->
                (compileTask as? BaseKotlinCompile) ?: return@map emptyList()
                compileTask.pluginClasspath
            }
        )
    }

    override fun createTestExecuter(): TestExecuter<out HotReloadTestExecutionSpec?>? {
        return HotReloadUnitTestExecutor(
            javaExecutable = launcher.get().executablePath.asFile,
            classpath = classpath,
            agentJar = agentJar,
            agentClasspath = agentClasspath,
            compileModuleName = moduleName.get(),
            compileClasspath = compileClasspath,
            compilePluginClasspath = compilePluginClasspath,
            intellijDebuggerDispatchPort = intellijDebuggerDispatchPort.orNull
        )
    }

    override fun createTestExecutionSpec(): HotReloadTestExecutionSpec? {
        return HotReloadTestExecutionSpec(
            className = className.orNull,
            methodName = methodName.orNull,
        )
    }
}

internal fun Project.configureHotReloadUnitTestTasks() {
    tasks.withType(HotReloadUnitTestTask::class.java).configureEach { task ->
        task.group = VERIFICATION_GROUP
        task.description = "Runs Hot Reload test"
        task.binaryResultsDirectory.convention(layout.buildDirectory.dir(task.name))
        task.reports.junitXml.required.set(false)
        task.reports.html.outputLocation.convention(task.binaryResultsDirectory.dir("html"))
    }

    kotlinMultiplatformOrNull?.run {
        targets.withType<KotlinJvmTarget>().all { target ->
            target.configureDefaultHotReloadTestTask()
        }
    }

    kotlinJvmOrNull?.run {
        target.configureDefaultHotReloadTestTask()
    }
}

private fun KotlinTarget.configureDefaultHotReloadTestTask() {
    val main = compilations.getByName("main")
    val compilation = compilations.create("reloadUnitTest")
    compilation.associateWith(main)

    compilation.defaultSourceSet.dependencies {
        implementation("org.jetbrains.compose:hot-reload-test:${HOT_RELOAD_VERSION}")
        implementation("org.jetbrains.compose:hot-reload-runtime-jvm:${HOT_RELOAD_VERSION}:dev")
        project.withComposePlugin {
            implementation(project.extensions.getByType<ComposeExtension>().dependencies.desktop.currentOs)
        }
    }

    val hotReloadTest = project.tasks.register<HotReloadUnitTestTask>(lowerCamelCase(name, "reloadUnitTest"))
    hotReloadTest.configure { task -> task.compilation(compilation) }

    project.tasks.named { name -> name == "check" }.configureEach { check ->
        check.dependsOn(hotReloadTest)
    }
}
