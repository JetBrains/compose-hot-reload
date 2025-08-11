/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:JvmName("ComposeHotReloadArgumentsKt")

package org.jetbrains.compose.reload.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions
import org.jetbrains.compose.reload.DelicateHotReloadApi
import org.jetbrains.compose.reload.core.BuildSystem
import org.jetbrains.compose.reload.core.HotReloadProperty
import java.io.File
import kotlin.io.path.absolutePathString

@DelicateHotReloadApi
sealed interface ComposeHotReloadArgumentsBuilder {
    fun setMainClass(mainClass: Provider<String>)
    fun setAgentJar(files: FileCollection)
    fun setHotClasspath(files: FileCollection)
    fun setIsHeadless(isHeadless: Provider<Boolean>)
    fun setPidFile(file: Provider<File>)
    fun setArgFile(file: Provider<File>)
    fun setDevToolsEnabled(enabled: Provider<Boolean>)
    fun setDevToolsClasspath(files: FileCollection)
    fun setDevToolsHeadless(headless: Provider<Boolean>)
    fun setDevToolsTransparencyEnabled(enabled: Provider<Boolean>)
    fun setDevToolsDetached(detached: Provider<Boolean>)
    fun setDevToolsAnimationsEnabled(enabled: Provider<Boolean>)

    fun setReloadTaskName(name: Provider<String>)
    fun setReloadTaskName(name: String)
    fun isAutoRecompileEnabled(isAutoRecompileEnabled: Provider<Boolean>)
    fun isRecompilerWarmupEnabled(isRecompilerWarmupEnabled: Provider<Boolean>)
}

@DelicateHotReloadApi
fun <T> T.withComposeHotReload(arguments: ComposeHotReloadArgumentsBuilder.() -> Unit) where T : JavaForkOptions, T : Task {
    val arguments = ComposeHotReloadArguments(project).also(arguments)
    jvmArgumentProviders.add(arguments)

    if (project.composeReloadJetBrainsRuntimeBinary != null) {
        this.executable(project.composeReloadJetBrainsRuntimeBinary)
    } else if (this is JavaExec) {
        javaLauncher.set(project.jetbrainsRuntimeLauncher())
    }
}

@DelicateHotReloadApi
fun Project.createComposeHotReloadArguments(builder: ComposeHotReloadArgumentsBuilder.() -> Unit): CommandLineArgumentProvider {
    return ComposeHotReloadArguments(this).also(builder)
}

internal class ComposeHotReloadArguments(project: Project) :
    ComposeHotReloadArgumentsBuilder,
    CommandLineArgumentProvider {
    private val rootProjectDir = project.rootProject.projectDir
    private val projectPath = project.path
    private val logger = project.logger

    @get:Input
    @get:Optional
    val mainClass: Property<String> = project.objects.property(String::class.java)
        .convention(project.mainClassConvention)

    @get:Classpath
    var agentJarFiles: FileCollection = project.composeHotReloadAgentJar

    @get:Optional
    @get:Classpath
    var hotClasspathFiles: FileCollection? = null

    @Classpath
    var devToolsClasspathFiles: FileCollection = project.composeHotReloadDevToolsConfiguration

    @get:Input
    @get:JvmName("getIsHeadless")
    val isHeadless: Property<Boolean> = project.objects.property(Boolean::class.java)
        .value(project.composeReloadIsHeadless)

    @get:Internal
    val pidFile: Property<File> = project.objects.property(File::class.java)

    @get:Internal
    val argFile: Property<File> = project.objects.property(File::class.java)

    @get:Input
    val devToolsEnabled: Property<Boolean> = project.objects.property(Boolean::class.java)
        .value(project.composeReloadDevToolsEnabled)

    @get:Input
    val devToolsTransparencyEnabled: Property<Boolean> = project.objects.property(Boolean::class.java)
        .value(project.composeReloadDevToolsTransparencyEnabled)

    @get:Input
    val devToolsDetached: Property<Boolean> = project.objects.property(Boolean::class.java)
        .value(project.composeReloadDevToolsDetached)

    @get:Input
    val devToolsAnimationsEnabled: Property<Boolean> = project.objects.property(Boolean::class.java)
        .value(project.composeReloadDevToolsAnimationsEnabled)

    @get:Input
    @get:Optional
    val devToolsIsHeadless: Property<Boolean> = project.objects.property(Boolean::class.java)
        .value(project.composeReloadDevToolsIsHeadless)

    @get:Input
    @get:Optional
    val reloadTaskName: Property<String> = project.objects.property(String::class.java)

    @get:Input
    @get:JvmName("getIsAutoRecompileEnabled")
    val isAutoRecompileEnabled: Property<Boolean> = project.objects.property(Boolean::class.java)
        .value(project.composeReloadGradleBuildContinuous)

    @get:Input
    @get:JvmName("getIsRecompilerWarmupEnabled")
    val isRecompilerWarmupEnabled: Property<Boolean> = project.objects.property(Boolean::class.java)
        .value(project.composeReloadGradleWarmupEnabled)


    @get:Input
    @get:Optional
    val orchestrationPort = project.composeReloadOrchestrationPort

    @get:Input
    @get:Optional
    val javaHome: Provider<String> = project.providers.systemProperty("java.home")

    @get:Input
    val virtualMethodResolveEnabled = project.composeReloadVirtualMethodResolveEnabled

    @get:Input
    val dirtyResolveDepthLimit = project.composeReloadDirtyResolveDepthLimit

    @get:Input
    val composeReloadResourcesDirtyResolverEnabled = project.composeReloadResourcesDirtyResolverEnabled

    @get:Input
    val logLevel = project.composeReloadLogLevel

    @get:Input
    val logStdout = project.composeReloadLogStdout

    @get:Internal
    val stdinFile = project.composeReloadStdinFile

    @get:Internal
    val stdoutFile = project.composeReloadStdoutFile

    @get:Internal
    val stderrFile = project.composeReloadStderrFile

    override fun setMainClass(mainClass: Provider<String>) {
        this.mainClass.set(mainClass)
    }

    override fun setAgentJar(files: FileCollection) {
        agentJarFiles = files
    }

    override fun setHotClasspath(files: FileCollection) {
        hotClasspathFiles = files
    }

    override fun setIsHeadless(isHeadless: Provider<Boolean>) {
        this.isHeadless.set(isHeadless.orElse(false))
    }

    override fun setPidFile(file: Provider<File>) {
        this.pidFile.set(file)
    }

    override fun setArgFile(file: Provider<File>) {
        this.argFile.set(file)
    }

    override fun setDevToolsEnabled(enabled: Provider<Boolean>) {
        this.devToolsEnabled.set(enabled.orElse(true))
    }

    override fun setDevToolsClasspath(files: FileCollection) {
        devToolsClasspathFiles = files
    }

    override fun setDevToolsTransparencyEnabled(enabled: Provider<Boolean>) {
        devToolsEnabled.set(enabled)
    }

    override fun setDevToolsDetached(detached: Provider<Boolean>) {
        devToolsDetached.set(detached)
    }

    override fun setDevToolsAnimationsEnabled(enabled: Provider<Boolean>) {
        devToolsAnimationsEnabled.set(enabled)
    }

    override fun setDevToolsHeadless(headless: Provider<Boolean>) {
        devToolsIsHeadless.set(headless.orElse(false))
    }

    override fun setReloadTaskName(name: Provider<String>) {
        reloadTaskName.set(name)
    }

    override fun setReloadTaskName(name: String) {
        reloadTaskName.set(name)
    }

    override fun isAutoRecompileEnabled(isAutoRecompileEnabled: Provider<Boolean>) {
        this.isAutoRecompileEnabled.set(isAutoRecompileEnabled.orElse(true))
    }

    override fun isRecompilerWarmupEnabled(isRecompilerWarmupEnabled: Provider<Boolean>) {
        this.isRecompilerWarmupEnabled.set(isRecompilerWarmupEnabled.orElse(false))
    }

    override fun asArguments(): Iterable<String> = buildList {
        /* Signal that this execution runs with Hot Reload */
        add("-D${HotReloadProperty.IsHotReloadActive.key}=true")

        /* Non JBR JVMs will hate our previous JBR specific args */
        add("-XX:+IgnoreUnrecognizedVMOptions")

        /* Enable DCEVM enhanced hotswap capabilities */
        add("-XX:+AllowEnhancedClassRedefinition")

        if (mainClass.isPresent) {
            add("-D${HotReloadProperty.MainClass.key}=${mainClass.get()}")
        }

        /* Provide agent jar */
        val agentJar = agentJarFiles.asPath
        if (agentJar.isNotEmpty()) {
            add("-javaagent:$agentJar")
        }

        /* Provide 'hot classpath' */
        hotClasspathFiles?.let { hotClasspath ->
            add("-D${HotReloadProperty.HotClasspath.key}=${hotClasspath.asPath}")
        }

        /* Provide 'isHeadless' property */
        val isHeadless = isHeadless.orNull
        if (isHeadless == true) {
            add("-Djava.awt.headless=true")
            add("-D${HotReloadProperty.IsHeadless.key}=true")
        }

        /* Provide pid file */
        val pidFile = pidFile.orNull
        if (pidFile != null) {
            add("-D${HotReloadProperty.PidFile.key}=${pidFile.absolutePath}")
        }

        /* Provide arg file */
        val argFile = argFile.orNull
        if (argFile != null) {
            add("-D${HotReloadProperty.ArgFile.key}=${argFile.absolutePath}")
        }

        /* Provide dev tools */
        val isDevToolsEnabled = devToolsEnabled.getOrElse(true)
        add("-D${HotReloadProperty.DevToolsEnabled.key}=$isDevToolsEnabled")
        add("-D${HotReloadProperty.DevToolsIsHeadless.key}=${devToolsIsHeadless.orNull ?: false}")

        if (isDevToolsEnabled) {
            add("-D${HotReloadProperty.DevToolsClasspath.key}=${devToolsClasspathFiles.asPath}")
            add("-D${HotReloadProperty.DevToolsTransparencyEnabled.key}=${devToolsTransparencyEnabled.orNull ?: true}")
            add("-D${HotReloadProperty.DevToolsDetached.key}=${devToolsDetached.orNull ?: false}")
            add("-D${HotReloadProperty.DevToolsAnimationsEnabled.key}=${devToolsAnimationsEnabled.orNull ?: true}")
        }

        /* Provide "recompiler" properties */
        add("-D${HotReloadProperty.BuildSystem.key}=${BuildSystem.Gradle.name}")
        add("-D${HotReloadProperty.GradleBuildRoot.key}=${rootProjectDir.absolutePath}")
        add("-D${HotReloadProperty.GradleBuildProject.key}=$projectPath")
        if (reloadTaskName.isPresent) {
            add("-D${HotReloadProperty.GradleBuildTask.key}=${reloadTaskName.get()}")
        }
        add("-D${HotReloadProperty.GradleBuildContinuous.key}=${isAutoRecompileEnabled.getOrElse(true)}")
        add("-D${HotReloadProperty.GradleWarmupEnabled.key}=${isRecompilerWarmupEnabled.getOrElse(false)}")
        javaHome.orNull?.let { javaHome ->
            add("-D${HotReloadProperty.GradleJavaHome.key}=$javaHome")
        }

        /* Forward the orchestration port if one is explicitly requested (client mode) */
        if (orchestrationPort != null) {
            logger.quiet("Using orchestration server port: $orchestrationPort")
            add("-D${HotReloadProperty.OrchestrationPort.key}=${orchestrationPort}")
        }

        add("-D${HotReloadProperty.VirtualMethodResolveEnabled.key}=$virtualMethodResolveEnabled")
        add("-D${HotReloadProperty.DirtyResolveDepthLimit.key}=$dirtyResolveDepthLimit")
        add("-D${HotReloadProperty.ResourcesDirtyResolverEnabled.key}=$composeReloadResourcesDirtyResolverEnabled")

        add("-D${HotReloadProperty.LogLevel.key}=${logLevel.name}")
        add("-D${HotReloadProperty.LogStdout.key}=${logStdout}")

        if (stdinFile != null) {
            add("-D${HotReloadProperty.StdinFile.key}=${stdinFile.absolutePathString()}")
        }

        if (stdoutFile != null) {
            add("-D${HotReloadProperty.StdoutFile.key}=${stdoutFile.absolutePathString()}")
        }

        if (stderrFile != null) {
            add("-D${HotReloadProperty.StderrFile.key}=${stderrFile.absolutePathString()}")
        }

    }.also { arguments ->
        if (logger.isInfoEnabled) {
            logger.info("Compose Hot Reload arguments:\n${arguments.joinToString("\n") { it.prependIndent("  ") }}")
        }
    }
}
