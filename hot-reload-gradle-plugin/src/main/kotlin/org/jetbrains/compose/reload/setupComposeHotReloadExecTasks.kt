/*
 * Copyright 2024-2025 JetBrains s.r.o. and Compose Hot Reload contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:Suppress("NullableBooleanElvis")

package org.jetbrains.compose.reload

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.reload.core.BuildSystem
import org.jetbrains.compose.reload.core.HotReloadProperty
import org.jetbrains.compose.reload.core.HotReloadProperty.DevToolsClasspath
import org.jetbrains.compose.reload.gradle.composeHotReloadAgentConfiguration
import org.jetbrains.compose.reload.gradle.composeHotReloadAgentJar
import org.jetbrains.compose.reload.gradle.createDebuggerJvmArguments
import org.jetbrains.compose.reload.gradle.jetbrainsRuntimeLauncher
import org.jetbrains.compose.reload.gradle.kotlinJvmOrNull
import org.jetbrains.compose.reload.gradle.kotlinMultiplatformOrNull
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmRun


internal fun Project.setupComposeHotReloadExecTasks() {
    kotlinJvmOrNull?.apply {
        target.createComposeHotReloadExecTask()
    }

    kotlinMultiplatformOrNull?.apply {
        targets.withType<KotlinJvmTarget>().all { target ->
            target.createComposeHotReloadExecTask()
        }
    }
}

private fun KotlinTarget.createComposeHotReloadExecTask() {
    @OptIn(InternalKotlinGradlePluginApi::class)
    project.tasks.register<KotlinJvmRun>("${name}Run") {
        configureJavaExecTaskForHotReload(project.provider { compilations.getByName("main") })
    }
}

internal fun JavaExec.configureJavaExecTaskForHotReload(compilation: Provider<KotlinCompilation<*>>) {
    if (project.composeHotReloadExtension.useJetBrainsRuntime.get()) {
        javaLauncher.set(project.jetbrainsRuntimeLauncher())
    }

    classpath = project.files(compilation.map { it.applicationClasspath })
    systemProperty(HotReloadProperty.HotClasspath.key, ToString(compilation.map { it.hotApplicationClasspath.asPath }))

    /* Setup debugging capabilities */
    run {
        if (project.isDebugMode.orNull == true) {
            logger.quiet("Enabled debugging")
            jvmArgs("-agentlib:jdwp=transport=dt_socket,server=n,address=localhost:5005,suspend=y")
        }
    }

    /* Support headless mode */
    run {
        if (project.isHeadless.orNull == true) {
            systemProperty(HotReloadProperty.IsHeadless.key, true)
            systemProperty("apple.awt.UIElement", true)
        }
    }

    /* Configure Pid File */
    systemProperty(
        HotReloadProperty.PidFile.key,
        ToString(compilation.map { compilation -> compilation.runBuildFile("$name.pid").get().asFile.absolutePath })
    )

    /* Configure dev tooling window */
    systemProperty(HotReloadProperty.DevToolsEnabled.key, project.showDevTooling.orNull ?: true)
    if (project.showDevTooling.orElse(true).get()) {
        inputs.files(project.composeHotReloadDevToolsConfiguration)
        systemProperty(DevToolsClasspath.key, project.composeHotReloadDevToolsConfiguration.asPath)
    }

    /* Setup re-compiler */
    val compileTaskName = compilation.map { composeReloadHotClasspathTaskName(it) }
    project.providers.systemProperty("java.home").orNull?.let { javaHome ->
        systemProperty(HotReloadProperty.GradleJavaHome.key, javaHome)
    }
    systemProperty(HotReloadProperty.BuildSystem.key, BuildSystem.Gradle.name)
    systemProperty(HotReloadProperty.GradleBuildRoot.key, project.rootDir.absolutePath)
    systemProperty(HotReloadProperty.GradleBuildProject.key, project.path)
    systemProperty(HotReloadProperty.GradleBuildTask.key, ToString(compileTaskName))
    systemProperty(HotReloadProperty.GradleBuildContinuous.key, project.isRecompileContinuous.orNull ?: true)


    /* Generic JVM args for hot reload*/
    run {
        /* Will get us additional information at runtime */
        if (logger.isInfoEnabled) {
            jvmArgs("-Xlog:redefine+class*=info")
        }

        inputs.files(project.composeHotReloadAgentConfiguration.files)
        dependsOn(project.composeHotReloadAgentConfiguration.buildDependencies)
        jvmArgs("-javaagent:" + project.composeHotReloadAgentJar().asPath)
    }

    /* JBR args */
    run {
        /* Non JBR JVMs will hate our previous JBR specific args */
        jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

        /* Enable DCEVM enhanced hotswap capabilities */
        jvmArgs("-XX:+AllowEnhancedClassRedefinition")
    }


    /* Setup orchestration */
    run {
        project.orchestrationPort.orNull?.let { port ->
            logger.quiet("Using orchestration server port: $port")
            systemProperty(HotReloadProperty.OrchestrationPort.key, port.toInt())
        }
    }


    mainClass.value(
        project.providers.gradleProperty("mainClass")
            .orElse(project.providers.systemProperty("mainClass"))
    )

    val intellijDebuggerDispatchPort = project.providers
        .environmentVariable(HotReloadProperty.IntelliJDebuggerDispatchPort.key)
        .orNull?.toIntOrNull()

    doFirst {
        if (!mainClass.isPresent) {
            throw IllegalArgumentException(ErrorMessages.missingMainClassProperty())
        }

        if (intellijDebuggerDispatchPort != null) {
            /*
            Provisioning a new debug session. This will return jvm args for the debug agent.
            Since we would like to debug our hot reload agent, we ensure that the debug agent is listed first.
             */
            jvmArgs = createDebuggerJvmArguments(intellijDebuggerDispatchPort).toList() + jvmArgs.orEmpty()
        }

        logger.info("Running ${mainClass.get()}...")
        logger.info("Classpath:\n${classpath.joinToString("\n")}")
    }
}

private class ToString(val property: Provider<String>) {
    override fun toString(): String {
        return property.get()
    }
}
