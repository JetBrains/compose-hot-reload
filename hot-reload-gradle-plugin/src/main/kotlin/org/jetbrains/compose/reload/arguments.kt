/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:JvmName("ComposeHotReloadArgumentsKt")

package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions
import org.jetbrains.compose.reload.core.BuildSystem
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.logging.Level
import org.jetbrains.compose.reload.gradle.composeHotReloadAgentJar
import org.jetbrains.compose.reload.gradle.core.composeReloadDevToolsEnabled
import org.jetbrains.compose.reload.gradle.core.composeReloadDevToolsTransparencyEnabled
import org.jetbrains.compose.reload.gradle.core.composeReloadDirtyResolveDepthLimit
import org.jetbrains.compose.reload.gradle.core.composeReloadEnableStdoutLoggingProvider
import org.jetbrains.compose.reload.gradle.core.composeReloadGradleBuildContinuous
import org.jetbrains.compose.reload.gradle.core.composeReloadIsHeadless
import org.jetbrains.compose.reload.gradle.core.composeReloadJetBrainsRuntimeBinary
import org.jetbrains.compose.reload.gradle.core.composeReloadLoggingLevel
import org.jetbrains.compose.reload.gradle.core.composeReloadOrchestrationPort
import org.jetbrains.compose.reload.gradle.core.composeReloadStderrFile
import org.jetbrains.compose.reload.gradle.core.composeReloadStdinFile
import org.jetbrains.compose.reload.gradle.core.composeReloadStdoutFile
import org.jetbrains.compose.reload.gradle.core.composeReloadVirtualMethodResolveEnabled
import org.jetbrains.compose.reload.gradle.jetbrainsRuntimeLauncher
import java.io.File

sealed interface ComposeHotReloadArgumentsBuilder {
    val project: Project
    fun setMainClass(mainClass: Provider<String>)
    fun setAgentJar(files: FileCollection)
    fun setHotClasspath(files: FileCollection)
    fun setIsHeadless(isHeadless: Provider<Boolean>)
    fun setPidFile(file: Provider<File>)
    fun setArgFile(file: Provider<File>)
    fun setDevToolsEnabled(enabled: Provider<Boolean>)
    fun setDevToolsClasspath(files: FileCollection)
    fun setDevToolsTransparencyEnabled(enabled: Provider<Boolean>)
    fun setReloadTaskName(name: Provider<String>)
    fun setReloadTaskName(name: String)
    fun isRecompileContinuous(isRecompileContinuous: Provider<Boolean>)
    fun setEnableStdoutLogging(enableStdoutLogging: Provider<Boolean>)
    fun setLoggingLevel(loggingLevel: Provider<Level>)
}

fun <T> T.withComposeHotReloadArguments(builder: ComposeHotReloadArgumentsBuilder.() -> Unit) where T : JavaForkOptions, T : Task {
    project.composeHotReloadArguments(builder).applyTo(this)
}

@JvmName("withComposeHotReloadArguments")
@Deprecated(
    message = "Use withComposeHotReloadArguments instead",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("withComposeHotReloadArguments(builder)")
)
@PublishedApi
internal fun JavaExec.withComposeHotReloadArgumentsJavaExec(builder: ComposeHotReloadArgumentsBuilder.() -> Unit) {
    project.composeHotReloadArguments(builder).applyTo(this)
}

sealed interface ComposeHotReloadArguments : CommandLineArgumentProvider {
    fun applyTo(java: JavaForkOptions)
}

fun Project.composeHotReloadArguments(builder: ComposeHotReloadArgumentsBuilder.() -> Unit): ComposeHotReloadArguments {
    return ComposeHotReloadArgumentsBuilderImpl(this)
        .also(builder)
        .build()
}

private class ComposeHotReloadArgumentsBuilderImpl(
    override val project: Project,
) : ComposeHotReloadArgumentsBuilder {
    private val mainClass: Property<String> = project.objects.property(String::class.java).convention(
        project.providers.gradleProperty("mainClass")
            .orElse(project.providers.systemProperty("mainClass"))
    )

    private var agentJar: FileCollection = project.composeHotReloadAgentJar()
    private var hotClasspath: FileCollection? = null

    private val isHeadless: Property<Boolean> = project.objects.property(Boolean::class.java)
        .value(project.composeReloadIsHeadless)

    private val pidFile: Property<File> = project.objects.property(File::class.java)

    private val argFile: Property<File> = project.objects.property(File::class.java)

    private var devToolsClasspath: FileCollection = project.composeHotReloadDevToolsConfiguration

    private val devToolsEnabled: Property<Boolean> = project.objects.property(Boolean::class.java)
        .value(project.composeReloadDevToolsEnabled)

    private val devToolsTransparencyEnabled: Property<Boolean> = project.objects.property(Boolean::class.java)
        .value(project.composeReloadDevToolsTransparencyEnabled)

    private val reloadTaskName: Property<String> = project.objects.property(String::class.java)

    private val isRecompileContinues: Property<Boolean> = project.objects.property(Boolean::class.java)
        .value(project.composeReloadGradleBuildContinuous)

    private val enableStdoutLogging: Property<Boolean> = project.objects.property(Boolean::class.java)
        .value(project.composeReloadEnableStdoutLoggingProvider)

    private val loggingLevel: Property<Level> = project.objects.property(Level::class.java)
        .value(project.composeReloadLoggingLevel)

    override fun setMainClass(mainClass: Provider<String>) {
        this.mainClass.set(mainClass)
    }

    override fun setAgentJar(files: FileCollection) {
        agentJar = files
    }

    override fun setHotClasspath(files: FileCollection) {
        hotClasspath = files
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
        devToolsClasspath = files
    }

    override fun setDevToolsTransparencyEnabled(enabled: Provider<Boolean>) {
        devToolsEnabled.set(enabled)
    }

    override fun setReloadTaskName(name: Provider<String>) {
        reloadTaskName.set(name)
    }

    override fun setReloadTaskName(name: String) {
        reloadTaskName.set(name)
    }

    override fun isRecompileContinuous(isRecompileContinuous: Provider<Boolean>) {
        this.isRecompileContinues.set(isRecompileContinuous.orElse(true))
    }

    override fun setEnableStdoutLogging(enableStdoutLogging: Provider<Boolean>) {
        this.enableStdoutLogging.set(enableStdoutLogging)
    }

    override fun setLoggingLevel(loggingLevel: Provider<Level>) {
        this.loggingLevel.set(loggingLevel)
    }

    fun build(): ComposeHotReloadArguments {
        return ComposeHotReloadArgumentsImpl(
            logger = project.logger,
            rootProjectDir = project.rootProject.projectDir,
            projectPath = project.path,
            javaHome = project.providers.systemProperty("java.home"),
            mainClass = mainClass,
            agentJar = agentJar,
            hotClasspath = hotClasspath,
            isHeadless = isHeadless,
            pidFile = pidFile,
            argFile = argFile,
            devToolsClasspath = devToolsClasspath,
            devToolsEnabled = devToolsEnabled,
            devToolsTransparencyEnabled = devToolsTransparencyEnabled,
            reloadTaskName = reloadTaskName,
            isRecompileContinues = isRecompileContinues,
            enableStdoutLogging = enableStdoutLogging,
            loggingLevel = loggingLevel,
            orchestrationPort = project.provider { project.composeReloadOrchestrationPort },
            /* Non API elements */
            virtualMethodResolveEnabled = project.composeReloadVirtualMethodResolveEnabled,
            dirtyResolveDepthLimit = project.composeReloadDirtyResolveDepthLimit,
            stdinFile = project.composeReloadStdinFile?.toFile(),
            stdoutFile = project.composeReloadStdoutFile?.toFile(),
            stderrFile = project.composeReloadStderrFile?.toFile(),
        )
    }
}

private class ComposeHotReloadArgumentsImpl(
    private val logger: org.gradle.api.logging.Logger,
    private val rootProjectDir: File,
    private val projectPath: String,
    private val javaHome: Provider<String>,
    private val mainClass: Property<String>,
    private val agentJar: FileCollection,
    private val hotClasspath: FileCollection?,
    private val isHeadless: Provider<Boolean>,
    private val pidFile: Provider<File>,
    private val argFile: Provider<File>,
    private val devToolsClasspath: FileCollection,
    private val devToolsEnabled: Provider<Boolean>,
    private val devToolsTransparencyEnabled: Provider<Boolean>,
    private val reloadTaskName: Provider<String>,
    private val isRecompileContinues: Provider<Boolean>,
    private val enableStdoutLogging: Provider<Boolean>,
    private val loggingLevel: Provider<Level>,
    private val orchestrationPort: Provider<Int>,
    /* Non API elements */
    private val virtualMethodResolveEnabled: Boolean,
    private val dirtyResolveDepthLimit: Int,
    private val stdinFile: File?,
    private val stdoutFile: File?,
    private val stderrFile: File?,
) : ComposeHotReloadArguments {

    override fun applyTo(java: JavaForkOptions) {
        java.jvmArgumentProviders.add(this)

        if (java is Task) {
            java.inputs.files(agentJar)

            if (devToolsEnabled.getOrElse(true)) {
                java.inputs.files(devToolsClasspath)
            }
        }
        if (java is Task && java.project.composeReloadJetBrainsRuntimeBinary != null) {
            java.executable(java.project.composeReloadJetBrainsRuntimeBinary)
        } else if (java is JavaExec && java.project.composeHotReloadExtension.useJetBrainsRuntime.get()) {
            java.javaLauncher.set(java.project.jetbrainsRuntimeLauncher())
        }

    }

    override fun asArguments(): Iterable<String> = buildList {

        /* Will get us additional information at runtime */
        if (logger.isInfoEnabled) {
            add("-Xlog:redefine+class*=info")
        }

        /* Non JBR JVMs will hate our previous JBR specific args */
        add("-XX:+IgnoreUnrecognizedVMOptions")

        /* Enable DCEVM enhanced hotswap capabilities */
        add("-XX:+AllowEnhancedClassRedefinition")

        if (mainClass.isPresent) {
            add("-D${HotReloadProperty.MainClass.key}=${mainClass.get()}")
        }

        /* Provide agent jar */
        val agentJar = agentJar.asPath
        if (agentJar.isNotEmpty()) {
            add("-javaagent:$agentJar")
        }

        /* Provide 'hot classpath' */
        if (hotClasspath != null) {
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

        if (isDevToolsEnabled) {
            add("-D${HotReloadProperty.DevToolsClasspath.key}=${devToolsClasspath.asPath}")
            add("-D${HotReloadProperty.DevToolsTransparencyEnabled.key}=${devToolsTransparencyEnabled.orNull ?: true}")
        }

        /* Provide "recompiler" properties */
        add("-D${HotReloadProperty.BuildSystem.key}=${BuildSystem.Gradle.name}")
        add("-D${HotReloadProperty.GradleBuildRoot.key}=${rootProjectDir.absolutePath}")
        add("-D${HotReloadProperty.GradleBuildProject.key}=$projectPath")
        if (reloadTaskName.isPresent) {
            add("-D${HotReloadProperty.GradleBuildTask.key}=${reloadTaskName.get()}")
        }
        add("-D${HotReloadProperty.GradleBuildContinuous.key}=${isRecompileContinues.getOrElse(true)}")
        javaHome.orNull?.let { javaHome ->
            add("-D${HotReloadProperty.GradleJavaHome.key}=$javaHome")
        }

        add("-D${HotReloadProperty.EnableStdoutLogging.key}=${enableStdoutLogging.getOrElse(false)}")
        add("-D${HotReloadProperty.LoggingLevel.key}=${loggingLevel.getOrElse(Level.Debug)}")

        /* Forward the orchestration port if one is explicitly requested (client mode) */
        if (orchestrationPort.isPresent) {
            logger.quiet("Using orchestration server port: ${orchestrationPort.get()}")
            add("-D${HotReloadProperty.OrchestrationPort.key}=${orchestrationPort.get()}")
        }

        add("-D${HotReloadProperty.VirtualMethodResolveEnabled.key}=$virtualMethodResolveEnabled")
        add("-D${HotReloadProperty.DirtyResolveDepthLimit.key}=$dirtyResolveDepthLimit")

        if (stdinFile != null) {
            add("-D${HotReloadProperty.StdinFile.key}=${stdinFile.absolutePath}")
        }

        if (stdoutFile != null) {
            add("-D${HotReloadProperty.StdoutFile.key}=${stdoutFile.absolutePath}")
        }

        if (stderrFile != null) {
            add("-D${HotReloadProperty.StderrFile.key}=${stderrFile.absolutePath}")
        }

    }.also { arguments ->
        if (logger.isInfoEnabled) {
            logger.info("Compose Hot Reload arguments:\n${arguments.joinToString("\n") { it.prependIndent("  ") }}")
        }
    }
}
